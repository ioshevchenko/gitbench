package gitbench.ui.status;

import gitbench.diff.BlockKind;
import gitbench.diff.DiffChange;
import org.eclipse.swt.graphics.GC;

import java.util.List;

/** Paints the change markers of a ruler column, as the IntelliJ LineStatusMarkerDrawUtil of the New UI. */
public final class ChangeMarkerPainter {
  /**
   * The width of the column: the marker area and a gap of 3 px on the right. Eclipse leaves the folding column next to
   * this one light in the dark theme, and without the gap the marker runs into it.
   */
  public static final int WIDTH = 10;
  /** The left edge of a marker: 1 px for brace highlighters and 2 px more for a 4 px marker (IDEA-286352). */
  private static final int MARKER_X = 3;
  /** Gutter.VcsChanges.width of the New UI. */
  private static final int MARKER_WIDTH = 4;
  /** A hovered marker grows to the left by this much (getHoveredMarkerExtraWidth). */
  private static final int HOVER_EXTRA = 3;
  /** Half the height of a deletion marker (getTriangleHeight of the New UI). */
  private static final int DELETION_HALF = 5;

  private ChangeMarkerPainter() {
  }

  /** Paints the markers of the visible ranges; {@code hovered} is the index of the range under the mouse, or -1. */
  public static void paint(GC gc, List<DiffChange> ranges, LineGeometry geometry, int hovered, boolean dark) {
    int endX = MARKER_X + MARKER_WIDTH;
    for (int i = 0; i < ranges.size(); i++) {
      DiffChange range = ranges.get(i);
      if (!shows(range, geometry)) {
        continue;
      }
      int x = i == hovered ? MARKER_X - HOVER_EXTRA : MARKER_X;
      int width = endX - x;
      gc.setBackground(ChangeMarkerColors.color(range.kind(), dark));
      int y = geometry.top(range.startLine2());
      if (range.kind() == BlockKind.DELETED) {
        y = Math.max(y, DELETION_HALF);
        // RectanglePainter2D.FILL with the arc of the width: a bar with round ends.
        gc.fillRoundRectangle(x, y - DELETION_HALF + 1, width, 2 * DELETION_HALF - 2, width, width);
      }
      else {
        gc.fillRoundRectangle(x, y + 1, width, bottom(range, geometry) - y - 2, width, width);
      }
    }
  }

  /**
   * Whether the range shows: it is near the visible lines, and a deletion is not inside a fold. A block of lines that
   * starts or ends inside a fold shows on the fold line, as IntelliJ draws it.
   */
  private static boolean shows(DiffChange range, LineGeometry geometry) {
    // Compared without + 1: the last visible line may be Integer.MAX_VALUE.
    if (range.endLine2() < geometry.firstVisibleLine() || range.startLine2() - 1 > geometry.lastVisibleLine()) {
      return false;
    }
    return range.kind() != BlockKind.DELETED || geometry.visible(range.startLine2());
  }

  /** The bottom of a block of lines; a block that is folded away has the height of its fold line. */
  private static int bottom(DiffChange range, LineGeometry geometry) {
    int y = geometry.top(range.startLine2());
    int endY = geometry.top(range.endLine2());
    return endY > y ? endY : y + geometry.lineHeight();
  }

  /**
   * The index of the range under {@code y}, or -1. A deletion has no height, so the mouse may miss it by a third of a
   * line, as isBlockUnderY allows.
   */
  public static int rangeAt(List<DiffChange> ranges, LineGeometry geometry, int y) {
    int gap = geometry.lineHeight() / 3;
    for (int i = 0; i < ranges.size(); i++) {
      DiffChange range = ranges.get(i);
      if (!shows(range, geometry)) {
        continue;
      }
      int startY = geometry.top(range.startLine2());
      int endY = range.kind() == BlockKind.DELETED ? startY : bottom(range, geometry);
      if (startY == endY) {
        startY -= gap;
        endY += gap;
      }
      if (startY <= y && y < endY) {
        return i;
      }
    }
    return -1;
  }
}
