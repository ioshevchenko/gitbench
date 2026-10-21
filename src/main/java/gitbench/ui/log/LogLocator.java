package gitbench.ui.log;

import gitbench.ui.GitView;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;

/** Shows a commit in the log: the open Git view, else the open Git Log view, else a new Git view. */
public final class LogLocator {
  private LogLocator() {
  }

  public static void showCommit(IWorkbenchPage page, String hash) {
    try {
      LogPanel log;
      if (page.findView(GitView.ID) != null) {
        log = ((GitView)page.showView(GitView.ID)).log();
      }
      else if (page.findView(GitLogView.ID) != null) {
        log = ((GitLogView)page.showView(GitLogView.ID)).panel();
      }
      else {
        IViewPart view = page.showView(GitView.ID);
        log = ((GitView)view).log();
      }
      log.selectCommit(hash);
    }
    catch (PartInitException e) {
      MessageDialog.openError(page.getWorkbenchWindow().getShell(), "Select in Git Log", e.getMessage());
    }
  }
}
