package gitbench.ui;

import gitbench.git.GitException;
import gitbench.git.GitRepository;
import gitbench.git.RepositoryCache;
import gitbench.git.RepositoryLocator;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.Adapters;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IPartListener2;
import org.eclipse.ui.ISelectionListener;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchPartReference;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Tracks the repository that the Gitbench views show. The repository follows the project that is selected in a
 * view or open in the active editor. The user can also pick one of the workspace repositories by hand.
 * All git lookups run in a background job; listeners are called on the UI thread.
 */
public final class RepositoryContext {
  /** Receives changes of the current repository or of the lookup error. Called on the UI thread. */
  public interface Listener {
    void changed();
  }

  private final IWorkbenchPage page;
  private final Display display;
  private final RepositoryCache cache;
  private final List<Listener> listeners = new ArrayList<>();
  private Optional<GitRepository> current = Optional.empty();
  private List<GitRepository> workspaceRepositories = List.of();
  private String error;
  private boolean disposed;

  private final ISelectionListener selectionListener = this::selectionChanged;
  private final IPartListener2 partListener = new IPartListener2() {
    @Override
    public void partActivated(IWorkbenchPartReference ref) {
      IWorkbenchPart part = ref.getPart(false);
      if (part instanceof IEditorPart editor) {
        IResource resource = Adapters.adapt(editor.getEditorInput(), IResource.class);
        if (resource != null) {
          resolve(folderOf(resource));
        }
      }
    }
  };
  private final IPropertyChangeListener preferenceListener = event -> {
    if (Activator.PREF_GIT_PATH.equals(event.getProperty())) {
      cache().clear();
      resolve(null);
    }
  };

  public RepositoryContext(IWorkbenchPage page, RepositoryLocator locator) {
    this.page = page;
    this.display = page.getWorkbenchWindow().getShell().getDisplay();
    this.cache = new RepositoryCache(locator);
    page.addPostSelectionListener(selectionListener);
    page.addPartListener(partListener);
    Activator.getDefault().getPreferenceStore().addPropertyChangeListener(preferenceListener);
    Path selected = null;
    if (page.getSelection() instanceof IStructuredSelection structured && !structured.isEmpty()) {
      IResource resource = Adapters.adapt(structured.getFirstElement(), IResource.class);
      selected = resource == null ? null : folderOf(resource);
    }
    resolve(selected);
  }

  public Optional<GitRepository> current() {
    return current;
  }

  /** The repositories of all open workspace projects, as of the last lookup. */
  public List<GitRepository> workspaceRepositories() {
    return workspaceRepositories;
  }

  /** The text of the last git failure, or {@code null}. */
  public String error() {
    return error;
  }

  public void addListener(Listener listener) {
    listeners.add(listener);
  }

  /** Selects a repository by hand, for example from the repository combo of a view. */
  public void select(GitRepository repository) {
    if (!Optional.of(repository).equals(current)) {
      current = Optional.of(repository);
      notifyListeners();
    }
  }

  public void dispose() {
    disposed = true;
    page.removePostSelectionListener(selectionListener);
    page.removePartListener(partListener);
    Activator.getDefault().getPreferenceStore().removePropertyChangeListener(preferenceListener);
    listeners.clear();
  }

  private RepositoryCache cache() {
    return cache;
  }

  private void selectionChanged(IWorkbenchPart part, ISelection selection) {
    if (selection instanceof IStructuredSelection structured && !structured.isEmpty()) {
      IResource resource = Adapters.adapt(structured.getFirstElement(), IResource.class);
      if (resource != null) {
        Path folder = folderOf(resource);
        if (folder != null) {
          resolve(folder);
        }
      }
    }
  }

  /**
   * Finds the repository of {@code folder} (or keeps the current one when {@code folder} is null) and the list of
   * workspace repositories, in a background job.
   */
  private void resolve(Path folder) {
    List<Path> projectFolders = new ArrayList<>();
    for (IProject project : ResourcesPlugin.getWorkspace().getRoot().getProjects()) {
      if (project.isOpen() && project.getLocation() != null) {
        projectFolders.add(project.getLocation().toFile().toPath());
      }
    }
    Job job = Job.create("Finding Git repository", monitor -> {
      String failure = null;
      Optional<GitRepository> followed = Optional.empty();
      Set<GitRepository> all = new LinkedHashSet<>();
      try {
        if (folder != null) {
          followed = cache.find(folder);
        }
        for (Path projectFolder : projectFolders) {
          cache.find(projectFolder).ifPresent(all::add);
        }
      }
      catch (GitException e) {
        failure = e.userMessage();
      }
      String finalFailure = failure;
      Optional<GitRepository> finalFollowed = followed;
      List<GitRepository> finalAll = List.copyOf(all);
      if (!display.isDisposed()) {
        display.asyncExec(() -> apply(finalFollowed, finalAll, finalFailure));
      }
      return Status.OK_STATUS;
    });
    job.setSystem(true);
    job.schedule();
  }

  private void apply(Optional<GitRepository> followed, List<GitRepository> all, String failure) {
    if (disposed) {
      return;
    }
    Optional<GitRepository> next = current;
    if (followed.isPresent()) {
      next = followed;
    }
    else if (next.isEmpty() || !all.contains(next.get())) {
      next = all.isEmpty() ? Optional.empty() : Optional.of(all.get(0));
    }
    boolean changed = !next.equals(current) || !all.equals(workspaceRepositories) || !java.util.Objects.equals(failure, error);
    current = failure == null ? next : current;
    workspaceRepositories = all;
    error = failure;
    if (changed) {
      notifyListeners();
    }
  }

  private void notifyListeners() {
    List.copyOf(listeners).forEach(Listener::changed);
  }

  private static Path folderOf(IResource resource) {
    IResource container = resource.getType() == IResource.FILE ? resource.getParent() : resource;
    IPath location = container.getLocation();
    return location == null ? null : location.toFile().toPath();
  }
}
