package gitbench.ui.branch;

import gitbench.branch.AheadBehind;
import gitbench.branch.BranchTreeBuilder;
import gitbench.branch.BranchTreeNode;
import gitbench.branch.Favorites;
import gitbench.git.GitException;
import gitbench.git.GitRepository;
import gitbench.git.GitResult;
import gitbench.git.GitRunner;
import gitbench.git.RepositoryLocator;
import gitbench.log.GitRef;
import gitbench.log.RefType;
import gitbench.log.RefsLoader;
import gitbench.ui.Activator;
import gitbench.ui.GitbenchIcons;
import gitbench.ui.GitbenchIcons.Icon;
import gitbench.ui.RepositoryContext;
import gitbench.ui.RepositoryEvents;
import gitbench.ui.log.LogPanel;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;
import org.eclipse.ui.IPartListener2;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IViewSite;
import org.eclipse.ui.IWorkbenchPartSite;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.IWorkbenchPartReference;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The content of the branches panel of the IntelliJ Git Log: a vertical toolbar, a search field, and the HEAD /
 * Local / Remote tree. Selecting branches filters the log. The Branches view and the Git view show it.
 */
public final class BranchesPanel {
  /** The log that the branches drive. */
  public interface LogTarget {
    /** The log, when one is open; null otherwise. */
    LogPanel find();

    /** The log, opened when necessary. */
    LogPanel show() throws PartInitException;
  }

  private static final String PREF_SELECTION_NAVIGATES = "branches.selectionNavigates";
  private static final String PREF_ONLY_MINE = "branches.onlyMine";

  private GitRunner runner;
  private RefsLoader refsLoader;
  private RepositoryContext context;
  private Display display;
  private final IViewPart part;
  private final Consumer<String> description;
  private final LogTarget logs;
  private final Composite content;
  private ToolBar toolbar;
  private Text search;
  private BranchesTreeViewer tree;
  private ToolItem onlyMineItem;
  private List<GitRef> refs = List.of();
  private int loadGeneration;
  private IPartListener2 activationListener;
  private final Consumer<GitRepository> repositoryListener = repository -> asyncExec(() -> {
    if (context.current().equals(Optional.of(repository))) {
      reload();
    }
  });

  /**
   * Makes the vertical toolbar in {@code toolbarParent} and the search field with the tree in {@code contentParent},
   * for the view {@code part}. The caller lays out {@link #toolbar()} and {@link #content()}.
   */
  public BranchesPanel(Composite toolbarParent, Composite contentParent, IViewPart part, Consumer<String> description,
                       LogTarget logs) {
    this.part = part;
    this.description = description;
    this.logs = logs;
    display = contentParent.getDisplay();
    runner = Activator.getDefault().gitRunner();
    refsLoader = new RefsLoader(runner);

    toolbar = new ToolBar(toolbarParent, SWT.VERTICAL | SWT.FLAT);

    content = new Composite(contentParent, SWT.NONE);
    GridLayout contentLayout = new GridLayout(1, false);
    contentLayout.marginWidth = 0;
    contentLayout.marginHeight = 0;
    content.setLayout(contentLayout);
    search = new Text(content, SWT.SEARCH | SWT.ICON_SEARCH | SWT.ICON_CANCEL);
    search.setMessage("Branch or tag");
    search.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    search.addListener(SWT.Modify, e -> rebuild());
    tree = new BranchesTreeViewer(content);
    tree.viewer().getControl().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
    tree.viewer().addSelectionChangedListener(e -> onSelection());

    createToolbar();
    new BranchActions(this).install();

    context = new RepositoryContext(getSite().getPage(), new RepositoryLocator(runner));
    context.addListener(this::reload);
    RepositoryEvents.listen(repositoryListener);
    activationListener = new IPartListener2() {
      @Override
      public void partActivated(IWorkbenchPartReference ref) {
        if (ref.getPart(false) == part) {
          reload();
        }
      }
    };
    getSite().getPage().addPartListener(activationListener);
    reload();
  }

  public Composite content() {
    return content;
  }

  IWorkbenchPartSite getSite() {
    return part.getSite();
  }

  IViewSite getViewSite() {
    return (IViewSite) part.getSite();
  }

  LogTarget logs() {
    return logs;
  }

  private void setContentDescription(String text) {
    description.accept(text);
  }

  public void setFocus() {
    tree.viewer().getControl().setFocus();
  }

  public void dispose() {
    RepositoryEvents.unlisten(repositoryListener);
    if (activationListener != null) {
      getSite().getPage().removePartListener(activationListener);
    }
    if (context != null) {
      context.dispose();
    }
  }

  Optional<GitRepository> currentRepository() {
    return context.current();
  }

  List<GitRef> refs() {
    return refs;
  }

  BranchesTreeViewer tree() {
    return tree;
  }

  public ToolBar toolbar() {
    return toolbar;
  }

  /** The refs under the selected nodes. */
  List<GitRef> selectedRefs() {
    return BranchesTreeViewer.refsUnder(tree.selectedNodes(), refs);
  }

  Optional<GitRef> currentBranch() {
    return refs.stream().filter(GitRef::current).findFirst();
  }

  /** Reloads refs, counts, and my branches in the background. */
  void reload() {
    Optional<GitRepository> repository = context.current();
    if (repository.isEmpty()) {
      setContentDescription(context.error() != null ? context.error() : "No Git repository. Select a project that is under Git.");
      return;
    }
    GitRepository repo = repository.get();
    int generation = ++loadGeneration;
    boolean onlyMine = store().getBoolean(PREF_ONLY_MINE);
    Job job = Job.create("Loading branches", monitor -> {
      try {
        List<GitRef> newRefs = refsLoader.load(repo);
        Map<String, AheadBehind.Counts> counts = AheadBehind.load(runner, repo);
        Set<String> mine = onlyMine ? BranchTreeBuilder.myBranches(runner, repo) : Set.of();
        asyncExec(() -> {
          if (generation == loadGeneration) {
            refs = newRefs;
            this.counts = counts;
            this.myBranches = mine;
            setContentDescription("");
            rebuild();
          }
        });
      }
      catch (GitException e) {
        asyncExec(() -> setContentDescription(e.userMessage()));
      }
      catch (RuntimeException e) {
        Activator.getDefault().getLog().error("Cannot load branches", e);
        asyncExec(() -> setContentDescription("Cannot load branches: " + e));
      }
      return Status.OK_STATUS;
    });
    job.setSystem(true);
    job.schedule();
  }

  private Map<String, AheadBehind.Counts> counts = Map.of();
  private Set<String> myBranches = Set.of();

  private void rebuild() {
    Optional<GitRepository> repository = context.current();
    if (repository.isEmpty()) {
      return;
    }
    List<BranchTreeNode> nodes = BranchTreeBuilder.build(refs, favorites(repository.get()).all(), counts, search.getText(),
                                                        store().getBoolean(PREF_ONLY_MINE), myBranches);
    // Restoring the selection fires a selection event; it must not apply the filter to the Git Log again.
    rebuilding = true;
    try {
      tree.setNodes(nodes);
    }
    finally {
      rebuilding = false;
    }
  }

  private boolean rebuilding;

  Favorites favorites(GitRepository repository) {
    IPreferenceStore store = store();
    return new Favorites(key -> store.contains(key) ? store.getString(key) : null, store::setValue, repository.root());
  }

  private static IPreferenceStore store() {
    return Activator.getDefault().getPreferenceStore();
  }

  /** Filters the Git Log by the selected branches, or moves the log to the branch head, by the Settings choice. */
  private void onSelection() {
    if (rebuilding) {
      return;
    }
    List<GitRef> selected = selectedRefs();
    if (selected.isEmpty()) {
      return;
    }
    LogPanel log = logs.find();
    if (log == null) {
      return;
    }
    if (store().getBoolean(PREF_SELECTION_NAVIGATES)) {
      log.selectCommit(selected.get(0).hash());
    }
    else {
      log.showRefFilter(selected.stream().map(GitRef::name).toList());
    }
  }

  private void createToolbar() {
    onlyMineItem = item(SWT.CHECK, Icon.SEARCH, "Show My Branches", () -> {
      store().setValue(PREF_ONLY_MINE, onlyMineItem.getSelection());
      reload();
    });
    onlyMineItem.setSelection(store().getBoolean(PREF_ONLY_MINE));
    item(SWT.PUSH, Icon.FETCH, "Fetch", this::fetch);
    item(SWT.PUSH, Icon.STAR, "Mark/Unmark As Favorite", this::toggleFavorite);
    item(SWT.PUSH, Icon.LOCATE, "Navigate Log to Selected Branch", () -> {
      LogPanel log = logs.find();
      List<GitRef> selected = selectedRefs();
      if (log != null && !selected.isEmpty()) {
        log.selectCommit(selected.get(0).hash());
      }
    });
    new ToolItem(toolbar, SWT.SEPARATOR);
    ToolItem settings = item(SWT.PUSH, Icon.SETTINGS, "Settings", () -> { });
    settings.addListener(SWT.Selection, e -> showSettings(settings));
  }

  /** Adds a toolbar button. Task 11 inserts the branch operations above these with {@link #insertItem}. */
  ToolItem item(int style, Icon icon, String tip, Runnable action) {
    return insertItem(toolbar.getItemCount(), style, icon, tip, action);
  }

  public ToolItem insertItem(int index, int style, Icon icon, String tip, Runnable action) {
    ToolItem item = new ToolItem(toolbar, style, index);
    item.setImage(GitbenchIcons.get(icon));
    item.setToolTipText(tip);
    item.addListener(SWT.Selection, e -> action.run());
    return item;
  }

  private void showSettings(ToolItem settings) {
    Menu menu = new Menu(toolbar.getShell(), SWT.POP_UP);
    MenuItem title = new MenuItem(menu, SWT.PUSH);
    title.setText("On Selection");
    title.setEnabled(false);
    MenuItem filter = new MenuItem(menu, SWT.RADIO);
    filter.setText("Filter Log");
    filter.setSelection(!store().getBoolean(PREF_SELECTION_NAVIGATES));
    filter.addListener(SWT.Selection, e -> store().setValue(PREF_SELECTION_NAVIGATES, false));
    MenuItem navigate = new MenuItem(menu, SWT.RADIO);
    navigate.setText("Navigate Log");
    navigate.setSelection(store().getBoolean(PREF_SELECTION_NAVIGATES));
    navigate.addListener(SWT.Selection, e -> store().setValue(PREF_SELECTION_NAVIGATES, true));
    Rectangle bounds = settings.getBounds();
    menu.addListener(SWT.Hide, e -> menu.getDisplay().asyncExec(menu::dispose));
    menu.setLocation(toolbar.toDisplay(bounds.x + bounds.width, bounds.y));
    menu.setVisible(true);
  }

  private void toggleFavorite() {
    context.current().ifPresent(repo -> {
      Favorites favorites = favorites(repo);
      selectedRefs().stream().filter(r -> r.type() == RefType.LOCAL_BRANCH || r.type() == RefType.REMOTE_BRANCH)
        .forEach(r -> favorites.toggle(r.name()));
      rebuild();
    });
  }

  private void fetch() {
    context.current().ifPresent(repo -> {
      Job job = Job.create("Fetching", monitor -> {
        try {
          GitResult result = runner.run(repo.root(), List.of("fetch", "--all", "--prune"), null);
          asyncExec(() -> getViewSite().getActionBars().getStatusLineManager()
            .setMessage(result.ok() ? "Fetched all remotes" : "Fetch failed: " + result.stderr().trim()));
        }
        catch (GitException e) {
          asyncExec(() -> getViewSite().getActionBars().getStatusLineManager().setErrorMessage(e.userMessage()));
        }
        RepositoryEvents.fire(repo);
        return Status.OK_STATUS;
      });
      job.schedule();
    });
  }

  void asyncExec(Runnable runnable) {
    if (display == null || display.isDisposed()) {
      return;
    }
    display.asyncExec(() -> {
      if (!tree.viewer().getControl().isDisposed()) {
        runnable.run();
      }
    });
  }
}
