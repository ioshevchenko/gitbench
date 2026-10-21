package gitbench.ui.commit;

import gitbench.git.ChangeKind;
import gitbench.git.FileChange;
import gitbench.git.GitRepository;
import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.ide.IDE;

import java.net.URI;
import java.util.List;
import java.util.Optional;

/** Opens changed files in editors. Deleted files have nothing to open and are skipped. */
final class OpenFiles {
  private OpenFiles() {
  }

  static void open(IWorkbenchPage page, Optional<GitRepository> repository, List<FileChange> changes) {
    if (repository.isEmpty()) {
      return;
    }
    for (FileChange change : changes) {
      if (change.kind() == ChangeKind.DELETED) {
        continue;
      }
      URI uri = repository.get().root().resolve(change.path()).toUri();
      try {
        IFile[] files = ResourcesPlugin.getWorkspace().getRoot().findFilesForLocationURI(uri);
        if (files.length > 0) {
          IDE.openEditor(page, files[0]);
        }
        else {
          IDE.openEditorOnFileStore(page, EFS.getLocalFileSystem().getStore(uri));
        }
      }
      catch (PartInitException e) {
        MessageDialog.openError(page.getWorkbenchWindow().getShell(), "Open File", e.getMessage());
      }
    }
  }
}
