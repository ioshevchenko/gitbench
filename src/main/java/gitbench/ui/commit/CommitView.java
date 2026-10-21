package gitbench.ui.commit;

import gitbench.git.ChangeKind;
import gitbench.git.CommitOptions;
import gitbench.git.CommitService;
import gitbench.git.FileChange;
import gitbench.git.GitException;
import gitbench.git.GitRepository;
import gitbench.git.GitRunner;
import gitbench.git.RepositoryLocator;
import gitbench.git.RollbackService;
import gitbench.git.StatusService;
import gitbench.model.InclusionModel;
import gitbench.ui.Activator;
import gitbench.ui.GitbenchIcons;
import gitbench.ui.GitbenchIcons.Icon;
import gitbench.ui.RepositoryContext;
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

  private final InclusionModel inclusion = new InclusionModel();
  private StatusService statusService;
  private CommitService commitService;
  private RollbackService rollbackService;
  private RepositoryContext context;

  private Combo repositoryCombo;
  private List<GitRepository> comboRepositories = List.of();
  private ChangesTreeViewer tree;
  private CommitPanel panel;
  private Action rollbackAction;
  private Job refreshJob;
  private Job delayedRefresh;
  private IResourceChangeListener resourceListener;
  private IPartListener2 activationListener;

  @Override
  public void createPartControl(Composite parent) {
    GitRunner runner = Activator.getDefault().gitRunner();
    statusService = new StatusService(runner);
    commitService = new CommitService(runner);
    rollbackService = new RollbackService(runner);

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

    context = new RepositoryContext(getSite().getPage(), new RepositoryLocator(runner));
    context.addListener(repository -> {
      updateRepositoryCombo();
      refresh();
    });
    listenToChanges();
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
          setContentDescription("");
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

  /** Runs a git operation in the background, shows its error, and refreshes the view after it. */
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

  private void doCommit(boolean andPush) {
    Optional<GitRepository> repository = context.current();
    if (repository.isEmpty()) {
      return;
    }
    GitRepository repo = repository.get();
    String message = panel.message();
    List<FileChange> included = inclusion.included();
    CommitOptions options = panel.options();
    Job job = Job.create("Committing", monitor -> {
      try {
        commitService.commit(repo, message, included, options);
        asyncExec(() -> afterCommit(repo, message, included.size(), andPush));
      }
      catch (GitException e) {
        asyncExec(() -> MessageDialog.openError(getSite().getShell(), "Commit Failed", e.userMessage()));
      }
      return Status.OK_STATUS;
    });
    job.schedule();
  }

  private void afterCommit(GitRepository repo, String message, int fileCount, boolean andPush) {
    onCommitted(message);
    panel.afterCommit();
    String subject = message.strip().lines().findFirst().orElse("");
    getViewSite().getActionBars().getStatusLineManager()
      .setMessage(fileCount + (fileCount == 1 ? " file committed: " : " files committed: ") + subject);
    refresh();
    if (andPush) {
      openPushDialog(repo);
    }
  }

  /** Stores the message in the message history. Task 8 fills it. */
  private void onCommitted(String message) {
  }

  /** Shows the message history popup. Task 8 fills it. */
  private void onShowMessageHistory(Point screenLocation) {
  }

  /** Opens the Push dialog. Task 9 fills it. */
  private void openPushDialog(GitRepository repo) {
  }

  private void onAmendChanged(boolean amend) {
    if (amend && panel.message().isBlank()) {
      context.current().ifPresent(repo -> {
        try {
          panel.setMessage(commitService.lastCommitMessage(repo));
        }
        catch (GitException e) {
          MessageDialog.openError(getSite().getShell(), "Amend", e.userMessage());
        }
      });
    }
    updateButtons();
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

  void asyncExec(Runnable runnable) {
    Display display = getSite().getShell().getDisplay();
    if (!display.isDisposed()) {
      display.asyncExec(() -> {
        Control control = tree.viewer().getControl();
        if (!control.isDisposed()) {
          runnable.run();
        }
      });
    }
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
