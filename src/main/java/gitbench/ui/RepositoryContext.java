package gitbench.ui;

import gitbench.git.GitException;
import gitbench.git.GitRepository;
import gitbench.git.RepositoryLocator;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.Adapters;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.ISelectionListener;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchPartReference;
import org.eclipse.ui.IPartListener2;
import org.eclipse.ui.IWorkbenchPage;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Tracks the repository that the Gitbench views show. The repository follows the project that is selected in a
 * view or open in the active editor. The user can also pick one of the workspace repositories by hand.
 */
public final class RepositoryContext {
  private final IWorkbenchPage page;
  private final RepositoryLocator locator;
  private final Map<Path, Optional<GitRepository>> cache = new HashMap<>();
  private final List<Consumer<Optional<GitRepository>>> listeners = new ArrayList<>();
  private Optional<GitRepository> current = Optional.empty();

  private final ISelectionListener selectionListener = this::selectionChanged;
  private final IPartListener2 partListener = new IPartListener2() {
    @Override
    public void partActivated(IWorkbenchPartReference ref) {
      IWorkbenchPart part = ref.getPart(false);
      if (part instanceof IEditorPart editor) {
        IResource resource = Adapters.adapt(editor.getEditorInput(), IResource.class);
        if (resource != null) {
          follow(resource);
        }
      }
    }
  };

  public RepositoryContext(IWorkbenchPage page, RepositoryLocator locator) {
    this.page = page;
    this.locator = locator;
    page.addPostSelectionListener(selectionListener);
    page.addPartListener(partListener);
    selectionChanged(null, page.getSelection());
    if (current.isEmpty()) {
      List<GitRepository> all = workspaceRepositories();
      if (!all.isEmpty()) {
        current = Optional.of(all.get(0));
      }
    }
  }

  public Optional<GitRepository> current() {
    return current;
  }

  public void addListener(Consumer<Optional<GitRepository>> listener) {
    listeners.add(listener);
  }

  /** Selects a repository by hand, for example from the repository combo of a view. */
  public void select(GitRepository repository) {
    setCurrent(Optional.of(repository));
  }

  /** Returns the repositories of all open workspace projects, without duplicates. */
  public List<GitRepository> workspaceRepositories() {
    Set<GitRepository> all = new LinkedHashSet<>();
    for (IProject project : ResourcesPlugin.getWorkspace().getRoot().getProjects()) {
      if (project.isOpen()) {
        find(project).ifPresent(all::add);
      }
    }
    return List.copyOf(all);
  }

  public void dispose() {
    page.removePostSelectionListener(selectionListener);
    page.removePartListener(partListener);
    listeners.clear();
  }

  private void selectionChanged(IWorkbenchPart part, ISelection selection) {
    if (selection instanceof IStructuredSelection structured && !structured.isEmpty()) {
      IResource resource = Adapters.adapt(structured.getFirstElement(), IResource.class);
      if (resource != null) {
        follow(resource);
      }
    }
  }

  private void follow(IResource resource) {
    Optional<GitRepository> repository = find(resource);
    if (repository.isPresent()) {
      setCurrent(repository);
    }
  }

  private Optional<GitRepository> find(IResource resource) {
    IResource container = resource.getType() == IResource.FILE ? resource.getParent() : resource;
    IPath location = container.getLocation();
    if (location == null) {
      return Optional.empty();
    }
    Path path = location.toFile().toPath();
    return cache.computeIfAbsent(path, p -> {
      try {
        return locator.find(p);
      }
      catch (GitException e) {
        return Optional.empty();
      }
    });
  }

  private void setCurrent(Optional<GitRepository> repository) {
    if (repository.equals(current)) {
      return;
    }
    current = repository;
    List.copyOf(listeners).forEach(listener -> listener.accept(repository));
  }
}
