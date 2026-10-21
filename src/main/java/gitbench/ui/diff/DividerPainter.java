package gitbench.ui.diff;

import gitbench.diff.DiffChange;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Path;
import org.eclipse.swt.widgets.Control;

/**
 * Paints the band between the two columns: one curved polygon per block, from the block lines on the left to the
 * block lines on the right. Port of DiffDividerDrawUtil.DividerPolygon and DiffDrawUtil.drawCurveTrapezium.
 */
public final class DividerPainter {
  /** DiffDrawUtil.CTRL_PROXIMITY_X: where the control points of the curve lie. */
  private static final double CTRL_PROXIMITY_X = 0.3;

  private DividerPainter() {
  }

  /** The y of the line top in the coordinates of {@code target}. A line after the end lies one line below the last. */
  public static int lineToY(StyledText text, int line, Control target) {
    int y;
    int lineCount = text.getLineCount();
    if (line < lineCount) {
      y = text.getLinePixel(line);
    }
    else {
      y = text.getLinePixel(lineCount - 1) + text.getLineHeight() * (line - lineCount + 1);
    }
    return y + text.toDisplay(0, 0).y - target.toDisplay(0, 0).y;
  }

  static void paint(GC gc, int width, int height, StyledText left, StyledText right, Control divider,
                    Iterable<DiffChange> changes, boolean dark) {
    gc.setAntialias(SWT.ON);
    for (DiffChange change : changes) {
      int start1 = lineToY(left, change.startLine1(), divider);
      int end1 = lineToY(left, change.endLine1(), divider);
      int start2 = lineToY(right, change.startLine2(), divider);
      int end2 = lineToY(right, change.endLine2(), divider);
      if (Math.max(end1, end2) < 0 && Math.max(start1, start2) < 0 || Math.min(start1, start2) > height) {
        continue;
      }
      paintPolygon(gc, width, start1, end1, start2, end2, DiffColors.color(change.kind(), dark));
    }
  }

  /**
   * DividerPolygon.paint: one curved band from the y range {@code [start1, end1)} on the left edge to
   * {@code [start2, end2)} on the right edge. A side without lines becomes a one-pixel line above its insertion point.
   */
  public static void paintPolygon(GC gc, int width, int start1, int end1, int start2, int end2, Color color) {
    int startY1 = end1 - start1 < 2 ? start1 - 1 : start1;
    int endY1 = end1 - start1 < 2 ? start1 : end1 - 1;
    int startY2 = end2 - start2 < 2 ? start2 - 1 : start2;
    int endY2 = end2 - start2 < 2 ? start2 : end2 - 1;
    gc.setAntialias(SWT.ON);
    gc.setBackground(color);
    gc.setForeground(color);
    Path path = new Path(gc.getDevice());
    try {
      curve(path, width, startY1, startY2, true);
      curve(path, width, endY1 + 1, endY2 + 1, false);
      path.close();
      gc.fillPath(path);
    }
    finally {
      path.dispose();
    }
    // A fill of a steep thin band can vanish; the middle line keeps it at least one pixel thick.
    Path middle = new Path(gc.getDevice());
    try {
      middle.moveTo(0, (startY1 + endY1) / 2f);
      addCubic(middle, width, (startY1 + endY1) / 2f, (startY2 + endY2) / 2f);
      gc.setLineWidth(1);
      gc.drawPath(middle);
    }
    finally {
      middle.dispose();
    }
  }

  private static void curve(Path path, int width, float y1, float y2, boolean forward) {
    if (forward) {
      path.moveTo(0, y1);
      addCubic(path, width, y1, y2);
    }
    else {
      path.lineTo(width, y2);
      path.cubicTo((float)(width * (1.0 - CTRL_PROXIMITY_X)), y2, (float)(width * CTRL_PROXIMITY_X), y1, 0, y1);
    }
  }

  private static void addCubic(Path path, int width, float y1, float y2) {
    path.cubicTo((float)(width * CTRL_PROXIMITY_X), y1, (float)(width * (1.0 - CTRL_PROXIMITY_X)), y2, width, y2);
  }
}
