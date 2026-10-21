package gitbench.ui.push;

import gitbench.git.FileChange;
import gitbench.git.GitException;
import gitbench.git.GitRepository;
import gitbench.git.OutgoingCommit;
import gitbench.git.PushResult;
import gitbench.git.PushService;
import gitbench.git.PushTarget;
import gitbench.ui.GitbenchIcons;
import gitbench.ui.GitbenchIcons.Icon;
import gitbench.ui.StatusColors;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;

/**
 * The Push dialog: the target branch and the outgoing commits on the left, the files of the selected commit on the
 * right, as the IntelliJ Push dialog shows them. The dialog only collects the choice; the caller runs the push.
 */
public final class PushDialog extends Dialog {
  private static final DateTimeFormatter DATE = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
    .withZone(ZoneId.systemDefault());

  private final GitRepository repository;
  private final PushService pushService;
  private final PushTarget defaultTarget;
  private final List<OutgoingCommit> commits;
  private Text targetBranch;
  private Table files;
  private PushTarget chosenTarget;
  private OutgoingCommit shownCommit;

  public PushDialog(Shell parent, GitRepository repository, PushService pushService, PushTarget defaultTarget,
                    List<OutgoingCommit> commits) {
    super(parent);
    this.repository = repository;
    this.pushService = pushService;
    this.defaultTarget = defaultTarget;
    this.commits = commits;
  }

  /** The target that the user confirmed. Valid after the dialog returns OK. */
  public PushTarget chosenTarget() {
    return chosenTarget;
  }

  @Override
  protected void configureShell(Shell shell) {
    super.configureShell(shell);
    shell.setText("Push Commits to " + repository.name());
    shell.setImage(GitbenchIcons.get(Icon.PUSH));
  }

  @Override
  protected boolean isResizable() {
    return true;
  }

  @Override
  protected Control createDialogArea(Composite parent) {
    Composite area = (Composite)super.createDialogArea(parent);
    SashForm sash = new SashForm(area, SWT.HORIZONTAL);
    GridData sashData = new GridData(SWT.FILL, SWT.FILL, true, true);
    sashData.widthHint = 760;
    sashData.heightHint = 360;
    sash.setLayoutData(sashData);

    Composite left = new Composite(sash, SWT.NONE);
    left.setLayout(new GridLayout(4, false));
    Label branch = new Label(left, SWT.NONE);
    branch.setText(repository.name() + ":  " + defaultTarget.branch() + "  →  " + defaultTarget.remote() + " :");
    targetBranch = new Text(left, SWT.BORDER);
    targetBranch.setText(defaultTarget.branch());
    targetBranch.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    Label newLabel = new Label(left, SWT.NONE);
    newLabel.setText(defaultTarget.hasUpstream() ? "" : "New");
    newLabel.setForeground(StatusColors.foreground(gitbench.git.ChangeKind.ADDED));
    targetBranch.addListener(SWT.Modify, e -> {
      boolean isNew = !defaultTarget.hasUpstream() || !targetBranch.getText().trim().equals(defaultTarget.branch());
      newLabel.setText(isNew ? "New" : "");
      newLabel.getParent().layout();
      getButton(IDialogConstants.OK_ID).setEnabled(!targetBranch.getText().isBlank());
    });
    new Label(left, SWT.NONE);

    Table commitTable = new Table(left, SWT.BORDER | SWT.FULL_SELECTION | SWT.SINGLE);
    commitTable.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 4, 1));
    commitTable.setHeaderVisible(false);
    TableColumn subject = new TableColumn(commitTable, SWT.LEFT);
    TableColumn author = new TableColumn(commitTable, SWT.LEFT);
    TableColumn date = new TableColumn(commitTable, SWT.LEFT);
    for (OutgoingCommit commit : commits) {
      TableItem item = new TableItem(commitTable, SWT.NONE);
      item.setText(new String[]{commit.subject(), commit.author(), DATE.format(commit.date())});
      item.setData(commit);
    }
    if (commits.isEmpty()) {
      new TableItem(commitTable, SWT.NONE).setText("No commits to push");
    }
    subject.setWidth(260);
    author.pack();
    date.pack();
    commitTable.addListener(SWT.Selection, e -> {
      if (e.item != null && e.item.getData() instanceof OutgoingCommit commit) {
        showFiles(commit);
      }
    });

    files = new Table(sash, SWT.BORDER | SWT.FULL_SELECTION);
    sash.setWeights(60, 40);
    if (!commits.isEmpty()) {
      commitTable.select(0);
      showFiles(commits.get(0));
    }
    return area;
  }

  /** Loads the files of the commit in the background and shows them unless another commit got selected meanwhile. */
  private void showFiles(OutgoingCommit commit) {
    shownCommit = commit;
    files.removeAll();
    org.eclipse.swt.widgets.Display display = files.getDisplay();
    org.eclipse.core.runtime.jobs.Job.create("Reading commit files", monitor -> {
      List<FileChange> changes;
      String error = null;
      try {
        changes = pushService.changedFiles(repository, commit.shortHash());
      }
      catch (GitException e) {
        changes = List.of();
        error = e.userMessage();
      }
      List<FileChange> result = changes;
      String failure = error;
      display.asyncExec(() -> {
        if (files.isDisposed() || shownCommit != commit) {
          return;
        }
        for (FileChange change : result) {
          TableItem item = new TableItem(files, SWT.NONE);
          item.setText(change.path());
          item.setForeground(StatusColors.foreground(change.kind()));
        }
        if (failure != null) {
          new TableItem(files, SWT.NONE).setText(failure);
        }
      });
      return org.eclipse.core.runtime.Status.OK_STATUS;
    }).schedule();
  }

  @Override
  protected void createButtonsForButtonBar(Composite parent) {
    createButton(parent, IDialogConstants.OK_ID, "Push", true);
    createButton(parent, IDialogConstants.CANCEL_ID, IDialogConstants.CANCEL_LABEL, false);
  }

  @Override
  protected void okPressed() {
    String branch = targetBranch.getText().trim();
    boolean sameAsUpstream = defaultTarget.hasUpstream() && branch.equals(defaultTarget.branch());
    chosenTarget = new PushTarget(defaultTarget.remote(), branch, sameAsUpstream);
    super.okPressed();
  }

  /** Builds the user-facing text of a push result. */
  public static String describe(PushResult result, int commitCount, PushTarget target) {
    return switch (result.status()) {
      case OK -> "Pushed " + commitCount + (commitCount == 1 ? " commit" : " commits") + " to "
                 + target.remote() + "/" + target.branch();
      case UP_TO_DATE -> "Everything is up to date";
      case REJECTED -> "Remote has changes you don't have locally. Update your branch and push again.\n\n" + result.message();
      case ERROR -> result.message();
    };
  }
}
