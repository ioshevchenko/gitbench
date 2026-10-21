package gitbench.ui.diff;

import gitbench.diff.DiffChange;
import org.eclipse.jface.text.ITextOperationTarget;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;

/** A diff viewer inside the diff editor: Side-by-side or Unified. */
interface DiffPane {
  /** What the pane asks of the editor. */
  interface Host {
    /** Copies the block to the right side: replaces its lines, or appends them when {@code append} is true. */
    void apply(DiffChange change, boolean append);

    /** Handles a key that works in the whole diff editor. Returns true when the key is consumed. */
    boolean handleKey(Event event);

    void contentChanged();
  }

  Control control();

  /** Shows new documents, or new blocks of the same documents. */
  void setContent(DiffContent content);

  /** Moves the caret to the next or the previous block. Returns false when there is none. */
  boolean navigate(boolean next);

  /** The block under the caret, or null. */
  DiffChange changeAtCaret();

  /** The text viewer with the focus, for Undo, Copy and the other global actions; or null. */
  ITextOperationTarget focusedTarget();

  void setFocus();

  void dispose();
}
