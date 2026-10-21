package gitbench.ui;

import gitbench.git.GitRepository;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;

import java.nio.file.Path;

/**
 * Brings the Eclipse workspace in sync with the disk after git changed files. Without it, Project Explorer and the
 * CDT build see stale files until the user presses F5.
 */
public final class WorkspaceSync {
  private WorkspaceSync() {
  }

  /** Refreshes every open project that lies inside the repository or contains it. Call it from a job. */
  public static void refresh(GitRepository repository) {
    Path root = repository.root();
    for (IProject project : ResourcesPlugin.getWorkspace().getRoot().getProjects()) {
      IPath location = project.getLocation();
      if (!project.isOpen() || location == null) {
        continue;
      }
      Path projectPath = location.toFile().toPath();
      if (projectPath.startsWith(root) || root.startsWith(projectPath)) {
        try {
          project.refreshLocal(IResource.DEPTH_INFINITE, null);
        }
        catch (CoreException e) {
          Activator.getDefault().getLog().log(e.getStatus());
        }
      }
    }
  }
}
