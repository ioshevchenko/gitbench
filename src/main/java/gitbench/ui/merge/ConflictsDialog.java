package gitbench.ui.merge;

import gitbench.git.GitException;
import gitbench.git.GitRepository;
import gitbench.merge.ConflictFile;
import gitbench.merge.ConflictService;
import gitbench.merge.MergeContext;
import gitbench.ui.Activator;
import gitbench.ui.GitbenchIcons;
import gitbench.ui.GitbenchIcons.Icon;
import gitbench.ui.RepositoryEvents;
import gitbench.ui.WorkspaceSync;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.BusyIndicator;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeColumn;
import org.eclipse.swt.widgets.TreeItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The conflicts window, as the IntelliJ MultipleFileMergeDialog: the conflicted files with the state of each side,
 * and Accept Yours, Accept Theirs, Merge... and Close. It closes by itself when no conflict is left.
 */
final class ConflictsDialog extends Dialog {
  private final GitRepository repository;
  private final ConflictService service;
  private MergeContext context;
  private List<ConflictFile> files;
  private Label description;
  private Tree tree;
  private Button groupByDirectory;
  private Button acceptYours;
  private Button acceptTheirs;
  private Button merge;

  ConflictsDialog(Shell shell, GitRepository repository, MergeContext context, List<ConflictFile> files) {
    super(shell);
    this.repository = repository;
    this.context = context;
    this.files = files;
    service = new ConflictService(Activator.getDefault().gitRunner());
    setShellStyle(getShellStyle() | SWT.RESIZE);
  }

  @Override
  protected void configureShell(Shell shell) {
    super.configureShell(shell);
    shell.setText("Conflicts");
  }

  @Override
  protected Point getInitialSize() {
    return new Point(760, 440);
  }

  @Override
  protected boolean isResizable() {
    return true;
  }

  @Override
  protected Control createDialogArea(Composite parent) {
    Composite area = (Composite)super.createDialogArea(parent);
    area.setLayout(new GridLayout(2, false));
    description = new Label(area, SWT.WRAP);
    description.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));

    tree = new Tree(area, SWT.BORDER | SWT.MULTI | SWT.FULL_SELECTION);
    tree.setHeaderVisible(true);
    tree.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
    TreeColumn name = new TreeColumn(tree, SWT.LEFT);
    name.setText("Name");
    name.setWidth(380);
    TreeColumn yours = new TreeColumn(tree, SWT.LEFT);
    yours.setWidth(130);
    TreeColumn theirs = new TreeColumn(tree, SWT.LEFT);
    theirs.setWidth(130);
    tree.addListener(SWT.Selection, e -> updateButtons());
    tree.addListener(SWT.DefaultSelection, e -> {
      if (merge.isEnabled()) {
        mergeSelected();
      }
    });

    Composite buttons = new Composite(area, SWT.NONE);
    buttons.setLayout(new GridLayout(1, true));
    buttons.setLayoutData(new GridData(SWT.FILL, SWT.TOP, false, false));
    acceptYours = button(buttons, "Accept Yours", () -> accept(true));
    acceptTheirs = button(buttons, "Accept Theirs", () -> accept(false));
    merge = button(buttons, "Merge...", this::mergeSelected);

    groupByDirectory = new Button(area, SWT.CHECK);
    groupByDirectory.setText("Group by directory");
    groupByDirectory.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false, 2, 1));
    groupByDirectory.addListener(SWT.Selection, e -> fill());
    fill();
    return area;
  }

  @Override
  protected void createButtonsForButtonBar(Composite parent) {
    createButton(parent, CANCEL, "Close", false);
  }

  private static Button button(Composite parent, String text, Runnable action) {
    Button button = new Button(parent, SWT.PUSH);
    button.setText(text);
    button.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    button.addListener(SWT.Selection, e -> action.run());
    return button;
  }

  /** Shows the files as a list, or under their folders, with Yours and Theirs as "Modified" or "Deleted". */
  private void fill() {
    description.setText((context.description().isEmpty() ? "" : context.description() + "\n")
                        + (files.size() == 1 ? "The following file has conflicts:" : "The following files have conflicts:"));
    tree.getColumn(1).setText(context.yoursColumn());
    tree.getColumn(2).setText(context.theirsColumn());
    tree.removeAll();
    if (groupByDirectory.getSelection()) {
      Map<String, List<ConflictFile>> byDirectory = new TreeMap<>();
      for (ConflictFile file : files) {
        int slash = file.path().lastIndexOf('/');
        byDirectory.computeIfAbsent(slash < 0 ? "" : file.path().substring(0, slash), k -> new ArrayList<>()).add(file);
      }
      byDirectory.forEach((directory, inDirectory) -> {
        TreeItem folder = new TreeItem(tree, SWT.NONE);
        folder.setText(directory.isEmpty() ? "." : directory.replace('/', java.io.File.separatorChar));
        folder.setImage(GitbenchIcons.get(Icon.FOLDER));
        for (ConflictFile file : inDirectory) {
          item(new TreeItem(folder, SWT.NONE), file, true);
        }
        folder.setExpanded(true);
      });
    }
    else {
      for (ConflictFile file : files) {
        item(new TreeItem(tree, SWT.NONE), file, false);
      }
    }
    if (tree.getItemCount() > 0 && !groupByDirectory.getSelection()) {
      tree.setSelection(tree.getItem(0));
    }
    updateButtons();
  }

  private void item(TreeItem item, ConflictFile file, boolean nameOnly) {
    String path = file.path().replace('/', java.io.File.separatorChar);
    int slash = file.path().lastIndexOf('/');
    item.setText(0, nameOnly && slash >= 0 ? file.path().substring(slash + 1) : path);
    item.setText(1, yoursDeleted(file) ? "Deleted" : "Modified");
    item.setText(2, theirsDeleted(file) ? "Deleted" : "Modified");
    item.setData(file);
  }

  private boolean yoursDeleted(ConflictFile file) {
    return context.reversed() ? file.theirsDeleted() : file.oursDeleted();
  }

  private boolean theirsDeleted(ConflictFile file) {
    return context.reversed() ? file.oursDeleted() : file.theirsDeleted();
  }

  private List<ConflictFile> selected() {
    List<ConflictFile> result = new ArrayList<>();
    for (TreeItem item : tree.getSelection()) {
      if (item.getData() instanceof ConflictFile file) {
        result.add(file);
      }
      else {
        for (TreeItem child : item.getItems()) {
          result.add((ConflictFile)child.getData());
        }
      }
    }
    // A folder and one of its files can be selected together.
    return result.stream().distinct().toList();
  }

  /** Merge... works on one file that exists on both sides; binary files show an error in the merge window. */
  private void updateButtons() {
    List<ConflictFile> selected = selected();
    acceptYours.setEnabled(!selected.isEmpty());
    acceptTheirs.setEnabled(!selected.isEmpty());
    merge.setEnabled(selected.size() == 1 && selected.get(0).canMerge());
  }

  private void accept(boolean yours) {
    List<ConflictFile> selected = selected();
    GitException[] error = new GitException[1];
    BusyIndicator.showWhile(getShell().getDisplay(), () -> {
      try {
        if (yours) {
          service.acceptYours(repository, selected, context);
        }
        else {
          service.acceptTheirs(repository, selected, context);
        }
      }
      catch (GitException e) {
        error[0] = e;
      }
    });
    if (error[0] != null) {
      MessageDialog.openError(getShell(), yours ? "Accept Yours" : "Accept Theirs", error[0].userMessage());
    }
    afterChange();
  }

  private void mergeSelected() {
    List<ConflictFile> selected = selected();
    if (selected.size() == 1) {
      MergeDialog.open(getShell(), repository, selected.get(0), context);
      afterChange();
    }
  }

  /** Reads the conflicts again, tells the workspace and the views, and closes when nothing is left. */
  private void afterChange() {
    Job refresh = Job.create("Refresh after conflict resolution", monitor -> {
      WorkspaceSync.refresh(repository);
      RepositoryEvents.fire(repository);
      return Status.OK_STATUS;
    });
    refresh.setSystem(true);
    refresh.schedule();
    try {
      files = service.list(repository);
      context = service.context(repository);
    }
    catch (GitException e) {
      MessageDialog.openError(getShell(), "Conflicts", e.userMessage());
      return;
    }
    if (files.isEmpty()) {
      setReturnCode(OK);
      close();
      return;
    }
    fill();
  }
}
