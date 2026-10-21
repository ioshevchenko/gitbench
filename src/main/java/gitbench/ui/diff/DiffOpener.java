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
    open(page, chain, index, -1);
  }

  /** Opens the request and shows the block at {@code revealLine} of the right side; -1 keeps the first block. */
  public static void open(IWorkbenchPage page, List<DiffRequest> chain, int index, int revealLine) {
    if (chain.isEmpty() || index < 0 || index >= chain.size()) {
      return;
    }
    try {
      org.eclipse.ui.IEditorPart editor =
        page.openEditor(new DiffEditorInput(chain, index), DiffEditor.ID, true, IWorkbenchPage.MATCH_INPUT | IWorkbenchPage.MATCH_ID);
      if (revealLine >= 0 && editor instanceof DiffEditor diff) {
        diff.revealRightLine(revealLine);
      }
    }
    catch (PartInitException e) {
      MessageDialog.openError(page.getWorkbenchWindow().getShell(), "Show Diff", e.getMessage());
    }
  }
}
