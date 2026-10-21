package gitbench.ui.commit;

import gitbench.git.ChangeKind;
import gitbench.git.CommitOptions;
import gitbench.git.CommitService;
import gitbench.git.FileChange;
import gitbench.git.GitException;
import gitbench.git.GitRepository;
import gitbench.git.GitRunner;
import gitbench.git.OutgoingCommit;
import gitbench.git.PushResult;
import gitbench.git.PushService;
import gitbench.git.PushTarget;
import gitbench.git.RepositoryLocator;
import gitbench.git.RollbackService;
import gitbench.git.StatusService;
import gitbench.model.InclusionModel;
import gitbench.model.MessageHistory;
import gitbench.ui.Activator;
import gitbench.ui.GitbenchIcons;
import gitbench.ui.GitbenchIcons.Icon;
import gitbench.ui.RepositoryContext;
import gitbench.ui.WorkspaceSync;
import gitbench.ui.push.PushDialog;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuCreator;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.ToolItem;
import org.eclipse.ui.IPartListener2;
import org.eclipse.ui.IWorkbenchPartReference;
import org.eclipse.ui.part.ViewPart;

import java.util.List;
import java.util.Optional;

/** The Commit view. It shows the local changes and the commit message panel, as the IntelliJ Commit window does. */
public final class CommitView extends ViewPart {
  public static final String ID = "gitbench.views.commit";
  private static final String PREF_MESSAGE_HISTORY = "messageHistory";
  private static final int MESSAGE_HISTORY_SIZE = 25;

  private final InclusionModel inclusion = new InclusionModel();
  private StatusService statusService;
  private CommitService commitService;
  private RollbackService rollbackService;
  private PushService pushService;
  private RepositoryContext context;
  private Display display;

  private Combo repositoryCombo;
  private List<GitRepository> comboRepositories = List.of();
  private ChangesTreeViewer tree;
  private CommitPanel panel;
  private Action rollbackAction;
  private Job refreshJob;
  private Job delayedRefresh;
  private IResourceChangeListener resourceListener;
  private IPartListener2 activationListener;
  private final java.util.function.Consumer<GitRepository> repositoryListener = repository -> asyncExec(() -> {
    if (context.current().equals(Optional.of(repository))) {
      refresh();
    }
  });

  @Override
  public void createPartControl(Composite parent) {
    display = parent.getDisplay();
    GitRunner runner = Activator.getDefault().gitRunner();
    statusService = new StatusService(runner);
    commitService = new CommitService(runner);
    rollbackService = new RollbackService(runner);
    pushService = new PushService(runner);

    Composite root = new Composite(parent, SWT.NONE);
    GridLayout layout = new GridLayout(1, false);
    layout.marginWidth = 0;
    layout.marginHeight = 0;
    layout.verticalSpacing = 0;
    root.setLayout(layout);

    repositoryCombo = new Combo(root, SWT.READ_ONLY);
    repositoryCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    repositoryCombo.addListener(SWT.Selection, e -> {
      int index = repositoryCombo.getSelectionIndex();
      if (index >= 0 && index < comboRepositories.size()) {
        context.select(comboRepositories.get(index));
      }
    });

    SashForm sash = new SashForm(root, SWT.VERTICAL);
    sash.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
    tree = new ChangesTreeViewer(sash, inclusion, this::updateButtons);
    tree.viewer().addSelectionChangedListener(e -> updateActions());
    tree.viewer().addDoubleClickListener(e -> OpenFiles.open(getSite().getPage(), currentRepository(), tree.selectedChanges()));
    panel = new CommitPanel(sash, new CommitPanel.Listener() {
      @Override
      public void commit(boolean andPush) {
        doCommit(andPush);
      }

      @Override
      public void amendChanged(boolean amend) {
        onAmendChanged(amend);
      }

      @Override
      public void showMessageHistory(Point screenLocation) {
        onShowMessageHistory(screenLocation);
      }
    }, () -> !inclusion.included().isEmpty());
    sash.setWeights(65, 35);

    createToolbar(getViewSite().getActionBars().getToolBarManager());
    new CommitContextMenu(this, commitService).install();

    context = new RepositoryContext(getSite().getPage(), new RepositoryLocator(runner));
    context.addListener(() -> {
      updateRepositoryCombo();
      refresh();
    });
    listenToChanges();
    gitbench.ui.RepositoryEvents.listen(repositoryListener);
    updateRepositoryCombo();
    refresh();
  }

  @Override
  public void setFocus() {
    tree.viewer().getControl().setFocus();
  }

  @Override
  public void dispose() {
    if (resourceListener != null) {
      ResourcesPlugin.getWorkspace().removeResourceChangeListener(resourceListener);
    }
    if (activationListener != null) {
      getSite().getPage().removePartListener(activationListener);
    }
    if (refreshJob != null) {
      refreshJob.cancel();
    }
    if (delayedRefresh != null) {
      delayedRefresh.cancel();
    }
    gitbench.ui.RepositoryEvents.unlisten(repositoryListener);
    if (context != null) {
      context.dispose();
    }
    super.dispose();
  }

  /** Reads the status again in the background and updates the view. */
  public void refresh() {
    if (refreshJob != null) {
      refreshJob.cancel();
    }
    Optional<GitRepository> repository = context.current();
    if (context.error() != null && repository.isEmpty()) {
      setContentDescription(context.error());
      tree.setChanges(List.of());
      panel.setAmendEnabled(false);
      return;
    }
    if (repository.isEmpty()) {
      setContentDescription("No Git repository. Select a project that is under Git.");
      tree.setChanges(List.of());
      panel.setAmendEnabled(false);
      return;
    }
    GitRepository repo = repository.get();
    refreshJob = Job.create("Refreshing Git changes", monitor -> {
      try {
        List<FileChange> changes = statusService.status(repo);
        boolean hasCommits = commitService.hasCommits(repo);
        asyncExec(() -> {
          if (!repository.equals(context.current())) {
            return;
          }
          setContentDescription(context.error() == null ? "" : context.error());
          tree.setChanges(changes);
          panel.setAmendEnabled(hasCommits);
          updateActions();
        });
      }
      catch (GitException e) {
        asyncExec(() -> setContentDescription(e.userMessage()));
      }
      return Status.OK_STATUS;
    });
    refreshJob.setSystem(true);
    refreshJob.schedule();
  }

  Optional<GitRepository> currentRepository() {
    return context.current();
  }

  ChangesTreeViewer tree() {
    return tree;
  }

  CommitPanel panel() {
    return panel;
  }

  InclusionModel inclusion() {
    return inclusion;
  }

  /** A git operation on the current repository. */
  @FunctionalInterface
  interface GitOperation {
    void run(GitRepository repository) throws GitException;
  }

  /**
   * Runs a git operation in the background, shows its error, then syncs the workspace with the disk and refreshes
   * the view. The workspace sync makes Project Explorer and the build see files that git changed.
   */
  void runOperation(String name, GitOperation operation) {
    Optional<GitRepository> repository = context.current();
    if (repository.isEmpty()) {
      return;
    }
    Job job = Job.create(name, monitor -> {
      try {
        operation.run(repository.get());
      }
      catch (GitException e) {
        asyncExec(() -> MessageDialog.openError(getSite().getShell(), name, e.userMessage()));
      }
      WorkspaceSync.refresh(repository.get());
      gitbench.ui.RepositoryEvents.fire(repository.get());
      asyncExec(this::refresh);
      return Status.OK_STATUS;
    });
    job.schedule();
  }

  void rollback(List<FileChange> changes) {
    List<FileChange> versioned = changes.stream().filter(c -> c.kind() != ChangeKind.UNVERSIONED).toList();
    if (versioned.isEmpty()) {
      return;
    }
    RollbackDialog dialog = new RollbackDialog(getSite().getShell(), versioned);
    if (dialog.open() != IDialogConstants.OK_ID) {
      return;
    }
    boolean deleteAdded = dialog.deleteAddedFiles();
    runOperation("Rollback", repo -> rollbackService.rollback(repo, versioned, deleteAdded));
  }

  /** Commits in the background. The buttons stay disabled until the commit ends, so a double click commits once. */
  private void doCommit(boolean andPush) {
    Optional<GitRepository> repository = context.current();
    if (repository.isEmpty() || panel.isBusy()) {
      return;
    }
    GitRepository repo = repository.get();
    String message = panel.message();
    List<FileChange> included = inclusion.included();
    CommitOptions options = panel.options();
    panel.setBusy(true);
    Job job = Job.create("Committing", monitor -> {
      try {
        commitService.commit(repo, message, included, options);
        asyncExec(() -> afterCommit(repo, message, included.size(), andPush));
      }
      catch (GitException e) {
        asyncExec(() -> MessageDialog.openError(getSite().getShell(), "Commit Failed", e.userMessage()));
      }
      finally {
        asyncExec(() -> {
          panel.setBusy(false);
          updateButtons();
          refresh();
        });
      }
      return Status.OK_STATUS;
    });
    job.schedule();
  }

  private void afterCommit(GitRepository repo, String message, int fileCount, boolean andPush) {
    onCommitted(message);
    gitbench.ui.RepositoryEvents.fire(repo);
    panel.afterCommit();
    String subject = message.strip().lines().findFirst().orElse("");
    getViewSite().getActionBars().getStatusLineManager()
      .setMessage(fileCount + (fileCount == 1 ? " file committed: " : " files committed: ") + subject);
    if (andPush) {
      openPushDialog(repo);
    }
  }

  /** Stores the message in the message history of the plugin preferences. */
  private void onCommitted(String message) {
    MessageHistory history = loadHistory();
    history.add(message.strip());
    Activator.getDefault().getPreferenceStore().setValue(PREF_MESSAGE_HISTORY, history.serialize());
  }

  /** Shows the recent messages. A click puts the message into the message field. */
  private void onShowMessageHistory(Point screenLocation) {
    Menu menu = new Menu(getSite().getShell(), SWT.POP_UP);
    List<String> recent = loadHistory().recent();
    if (recent.isEmpty()) {
      MenuItem empty = new MenuItem(menu, SWT.PUSH);
      empty.setText("No recent messages");
      empty.setEnabled(false);
    }
    for (String message : recent) {
      MenuItem item = new MenuItem(menu, SWT.PUSH);
      String subject = message.lines().findFirst().orElse("");
      item.setText(subject.length() > 80 ? subject.substring(0, 77) + "..." : subject);
      item.addListener(SWT.Selection, e -> {
        panel.setMessage(message);
        panel.focusMessage();
      });
    }
    menu.addListener(SWT.Hide, e -> display.asyncExec(menu::dispose));
    menu.setLocation(screenLocation);
    menu.setVisible(true);
  }

  private static MessageHistory loadHistory() {
    return MessageHistory.deserialize(Activator.getDefault().getPreferenceStore().getString(PREF_MESSAGE_HISTORY),
                                      MESSAGE_HISTORY_SIZE);
  }

  /** Loads the push target and the outgoing commits in the background, then opens the Push dialog. */
  private void openPushDialog(GitRepository repo) {
    Job load = Job.create("Preparing push", monitor -> {
      try {
        PushTarget target = pushService.defaultTarget(repo);
        List<OutgoingCommit> outgoing = pushService.outgoing(repo, target);
        asyncExec(() -> showPushDialog(repo, target, outgoing));
      }
      catch (GitException e) {
        asyncExec(() -> MessageDialog.openError(getSite().getShell(), "Push", e.userMessage()));
      }
      return Status.OK_STATUS;
    });
    load.schedule();
  }

  private void showPushDialog(GitRepository repo, PushTarget target, List<OutgoingCommit> outgoing) {
    PushDialog dialog = new PushDialog(getSite().getShell(), repo, pushService, target, outgoing);
    if (dialog.open() != IDialogConstants.OK_ID) {
      return;
    }
    PushTarget chosen = dialog.chosenTarget();
    Job job = Job.create("Pushing to " + chosen.remote(), monitor -> {
      try {
        PushResult result = pushService.push(repo, chosen);
        gitbench.ui.RepositoryEvents.fire(repo);
        String text = PushDialog.describe(result, outgoing.size(), chosen);
        asyncExec(() -> {
          switch (result.status()) {
            case OK, UP_TO_DATE -> getViewSite().getActionBars().getStatusLineManager().setMessage(text);
            case REJECTED -> MessageDialog.openWarning(getSite().getShell(), "Push Rejected", text);
            case ERROR -> MessageDialog.openError(getSite().getShell(), "Push Failed", text);
          }
        });
      }
      catch (GitException e) {
        asyncExec(() -> MessageDialog.openError(getSite().getShell(), "Push Failed", e.userMessage()));
      }
      return Status.OK_STATUS;
    });
    job.schedule();
  }

  /** Amend fills an empty message with the message of the last commit. The git call runs in the background. */
  private void onAmendChanged(boolean amend) {
    updateButtons();
    Optional<GitRepository> repository = context.current();
    if (!amend || !panel.message().isBlank() || repository.isEmpty()) {
      return;
    }
    Job job = Job.create("Reading last commit message", monitor -> {
      try {
        String last = commitService.lastCommitMessage(repository.get());
        asyncExec(() -> {
          if (panel.isAmend() && panel.message().isBlank()) {
            panel.setMessage(last);
          }
        });
      }
      catch (GitException e) {
        asyncExec(() -> MessageDialog.openError(getSite().getShell(), "Amend", e.userMessage()));
      }
      return Status.OK_STATUS;
    });
    job.setSystem(true);
    job.schedule();
  }

  private void updateButtons() {
    if (panel != null) {
      panel.updateButtons(() -> !inclusion.included().isEmpty());
    }
  }

  private void updateActions() {
    if (rollbackAction != null) {
      rollbackAction.setEnabled(tree.selectedChanges().stream().anyMatch(c -> c.kind() != ChangeKind.UNVERSIONED));
    }
  }

  private void updateRepositoryCombo() {
    comboRepositories = context.workspaceRepositories();
    boolean show = comboRepositories.size() > 1;
    ((GridData)repositoryCombo.getLayoutData()).exclude = !show;
    repositoryCombo.setVisible(show);
    repositoryCombo.setItems(comboRepositories.stream().map(GitRepository::name).toArray(String[]::new));
    context.current().ifPresent(current -> repositoryCombo.select(comboRepositories.indexOf(current)));
    repositoryCombo.getParent().layout(true);
  }

  private void createToolbar(IToolBarManager toolbar) {
    toolbar.add(action("Refresh", Icon.REFRESH, this::refresh));
    rollbackAction = action("Rollback...", Icon.ROLLBACK, () -> rollback(tree.selectedChanges()));
    rollbackAction.setEnabled(false);
    toolbar.add(rollbackAction);
    toolbar.add(new ViewOptionsAction());
    toolbar.add(action("Expand All", Icon.EXPAND_ALL, () -> tree.viewer().expandAll()));
    toolbar.add(action("Collapse All", Icon.COLLAPSE_ALL, () -> tree.viewer().collapseAll()));
  }

  static Action action(String text, Icon icon, Runnable run) {
    Action action = new Action(text) {
      @Override
      public void run() {
        run.run();
      }
    };
    action.setToolTipText(text);
    if (icon != null) {
      action.setImageDescriptor(GitbenchIcons.descriptor(icon));
    }
    return action;
  }

  /** Refreshes on workspace file changes outside the {@code .git} folder, and when the view becomes active. */
  private void listenToChanges() {
    resourceListener = event -> {
      if (event.getDelta() != null && touchesWorkingTree(event.getDelta())) {
        asyncExec(this::scheduleRefresh);
      }
    };
    ResourcesPlugin.getWorkspace().addResourceChangeListener(resourceListener, IResourceChangeEvent.POST_CHANGE);
    activationListener = new IPartListener2() {
      @Override
      public void partActivated(IWorkbenchPartReference ref) {
        if (ref.getPart(false) == CommitView.this) {
          refresh();
        }
      }
    };
    getSite().getPage().addPartListener(activationListener);
  }

  private void scheduleRefresh() {
    if (delayedRefresh == null) {
      delayedRefresh = Job.create("Refreshing Git changes", monitor -> {
        asyncExec(this::refresh);
        return Status.OK_STATUS;
      });
      delayedRefresh.setSystem(true);
    }
    delayedRefresh.cancel();
    delayedRefresh.schedule(300);
  }

  private static boolean touchesWorkingTree(IResourceDelta delta) {
    boolean[] found = {false};
    try {
      delta.accept(child -> {
        if (".git".equals(child.getResource().getName())) {
          return false;
        }
        if (child.getResource().getType() == IResource.FILE) {
          found[0] = true;
        }
        return !found[0];
      });
    }
    catch (CoreException e) {
      return true;
    }
    return found[0];
  }

  /** Runs on the UI thread, if the view still exists. Safe to call from jobs, also during shutdown. */
  void asyncExec(Runnable runnable) {
    if (display == null || display.isDisposed()) {
      return;
    }
    display.asyncExec(() -> {
      Control control = tree.viewer().getControl();
      if (!control.isDisposed()) {
        runnable.run();
      }
    });
  }

  /** The eye button: a drop-down with "Group By → Directory", as in IntelliJ. */
  private final class ViewOptionsAction extends Action implements IMenuCreator {
    private Menu menu;

    ViewOptionsAction() {
      super("View Options", IAction.AS_DROP_DOWN_MENU);
      setToolTipText("View Options");
      setImageDescriptor(GitbenchIcons.descriptor(Icon.VIEW_OPTIONS));
      setMenuCreator(this);
    }

    @Override
    public void runWithEvent(Event event) {
      if (event.widget instanceof ToolItem item) {
        Menu dropDown = getMenu(item.getParent());
        Rectangle bounds = item.getBounds();
        dropDown.setLocation(item.getParent().toDisplay(bounds.x, bounds.y + bounds.height));
        dropDown.setVisible(true);
      }
    }

    @Override
    public Menu getMenu(Control parent) {
      dispose();
      menu = new Menu(parent);
      MenuItem groupBy = new MenuItem(menu, SWT.CASCADE);
      groupBy.setText("Group By");
      Menu groupMenu = new Menu(menu);
      groupBy.setMenu(groupMenu);
      MenuItem directory = new MenuItem(groupMenu, SWT.CHECK);
      directory.setText("Directory");
      directory.setSelection(tree.groupByDirectory());
      directory.addListener(SWT.Selection, e -> tree.setGroupByDirectory(directory.getSelection()));
      return menu;
    }

    @Override
    public Menu getMenu(Menu parent) {
      return null;
    }

    @Override
    public void dispose() {
      if (menu != null && !menu.isDisposed()) {
        menu.dispose();
      }
      menu = null;
    }
  }
}
