package gitbench.ui.status;

import gitbench.diff.DiffChange;
import gitbench.status.LineStatus;
import gitbench.ui.diff.DocumentEdits;
import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.texteditor.ITextEditor;

import java.util.List;
import java.util.Optional;

/**
 * The commands of the change markers, as the IntelliJ actions VcsShowNextChangeMarker, VcsShowPrevChangeMarker and
 * Vcs.RollbackChangedLines. They have no key binding; the user can bind them in Preferences → Keys.
 */
public final class ChangeMarkerHandlers {
  private ChangeMarkerHandlers() {
  }

  /** Next Change: moves the caret to the next changed block and opens its popup. */
  public static final class Next extends AbstractHandler {
    @Override
    public Object execute(ExecutionEvent event) {
      move(event, true);
      return null;
    }
  }

  /** Previous Change: moves the caret to the previous changed block and opens its popup. */
  public static final class Previous extends AbstractHandler {
    @Override
    public Object execute(ExecutionEvent event) {
      move(event, false);
      return null;
    }
  }

  /** Rollback Lines: puts the HEAD lines of the block under the caret back. */
  public static final class Rollback extends AbstractHandler {
    @Override
    public Object execute(ExecutionEvent event) {
      Optional<ITextEditor> editor = editor(event);
      Optional<ChangeMarkerColumn> column = editor.flatMap(ChangeMarkerColumn::of);
      Optional<LineStatusTracker> tracker = column.flatMap(ChangeMarkerColumn::tracker);
      if (tracker.isEmpty() || tracker.get().base().isEmpty()) {
        return null;
      }
      int line = caretLine(editor.get());
      List<DiffChange> ranges = tracker.get().ranges();
      for (DiffChange range : ranges) {
        boolean under = range.startLine2() == range.endLine2() ? line == range.startLine2()
                                                               : range.startLine2() <= line && line < range.endLine2();
        if (under) {
          try {
            String base = tracker.get().base().get();
            DocumentEdits.apply(tracker.get().document(), LineStatus.rollback(base, tracker.get().document().get(), range));
          }
          catch (BadLocationException e) {
            // The document changed after the ranges were computed; the next comparison shows the new ranges.
          }
          return null;
        }
      }
      return null;
    }
  }

  private static void move(ExecutionEvent event, boolean next) {
    Optional<ITextEditor> editor = editor(event);
    Optional<ChangeMarkerColumn> column = editor.flatMap(ChangeMarkerColumn::of);
    if (column.isEmpty()) {
      return;
    }
    List<DiffChange> ranges = column.get().ranges();
    int line = caretLine(editor.get());
    int index = next ? LineStatus.next(ranges, line) : LineStatus.previous(ranges, line);
    if (index >= 0) {
      column.get().revealLine(ranges.get(index).startLine2());
      column.get().openPopup(index);
    }
  }

  private static Optional<ITextEditor> editor(ExecutionEvent event) {
    IEditorPart part = HandlerUtil.getActiveEditor(event);
    ITextEditor editor = part == null ? null : part.getAdapter(ITextEditor.class);
    return Optional.ofNullable(editor);
  }

  private static int caretLine(ITextEditor editor) {
    ISelection selection = editor.getSelectionProvider().getSelection();
    return selection instanceof ITextSelection text ? text.getStartLine() : 0;
  }
}
