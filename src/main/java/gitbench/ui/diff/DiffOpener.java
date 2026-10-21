package gitbench.ui.diff;

import gitbench.diff.DiffRequest;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;

import java.util.List;

/** Opens diff tabs. */
public final class DiffOpener {
  private DiffOpener() {
  }

  /** Opens the request at {@code index}; Previous File and Next File move along the chain. An open tab is reused. */
  public static void open(IWorkbenchPage page, List<DiffRequest> chain, int index) {
    if (chain.isEmpty() || index < 0 || index >= chain.size()) {
      return;
    }
    try {
      page.openEditor(new DiffEditorInput(chain, index), DiffEditor.ID, true, IWorkbenchPage.MATCH_INPUT | IWorkbenchPage.MATCH_ID);
    }
    catch (PartInitException e) {
      MessageDialog.openError(page.getWorkbenchWindow().getShell(), "Show Diff", e.getMessage());
    }
  }
}
