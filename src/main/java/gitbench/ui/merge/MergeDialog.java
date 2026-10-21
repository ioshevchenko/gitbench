package gitbench.ui.merge;

import com.intellij.diff.util.ThreeSide;
import gitbench.diff.DiffText;
import gitbench.diff.IgnorePolicy;
import gitbench.diff.Side;
import gitbench.git.GitException;
import gitbench.git.GitRepository;
import gitbench.merge.ConflictFile;
import gitbench.merge.ConflictService;
import gitbench.merge.MergeChange;
import gitbench.merge.MergeContentLoader;
import gitbench.merge.MergeContents;
import gitbench.merge.MergeContext;
import gitbench.merge.MergeModel;
import gitbench.merge.MergeResultWriter;
import gitbench.ui.Activator;
import gitbench.ui.RepositoryEvents;
import gitbench.ui.Theme;
import gitbench.ui.WorkspaceSync;
import gitbench.ui.diff.SideDocuments;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.text.Document;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.BusyIndicator;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Shell;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;

/**
 * The merge window of one conflicted file, as the IntelliJ merge dialog: the toolbar, Yours | Result | Theirs, and
 * the buttons Accept Left, Accept Right, Apply and Cancel. Apply writes the Result to the file and marks it resolved.
 */
public final class MergeDialog extends Dialog {
  private final GitRepository repository;
  private final ConflictFile file;
  private final MergeContext context;
  private final MergeContents contents;
  private final Charset charset;
  private final String baseText;
  private final Document resultDocument;
  private final ConflictService service;
  private final Runnable afterSave;
  private MergeToolbar toolbar;
  private Composite banner;
  private MergeColumns columns;
  private MergeModel model;
  private boolean resolved;
  /** The states before each block action, for Ctrl+Z, and the Result text right after the last action. */
  private final java.util.Deque<MergeModel.Snapshot> undo = new java.util.ArrayDeque<>();
  private String textAfterLastAction;

  /**
   * {@code service} marks the file resolved; {@code afterSave} tells the workspace and the views. The UI tests pass
   * their own, because they run without the plugin.
   */
  MergeDialog(Shell shell, GitRepository repository, ConflictFile file, MergeContext context, MergeContents contents,
              Charset charset, ConflictService service, Runnable afterSave) {
    super(shell);
    this.service = service;
    this.afterSave = afterSave;
    this.repository = repository;
    this.file = file;
    this.context = context;
    this.contents = contents;
    this.charset = charset;
    baseText = DiffText.of(contents.base().text()).text();
    resultDocument = new Document(baseText);
    // Enter in an empty or one-line Result would insert the system CRLF; the Result keeps LF like the model.
    resultDocument.setInitialLineDelimiter("\n");
    setShellStyle(getShellStyle() | SWT.RESIZE | SWT.MAX);
  }

  /** Loads the three sides and opens the window. Returns true when the file is resolved. */
  public static boolean open(Shell shell, GitRepository repository, ConflictFile file, MergeContext context) {
    Charset charset = SideDocuments.charsetOf(repository.root().resolve(file.path()));
    MergeContents[] loaded = new MergeContents[1];
    GitException[] error = new GitException[1];
    BusyIndicator.showWhile(shell.getDisplay(), () -> {
      try {
        loaded[0] = MergeContentLoader.load(Activator.getDefault().gitRunner(), repository, file, context, charset);
      }
      catch (GitException e) {
        error[0] = e;
      }
    });
    if (error[0] != null) {
      MessageDialog.openError(shell, "Cannot Show Merge Dialog", "Error loading revisions to merge: " + error[0].userMessage());
      return false;
    }
    if (loaded[0].binary()) {
      MessageDialog.openError(shell, "Cannot Show Merge Dialog",
                              file.path() + " is a binary file. Resolve it with Accept Yours or Accept Theirs.");
      return false;
    }
    Runnable afterSave = () -> {
      Job refresh = Job.create("Refresh " + file.path(), monitor -> {
        WorkspaceSync.refresh(repository);
        RepositoryEvents.fire(repository);
        return Status.OK_STATUS;
      });
      refresh.setSystem(true);
      refresh.schedule();
    };
    MergeDialog dialog = new MergeDialog(shell, repository, file, context, loaded[0], charset,
                                         new ConflictService(Activator.getDefault().gitRunner()), afterSave);
    dialog.open();
    return dialog.isResolved();
  }

  /** The columns, for the UI tests. */
  MergeColumns columns() {
    return columns;
  }

  /** The model, for the UI tests. */
  MergeModel model() {
    return model;
  }

  /** True after Apply, Accept Left or Accept Right saved the file. */
  boolean isResolved() {
    return resolved;
  }

  @Override
  protected void configureShell(Shell shell) {
    super.configureShell(shell);
    shell.setText("Merge Revisions for " + file.path());
  }

  @Override
  protected Point getInitialSize() {
    Rectangle screen = getShell().getDisplay().getPrimaryMonitor().getClientArea();
    return new Point(screen.width * 4 / 5, screen.height * 4 / 5);
  }

  @Override
  protected Control createDialogArea(Composite parent) {
    Composite area = (Composite)super.createDialogArea(parent);
    GridLayout layout = (GridLayout)area.getLayout();
    layout.marginWidth = 0;
    layout.marginHeight = 0;
    layout.verticalSpacing = 0;
    toolbar = new MergeToolbar(area, this::previous, this::next, side -> act(() -> model.applyNonConflicting(side)),
                              () -> act(model::resolveSimpleConflicts));
    toolbar.control().setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    createBanner(area);
    columns = new MergeColumns(area, Theme.isDark(), repository.root().resolve(file.path()),
                               new Document(DiffText.of(contents.yours().text()).text()), resultDocument,
                               new Document(DiffText.of(contents.theirs().text()).text()), contents.yours().title(),
                               contents.theirs().title(), new ColumnActions());
    columns.control().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

    DocumentResultText result = new DocumentResultText(resultDocument, this::changed);
    model = MergeModel.create(DiffText.of(contents.yours().text()), DiffText.of(baseText), DiffText.of(contents.theirs().text()),
                              IgnorePolicy.DEFAULT, result);
    // As IntelliJ with "Automatically apply non-conflicting changes": only the conflicts are left to the user.
    model.applyNonConflicting(ThreeSide.BASE);
    columns.setModel(model);
    columns.resetUndo();
    changed();
    MergeChange first = model.next(-1);
    if (first != null) {
      area.getDisplay().asyncExec(() -> {
        if (!area.isDisposed()) {
          columns.reveal(first);
        }
      });
    }
    return area;
  }

  /** "All changes have been processed" with the Apply Changes link, shown when no block is left. */
  private void createBanner(Composite parent) {
    banner = new Composite(parent, SWT.NONE);
    GridLayout layout = new GridLayout(2, false);
    layout.marginHeight = 2;
    banner.setLayout(layout);
    GridData data = new GridData(SWT.FILL, SWT.CENTER, true, false);
    data.exclude = true;
    banner.setLayoutData(data);
    banner.setVisible(false);
    new Label(banner, SWT.NONE).setText("All changes have been processed.");
    Link link = new Link(banner, SWT.NONE);
    link.setText("<a>Apply Changes</a>");
    link.addListener(SWT.Selection, e -> apply());
  }

  /** Repaints the columns and updates the status after a change of the blocks. */
  private void changed() {
    if (columns == null || model == null) {
      return;
    }
    columns.refresh();
    toolbar.setStatus(MergeModel.statusText(model.changesCount(), model.conflictsCount()));
    boolean done = model.isAllResolved();
    if (banner.getVisible() != done) {
      banner.setVisible(done);
      ((GridData)banner.getLayoutData()).exclude = !done;
      banner.getParent().layout(true);
    }
  }

  /** Runs a block action so that Ctrl+Z can take it back. */
  private void act(Runnable action) {
    undo.push(model.snapshot());
    action.run();
    textAfterLastAction = resultDocument.get();
    columns.resetUndo();
    changed();
  }

  /**
   * Ctrl+Z: takes back the last block action, when nothing was typed after it. Typed text goes back through the
   * undo of the Result column instead.
   */
  private boolean undoLast() {
    if (!undo.isEmpty() && resultDocument.get().equals(textAfterLastAction)) {
      model.restore(undo.pop());
      textAfterLastAction = resultDocument.get();
      columns.resetUndo();
      changed();
      return true;
    }
    return columns.undoTyping();
  }

  private void next() {
    MergeChange change = model.next(columns.resultCaretLine());
    if (change != null) {
      columns.reveal(change);
    }
  }

  private void previous() {
    MergeChange change = model.previous(columns.resultCaretLine());
    if (change != null) {
      columns.reveal(change);
    }
  }

  @Override
  protected Control createButtonBar(Composite parent) {
    Composite bar = new Composite(parent, SWT.NONE);
    GridLayout layout = new GridLayout(5, false);
    layout.marginWidth = 8;
    bar.setLayout(layout);
    bar.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    button(bar, "Accept Left", () -> acceptAll(Side.LEFT));
    button(bar, "Accept Right", () -> acceptAll(Side.RIGHT));
    new Label(bar, SWT.NONE).setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    Button applyButton = button(bar, "Apply", this::apply);
    button(bar, "Cancel", this::cancelPressed);
    getShell().setDefaultButton(applyButton);
    return bar;
  }

  private static Button button(Composite bar, String text, Runnable action) {
    Button button = new Button(bar, SWT.PUSH);
    button.setText(text);
    GridData data = new GridData(SWT.FILL, SWT.CENTER, false, false);
    data.widthHint = Math.max(90, button.computeSize(SWT.DEFAULT, SWT.DEFAULT).x);
    button.setLayoutData(data);
    button.addListener(SWT.Selection, e -> action.run());
    return button;
  }

  private void acceptAll(Side side) {
    model.acceptAll(side);
    save();
  }

  /** Apply: asks when blocks are left, as IntelliJ, then saves the Result and marks the file resolved. */
  private void apply() {
    int changes = model.changesCount();
    int conflicts = model.conflictsCount();
    if (changes + conflicts > 0) {
      String message = "There " + (changes + conflicts == 1 ? "is " : "are ") + unprocessed(changes, conflicts)
                       + " left unprocessed.\nSave changes and mark the conflict resolved anyway?";
      if (!MessageDialog.openQuestion(getShell(), "Apply Changes", message)) {
        return;
      }
    }
    save();
  }

  private static String unprocessed(int changes, int conflicts) {
    String changesText = changes == 0 ? "" : changes == 1 ? "one change" : changes + " changes";
    String conflictsText = conflicts == 0 ? "" : conflicts == 1 ? "one conflict" : conflicts + " conflicts";
    return changesText.isEmpty() ? conflictsText : conflictsText.isEmpty() ? changesText : changesText + " and " + conflictsText;
  }

  private void save() {
    Path target = repository.root().resolve(file.path());
    try {
      MergeResultWriter.write(target, resultDocument.get(), contents.lineSeparator(), contents.charset(), contents.bom());
      service.markResolved(repository, file.path());
    }
    catch (IOException e) {
      MessageDialog.openError(getShell(), "Apply Changes", "Error saving merged data: " + e.getMessage());
      return;
    }
    catch (GitException e) {
      MessageDialog.openError(getShell(), "Apply Changes", e.userMessage());
      return;
    }
    resolved = true;
    afterSave.run();
    setReturnCode(OK);
    close();
  }

  /** Cancel asks before it drops a changed Result, as IntelliJ. */
  @Override
  protected void cancelPressed() {
    if (resolved || resultDocument.get().equals(baseText)
        || MessageDialog.openQuestion(getShell(), "Cancel", "Are you sure you want to exit without applying changes?")) {
      super.cancelPressed();
    }
  }

  @Override
  protected void handleShellCloseEvent() {
    cancelPressed();
  }

  @Override
  protected boolean isResizable() {
    return true;
  }

  /** The block buttons and the keys of the columns. */
  private final class ColumnActions implements MergeColumns.Actions {
    @Override
    public void replace(MergeChange change, Side side) {
      act(() -> model.replaceChange(change, side));
    }

    @Override
    public void ignore(MergeChange change, Side side) {
      act(() -> model.ignoreChange(change, side));
    }

    @Override
    public void magic(MergeChange change) {
      act(() -> model.resolveAutomatically(change));
    }

    @Override
    public boolean key(int keyCode, int stateMask) {
      int modifiers = stateMask & SWT.MODIFIER_MASK;
      if (keyCode == SWT.F7 && modifiers == 0) {
        next();
        return true;
      }
      if (keyCode == SWT.F7 && modifiers == SWT.SHIFT) {
        previous();
        return true;
      }
      if ((keyCode == 'z' || keyCode == 'Z') && modifiers == SWT.MOD1) {
        undoLast();
        return true;
      }
      return false;
    }
  }
}
