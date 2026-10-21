package gitbench.ui.status;

/** Where the lines of a document are on a ruler column, in pixels of the column. */
public interface LineGeometry {
  int lineHeight();

  /** The top of the line; for the line after the last one, the bottom of the last line. */
  int top(int modelLine);

  /** False for a line inside a folded region. */
  boolean visible(int modelLine);

  /** The first document line that shows, even in part; the painters skip the lines above it. */
  default int firstVisibleLine() {
    return 0;
  }

  /** The last document line that shows, even in part. */
  default int lastVisibleLine() {
    return Integer.MAX_VALUE;
  }
}
