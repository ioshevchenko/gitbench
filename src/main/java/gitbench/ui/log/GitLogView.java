package gitbench.ui.log;

import gitbench.git.FileChange;
import gitbench.git.GitException;
import gitbench.git.GitRepository;
import gitbench.git.GitResult;
import gitbench.git.GitRunner;
import gitbench.git.RepositoryLocator;
import gitbench.log.CommitDetails;
import gitbench.log.CommitFilesLoader;
import gitbench.log.DetailsLoader;
import gitbench.log.FilterService;
import gitbench.log.GitRef;
import gitbench.log.LogFilter;
import gitbench.log.LogModel;
import gitbench.log.RefsLoader;
import gitbench.log.TopologyLoader;
import gitbench.ui.Activator;
import gitbench.ui.GitbenchIcons;
import gitbench.ui.GitbenchIcons.Icon;
import gitbench.ui.RepositoryContext;
import gitbench.ui.RepositoryEvents;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuCreator;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * The Git Log view: the filter bar on top, the commit table with the graph on the left, the files and the details
 * of the selected commit on the right, as the Log tab of the IntelliJ Git tool window.
 */
public final class GitLogView extends ViewPart {
  public static final String ID = "gitbench.views.log";
  private static final String PREF_INTELLISORT = "log.intelliSort";
  private static final String PREF_SHOW_DETAILS = "log.showDetails";
  private static final String PREF_SHOW_FILES = "log.showFiles";
  private static final int DETAILS_BATCH = 1000;

  private GitRunner runner;
  private RefsLoader refsLoader;
  private TopologyLoader topologyLoader;
  private DetailsLoader detailsLoader;
  private FilterService filterService;
  private CommitFilesLoader filesLoader;
  private RepositoryContext context;
  private Display display;

  private FilterBar filterBar;
  private CommitTable table;
  private SashForm rightSide;
  private SashForm mainSash;
  private CommitFilesTree files;
  private CommitDetailsPanel detailsPanel;
  private LogContextMenu contextMenu;

  private GitRepository loadedRepository;
  private List<GitRef> refs = List.of();
  private LogModel fullModel;
  private String myEmail;
  private Range range;
  private final Map<String, CommitDetails> details = new ConcurrentHashMap<>();
  private final Set<String> pendingDetails = ConcurrentHashMap.newKeySet();
  private int loadGeneration;
  private int filterGeneration;
  private int selectionGeneration;
  private IPartListener2 activationListener;
  private final Consumer<GitRepository> repositoryListener = repository -> asyncExec(() -> {
    if (context.current().equals(Optional.of(repository))) {
      reload(true);
    }
  });

  /** A "Compare with Current" range: commits of {@code include} that are not in {@code exclude}. */
  private record Range(String exclude, String include) {
  }

  @Override
  public void createPartControl(Composite parent) {
    display = parent.getDisplay();
    runner = Activator.getDefault().gitRunner();
    refsLoader = new RefsLoader(runner);
    topologyLoader = new TopologyLoader(runner);
    detailsLoader = new DetailsLoader(runner);
    filterService = new FilterService(runner);
    filesLoader = new CommitFilesLoader(runner);
    IPreferenceStore store = Activator.getDefault().getPreferenceStore();
    store.setDefault(PREF_INTELLISORT, true);
    store.setDefault(PREF_SHOW_DETAILS, true);
    store.setDefault(PREF_SHOW_FILES, true);

    Composite root = new Composite(parent, SWT.NONE);
    GridLayout layout = new GridLayout(1, false);
    layout.marginWidth = 0;
    layout.marginHeight = 0;
    layout.verticalSpacing = 0;
    root.setLayout(layout);

    filterBar = new FilterBar(root, new FilterBar.Listener() {
      @Override
      public void filterChanged(LogFilter filter) {
        applyFilter();
      }

      @Override
      public void intelliSortChanged(boolean on) {
        store.setValue(PREF_INTELLISORT, on);
        reload(true);
      }

      @Override
      public void rangeCleared() {
        range = null;
        filterBar.setRange(null);
        applyFilter();
      }
    }, () -> refs, this::knownAuthors, () -> myEmail, () -> context.current().map(GitRepository::root).orElse(null));
    filterBar.control().setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    filterBar.setIntelliSort(store.getBoolean(PREF_INTELLISORT));

    mainSash = new SashForm(root, SWT.HORIZONTAL);
    mainSash.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
    table = new CommitTable(mainSash, new CommitTable.DetailsSource() {
      @Override
      public CommitDetails get(String hash) {
        return details.get(hash);
      }

      @Override
      public void request(int fromRow, int toRow) {
        requestDetails(fromRow);
      }
    }, () -> myEmail);
    rightSide = new SashForm(mainSash, SWT.VERTICAL);
    files = new CommitFilesTree(rightSide);
    files.viewer().addDoubleClickListener(e -> openSelectedFiles());
    detailsPanel = new CommitDetailsPanel(rightSide);
    rightSide.setWeights(50, 50);
    mainSash.setWeights(70, 30);
    updateRightSide();

    table.table().addListener(SWT.Selection, e -> showSelection());
    table.table().addListener(SWT.KeyDown, this::onKey);
    contextMenu = new LogContextMenu(this);
    contextMenu.install(table.table());

    createToolbar(getViewSite().getActionBars().getToolBarManager());

    context = new RepositoryContext(getSite().getPage(), new RepositoryLocator(runner));
    context.addListener(() -> reload(false));
    RepositoryEvents.listen(repositoryListener);
    activationListener = new IPartListener2() {
      @Override
      public void partActivated(IWorkbenchPartReference ref) {
        if (ref.getPart(false) == GitLogView.this) {
          reload(false);
        }
      }
    };
    getSite().getPage().addPartListener(activationListener);
    reload(false);
  }

  @Override
  public void setFocus() {
    table.table().setFocus();
  }

  @Override
  public void dispose() {
    RepositoryEvents.unlisten(repositoryListener);
    if (activationListener != null) {
      getSite().getPage().removePartListener(activationListener);
    }
    if (context != null) {
      context.dispose();
    }
    super.dispose();
  }

  /** Shows only the given branches, as a selection in the Branches view does. */
  public void showRefFilter(List<String> branches) {
    filterBar.setFilter(filterBar.filter().withBranches(branches));
    applyFilter();
  }

  /** Selects the commit, and clears the filters when they hide it. */
  public void selectCommit(String hash) {
    if (!table.select(hash) && fullModel != null && fullModel.rowOf(hash) >= 0) {
      filterBar.setFilter(LogFilter.none());
      range = null;
      filterBar.setRange(null);
      table.setModel(fullModel);
      table.select(hash);
    }
  }

  /** Compare with Current: shows the commits of {@code includeRef} that are not in {@code excludeRef}. */
  public void showRange(String excludeRef, String includeRef) {
    range = new Range(excludeRef, includeRef);
    filterBar.setRange(excludeRef + ".." + includeRef);
    applyFilter();
  }

  public LogFilter currentFilter() {
    return filterBar.filter();
  }

  Optional<GitRepository> currentRepository() {
    return context.current();
  }

  List<GitRef> refs() {
    return refs;
  }

  LogModel shownModel() {
    return table.model();
  }

  List<String> selectedHashes() {
    return table.selectedHashes();
  }

  CommitDetails detailsOf(String hash) {
    return details.get(hash);
  }

  /** Reloads refs and, when they changed or {@code force} is set, the whole graph. */
  public void reload(boolean force) {
    Optional<GitRepository> repository = context.current();
    if (repository.isEmpty()) {
      setContentDescription(context.error() != null ? context.error() : "No Git repository. Select a project that is under Git.");
      return;
    }
    GitRepository repo = repository.get();
    boolean intelliSort = Activator.getDefault().getPreferenceStore().getBoolean(PREF_INTELLISORT);
    int generation = ++loadGeneration;
    Job job = Job.create("Loading Git log", monitor -> {
      try {
        List<GitRef> newRefs = refsLoader.load(repo);
        boolean sameRepository = repo.equals(loadedRepository);
        if (!force && sameRepository && newRefs.equals(refs) && fullModel != null && fullModel.intelliSort() == intelliSort) {
          return Status.OK_STATUS;
        }
        LogModel model = LogModel.build(topologyLoader.load(repo, newRefs), newRefs, intelliSort);
        String email = readEmail(repo);
        asyncExec(() -> {
          if (generation != loadGeneration) {
            return;
          }
          if (!sameRepository) {
            details.clear();
            range = null;
            filterBar.setRange(null);
            filterBar.setFilter(LogFilter.none());
          }
          loadedRepository = repo;
          refs = newRefs;
          fullModel = model;
          myEmail = email;
          setContentDescription("");
          applyFilter();
        });
      }
      catch (GitException e) {
        asyncExec(() -> setContentDescription(e.userMessage()));
      }
      return Status.OK_STATUS;
    });
    job.setSystem(true);
    job.schedule();
  }

  /** Runs the commit filters in the background and shows the filtered graph. */
  private void applyFilter() {
    LogModel model = fullModel;
    GitRepository repo = loadedRepository;
    if (model == null || repo == null) {
      return;
    }
    LogFilter filter = filterBar.filter();
    Range activeRange = range;
    int generation = ++filterGeneration;
    if (!filter.hasCommitFilter() && activeRange == null && filter.branches().isEmpty()) {
      table.setModel(model);
      showSelection();
      return;
    }
    Job job = Job.create("Filtering Git log", monitor -> {
      try {
        Set<Integer> heads = null;
        if (!filter.branches().isEmpty()) {
          heads = refs.stream().filter(ref -> filter.branches().contains(ref.name()))
            .map(ref -> model.commitIndexOf(ref.hash())).filter(i -> i >= 0).collect(Collectors.toSet());
        }
        Set<String> matched = filterService.matching(repo, filter);
        if (activeRange != null) {
          Set<String> inRange = filterService.range(repo, activeRange.exclude(), activeRange.include());
          if (matched == null) {
            matched = inRange;
          }
          else {
            matched.retainAll(inRange);
          }
        }
        Set<Integer> matchedIds = null;
        if (matched != null) {
          matchedIds = new HashSet<>();
          for (String hash : matched) {
            int index = model.commitIndexOf(hash);
            if (index >= 0) {
              matchedIds.add(index);
            }
          }
        }
        LogModel filtered = model.filtered(heads, matchedIds);
        asyncExec(() -> {
          if (generation == filterGeneration) {
            table.setModel(filtered);
            showSelection();
          }
        });
      }
      catch (GitException e) {
        asyncExec(() -> setContentDescription(e.userMessage()));
      }
      return Status.OK_STATUS;
    });
    job.setSystem(true);
    job.schedule();
  }

  /** Loads the details of up to {@value #DETAILS_BATCH} rows starting a little above {@code row}. */
  private void requestDetails(int row) {
    LogModel model = table.model();
    GitRepository repo = loadedRepository;
    if (model == null || repo == null) {
      return;
    }
    int from = Math.max(0, row - 100);
    int to = Math.min(model.rowCount(), from + DETAILS_BATCH);
    List<String> missing = new ArrayList<>();
    for (int i = from; i < to; i++) {
      String hash = model.row(i).hash();
      if (!details.containsKey(hash) && pendingDetails.add(hash)) {
        missing.add(hash);
      }
    }
    if (missing.isEmpty()) {
      return;
    }
    Job job = Job.create("Loading commit details", monitor -> {
      try {
        details.putAll(detailsLoader.load(repo, missing));
      }
      catch (GitException e) {
        asyncExec(() -> setContentDescription(e.userMessage()));
      }
      finally {
        missing.forEach(pendingDetails::remove);
      }
      asyncExec(() -> {
        table.table().redraw();
        showSelection();
      });
      return Status.OK_STATUS;
    });
    job.setSystem(true);
    job.schedule();
  }

  /** Updates the files and the details panel for the selected commits. */
  private void showSelection() {
    List<String> hashes = table.selectedHashes();
    int generation = ++selectionGeneration;
    if (hashes.isEmpty()) {
      files.setFiles(List.of());
      detailsPanel.clear();
      return;
    }
    if (hashes.size() > 1) {
      files.setFiles(List.of());
      List<CommitDetails> known = hashes.stream().map(details::get).filter(d -> d != null).toList();
      detailsPanel.showMany(known);
      return;
    }
    String hash = hashes.get(0);
    CommitDetails d = details.get(hash);
    if (d == null) {
      detailsPanel.showLoading();
    }
    else {
      detailsPanel.show(d, null);
    }
    GitRepository repo = loadedRepository;
    Job job = Job.create("Loading commit", monitor -> {
      try {
        List<FileChange> changed = filesLoader.load(repo, hash);
        CommitDetails loaded = d != null ? d : detailsLoader.load(repo, List.of(hash)).get(hash);
        asyncExec(() -> {
          if (generation == selectionGeneration) {
            files.setFiles(changed);
            if (loaded != null) {
              details.put(hash, loaded);
              detailsPanel.show(loaded, null);
            }
          }
        });
        List<String> branches = containingBranches(repo, hash);
        asyncExec(() -> {
          if (generation == selectionGeneration && loaded != null) {
            detailsPanel.show(loaded, branches);
          }
        });
      }
      catch (GitException e) {
        asyncExec(() -> detailsPanel.control().setText(e.userMessage()));
      }
      return Status.OK_STATUS;
    });
    job.setSystem(true);
    job.schedule();
  }

  private List<String> containingBranches(GitRepository repo, String hash) throws GitException {
    GitResult result = runner.run(repo.root(), List.of("branch", "-a", "--contains", hash, "--format=%(refname:short)"), null);
    if (!result.ok()) {
      return List.of();
    }
    return result.stdoutText().lines().map(String::trim).filter(s -> !s.isEmpty() && !s.endsWith("/HEAD")).toList();
  }

  private String readEmail(GitRepository repo) {
    try {
      GitResult result = runner.run(repo.root(), List.of("config", "--get", "user.email"), null);
      return result.ok() ? result.stdoutText().trim() : null;
    }
    catch (GitException e) {
      return null;
    }
  }

  private List<String> knownAuthors() {
    Set<String> names = new LinkedHashSet<>();
    details.values().forEach(d -> names.add(d.authorName()));
    return new ArrayList<>(names);
  }

  private void openSelectedFiles() {
    gitbench.ui.commit.OpenFiles.open(getSite().getPage(), context.current(), files.selectedFiles());
  }

  private void onKey(Event e) {
    if ((e.stateMask & SWT.MOD1) != 0 && (e.keyCode == 'f' || e.keyCode == 'F')) {
      filterBar.focusText();
      e.doit = false;
    }
    else if ((e.stateMask & SWT.MOD1) != 0 && (e.keyCode == 'c' || e.keyCode == 'C')) {
      copyHashes();
      e.doit = false;
    }
  }

  void copyHashes() {
    List<String> hashes = table.selectedHashes();
    if (hashes.isEmpty()) {
      return;
    }
    Clipboard clipboard = new Clipboard(display);
    try {
      clipboard.setContents(new Object[]{String.join(System.lineSeparator(), hashes)}, new Transfer[]{TextTransfer.getInstance()});
    }
    finally {
      clipboard.dispose();
    }
  }

  void select(String hash) {
    table.select(hash);
  }

  private void updateRightSide() {
    IPreferenceStore store = Activator.getDefault().getPreferenceStore();
    boolean showFiles = store.getBoolean(PREF_SHOW_FILES);
    boolean showDetails = store.getBoolean(PREF_SHOW_DETAILS);
    files.viewer().getControl().setVisible(showFiles);
    detailsPanel.control().setVisible(showDetails);
    if (!showFiles && !showDetails) {
      mainSash.setMaximizedControl(table.table());
    }
    else {
      mainSash.setMaximizedControl(null);
      rightSide.setMaximizedControl(!showFiles ? detailsPanel.control() : !showDetails ? files.viewer().getControl() : null);
    }
  }

  private void createToolbar(IToolBarManager toolbar) {
    toolbar.add(action("Refresh", Icon.REFRESH, () -> reload(true)));
    toolbar.add(action("Fetch", Icon.FETCH, this::fetch));
    toolbar.add(new ViewOptionsAction());
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

  private static Action action(String text, Icon icon, Runnable run) {
    Action action = new Action(text) {
      @Override
      public void run() {
        run.run();
      }
    };
    action.setToolTipText(text);
    action.setImageDescriptor(GitbenchIcons.descriptor(icon));
    return action;
  }

  void asyncExec(Runnable runnable) {
    if (display == null || display.isDisposed()) {
      return;
    }
    display.asyncExec(() -> {
      Control control = table.table();
      if (!control.isDisposed()) {
        runnable.run();
      }
    });
  }

  /** The eye button: IntelliSort, Show Details, Show Changed Files. */
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
      IPreferenceStore store = Activator.getDefault().getPreferenceStore();
      toggle(menu, "IntelliSort", PREF_INTELLISORT, () -> {
        filterBar.setIntelliSort(store.getBoolean(PREF_INTELLISORT));
        reload(true);
      });
      toggle(menu, "Show Details", PREF_SHOW_DETAILS, GitLogView.this::updateRightSide);
      toggle(menu, "Show Changed Files", PREF_SHOW_FILES, GitLogView.this::updateRightSide);
      return menu;
    }

    private void toggle(Menu parent, String text, String key, Runnable after) {
      IPreferenceStore store = Activator.getDefault().getPreferenceStore();
      MenuItem item = new MenuItem(parent, SWT.CHECK);
      item.setText(text);
      item.setSelection(store.getBoolean(key));
      item.addListener(SWT.Selection, e -> {
        store.setValue(key, item.getSelection());
        after.run();
      });
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
