package gitbench.ui.diff;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.handlers.HandlerUtil;

/**
 * Runs the diff commands of plugin.xml in the active diff tab. The commands carry the IntelliJ key bindings, so the
 * workbench gives these keys to the diff before another binding takes them.
 */
public final class DiffCommandHandler extends AbstractHandler {
  @Override
  public Object execute(ExecutionEvent event) {
    IEditorPart editor = HandlerUtil.getActiveEditor(event);
    if (!(editor instanceof DiffEditor diff)) {
      return null;
    }
    switch (event.getCommand().getId()) {
      case "gitbench.diff.nextDifference" -> diff.nextDifference();
      case "gitbench.diff.previousDifference" -> diff.previousDifference();
      case "gitbench.diff.nextFile" -> diff.nextFile();
      case "gitbench.diff.previousFile" -> diff.previousFile();
      case "gitbench.diff.applyLeftSide" -> diff.applyAtCaret();
      default -> {
      }
    }
    return null;
  }
}
