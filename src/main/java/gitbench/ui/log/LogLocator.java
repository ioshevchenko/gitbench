package gitbench.ui.log;

import gitbench.git.GitRepository;
import gitbench.ui.GitView;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;

/** Shows a commit in the log: the open Git view, else the open Git Log view, else a new Git view. */
public final class LogLocator {
  private LogLocator() {
  }

  public static void showCommit(IWorkbenchPage page, GitRepository repository, String hash) {
    try {
      if (page.findView(GitView.ID) == null && page.findView(GitLogView.ID) != null) {
        ((GitLogView)page.showView(GitLogView.ID)).panel().showCommit(repository, hash);
      }
      else {
        IViewPart view = page.showView(GitView.ID);
        ((GitView)view).showCommit(repository, hash);
      }
    }
    catch (PartInitException e) {
      MessageDialog.openError(page.getWorkbenchWindow().getShell(), "Select in Git Log", e.getMessage());
    }
  }
}
