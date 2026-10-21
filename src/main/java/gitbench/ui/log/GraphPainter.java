package gitbench.ui.log;

import com.intellij.vcs.log.graph.EdgePrintElement;
import com.intellij.vcs.log.graph.NodePrintElement;
import com.intellij.vcs.log.graph.PrintElement;
import com.intellij.vcs.log.graph.impl.print.elements.TerminalEdgePrintElement;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.LineAttributes;
import org.eclipse.swt.graphics.Path;
import org.eclipse.swt.graphics.Rectangle;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Paints the graph part of a log row. A port of {@code SimpleGraphCellPainter}, {@code HeadNodePainter} and
 * {@code PaintParameters} from IntelliJ, from Graphics2D to SWT GC. Sizes scale with the row height, as in IntelliJ.
 */
public final class GraphPainter {
  private static final int ROW_HEIGHT = 22;
  private static final int WIDTH_NODE = 16;
  private static final int CIRCLE_RADIUS = 4;
  private static final double THICK_LINE = 1.5;
  private static final double SELECT_THICK_LINE = 2.5;
  private static final int HEAD_RADIUS_DELTA = 2;
  private static final double ARROW_ANGLE_COS2 = 0.7;
  private static final double ARROW_LENGTH = 0.3;
  private static final int NEUTRAL_GRAY = 0x808080;

  private final Map<Integer, Color> colors = new HashMap<>();

  /** Returns the width in pixels of a graph with {@code columns} columns. */
  public static int width(int columns, int rowHeight) {
    return (int)Math.ceil(floorOdd(scale(WIDTH_NODE, rowHeight)) * Math.max(columns, 1));
  }

  /**
   * Paints the elements into {@code cell}. {@code background} is the row background; a head node uses it for its
   * middle ring.
   */
  public void paint(GC gc, Rectangle cell, Collection<? extends PrintElement> elements, boolean dark, Color background) {
    int oldAntialias = gc.getAntialias();
    Color oldBackground = gc.getBackground();
    Color oldForeground = gc.getForeground();
    LineAttributes oldLine = gc.getLineAttributes();
    gc.setAntialias(SWT.ON);
    Metrics m = new Metrics(cell.height);
    List<PrintElement> selected = new ArrayList<>();
    for (PrintElement element : elements) {
      if (element.isSelected()) {
        selected.add(element);
      }
      else {
        paintElement(gc, cell, m, element, color(element.getColorId(), dark), false, background);
      }
    }
    Color mark = color(0, dark);
    for (PrintElement element : selected) {
      paintElement(gc, cell, m, element, mark, true, background);
    }
    for (PrintElement element : selected) {
      paintElement(gc, cell, m, element, color(element.getColorId(), dark), false, background);
    }
    gc.setAntialias(oldAntialias);
    gc.setBackground(oldBackground);
    gc.setForeground(oldForeground);
    gc.setLineAttributes(oldLine);
  }

  private Color color(int colorId, boolean dark) {
    int rgb = GraphColors.rgb(colorId, dark);
    return colors.computeIfAbsent(rgb, value -> new Color((value >> 16) & 0xFF, (value >> 8) & 0xFF, value & 0xFF));
  }

  private void paintElement(GC gc, Rectangle cell, Metrics m, PrintElement element, Color color, boolean selected, Color background) {
    gc.setForeground(color);
    gc.setBackground(color);
    if (element instanceof EdgePrintElement edge) {
      paintEdge(gc, cell, m, edge, selected);
    }
    else if (element instanceof NodePrintElement node) {
      paintNode(gc, cell, m, node, selected, color, background);
    }
  }

  private static void paintEdge(GC gc, Rectangle cell, Metrics m, EdgePrintElement edge, boolean selected) {
    boolean down = edge.getType() == EdgePrintElement.Type.DOWN;
    boolean usual = edge.getLineStyle() == EdgePrintElement.LineStyle.SOLID;
    boolean arrow = edge.hasArrow();
    boolean terminal = edge instanceof TerminalEdgePrintElement;
    double x1 = cell.x + m.elementWidth * edge.getPositionInCurrentRow() + m.elementCenter;
    double y1 = cell.y + m.rowCenter;
    if (edge.getPositionInCurrentRow() == edge.getPositionInOtherRow()) {
      double arrowGap = terminal ? m.circleRadius / 2 + 1 : 0.0;
      double y2 = cell.y + (down ? m.rowHeight - arrowGap : arrowGap);
      paintLine(gc, m, x1, y1, x1, y2, x1, y2, arrow, usual, selected);
    }
    else {
      // Non-vertical lines go twice the row height, so that they dock with the next row well (as in IntelliJ).
      double x2 = cell.x + m.elementWidth * edge.getPositionInOtherRow() + m.elementCenter;
      double y2 = cell.y + (down ? m.rowHeight + m.rowCenter : m.rowCenter - m.rowHeight);
      paintLine(gc, m, x1, y1, x2, y2, (x1 + x2) / 2, (y1 + y2) / 2, arrow, usual, selected);
    }
  }

  private static void paintLine(GC gc, Metrics m, double x1, double y1, double x2, double y2, double arrowX, double arrowY,
                                boolean hasArrow, boolean usual, boolean selected) {
    float width = selected ? m.selectedLineThickness : m.lineThickness;
    if (usual || hasArrow) {
      gc.setLineAttributes(new LineAttributes(width, SWT.CAP_ROUND, SWT.JOIN_ROUND));
    }
    else {
      double length = x1 == x2 ? m.rowHeight : Math.hypot(x1 - x2, y1 - y2);
      float[] dash = dashLength(length, m.rowHeight);
      gc.setLineAttributes(new LineAttributes(width, SWT.CAP_ROUND, SWT.JOIN_ROUND, SWT.LINE_CUSTOM, dash, dash[0] / 2, 10));
    }
    drawLine(gc, x1, y1, x2, y2);
    if (hasArrow) {
      double cos = Math.sqrt(ARROW_ANGLE_COS2);
      double sin = Math.sqrt(1 - ARROW_ANGLE_COS2);
      double[] end1 = rotate(x1, y1, arrowX, arrowY, cos, sin, ARROW_LENGTH * m.rowHeight);
      double[] end2 = rotate(x1, y1, arrowX, arrowY, cos, -sin, ARROW_LENGTH * m.rowHeight);
      drawLine(gc, arrowX, arrowY, end1[0], end1[1]);
      drawLine(gc, arrowX, arrowY, end2[0], end2[1]);
    }
  }

  private static void paintNode(GC gc, Rectangle cell, Metrics m, NodePrintElement node, boolean selected, Color color,
                                Color background) {
    double x0 = cell.x + m.elementWidth * node.getPositionInCurrentRow() + m.elementCenter;
    double y0 = cell.y + m.rowCenter;
    double radius = selected ? m.selectedCircleRadius : m.circleRadius;
    double diameter = selected ? m.selectedCircleDiameter : m.circleDiameter;
    switch (node.getNodeType()) {
      case FILL -> fillCircle(gc, x0 - radius, y0 - radius, diameter);
      case OUTLINE_AND_FILL -> paintHeadNode(gc, m, x0, y0, selected, color, background);
      case OUTLINE -> {
        Color gray = new Color((NEUTRAL_GRAY >> 16) & 0xFF, (NEUTRAL_GRAY >> 8) & 0xFF, NEUTRAL_GRAY & 0xFF);
        gc.setForeground(gray);
        Path path = new Path(gc.getDevice());
        path.addArc((float)(x0 - radius), (float)(y0 - radius), (float)diameter, (float)diameter, 0, 360);
        gc.drawPath(path);
        path.dispose();
      }
    }
  }

  /** A head node: an outer circle, a ring in the row background, and an inner circle, as HeadNodePainter does. */
  private static void paintHeadNode(GC gc, Metrics m, double xCenter, double yCenter, boolean selected, Color color, Color background) {
    if (selected) {
      fillCircle(gc, xCenter - m.selectedOuterRadius, yCenter - m.selectedOuterRadius, m.selectedOuterDiameter);
      return;
    }
    double x = xCenter - m.outerRadius;
    double y = yCenter - m.outerRadius;
    double d = m.outerDiameter;
    fillCircle(gc, x, y, d);
    x += m.headDelta;
    y += m.headDelta;
    d -= m.headDelta * 2;
    gc.setBackground(background);
    fillCircle(gc, x, y, d);
    x += m.headDelta;
    y += m.headDelta;
    d -= m.headDelta * 2;
    gc.setBackground(color);
    fillCircle(gc, x, y, d);
  }

  private static void drawLine(GC gc, double x1, double y1, double x2, double y2) {
    Path path = new Path(gc.getDevice());
    path.moveTo((float)x1, (float)y1);
    path.lineTo((float)x2, (float)y2);
    gc.drawPath(path);
    path.dispose();
  }

  private static void fillCircle(GC gc, double x, double y, double diameter) {
    Path path = new Path(gc.getDevice());
    path.addArc((float)x, (float)y, (float)diameter, (float)diameter, 0, 360);
    gc.fillPath(path);
    path.dispose();
  }

  private static double[] rotate(double x, double y, double centerX, double centerY, double cos, double sin, double arrowLength) {
    double translateX = x - centerX;
    double translateY = y - centerY;
    double d = Math.hypot(translateX, translateY);
    double scaleX = arrowLength * translateX / d;
    double scaleY = arrowLength * translateY / d;
    return new double[]{scaleX * cos - scaleY * sin + centerX, scaleX * sin + scaleY * cos + centerY};
  }

  /** One dash and one space fit a vertical edge; a slanted edge gets longer dashes, so that whole dashes fit. */
  private static float[] dashLength(double edgeLength, int rowHeight) {
    int dashCount = Math.max(1, (int)Math.floor(edgeLength / rowHeight));
    float space = rowHeight / 2.0f - 2;
    float dash = (float)(edgeLength / dashCount - space);
    return new float[]{dash, space};
  }

  private static double scale(double value, int rowHeight) {
    return value * rowHeight / ROW_HEIGHT;
  }

  /** PaintUtil.alignToInt(value, FLOOR, ODD) at scale 1: the largest odd integer not above the value. */
  private static double floorOdd(double value) {
    int floor = (int)Math.floor(value);
    return floor % 2 == 1 ? floor : floor - 1;
  }

  /** The sizes of one row, computed once per paint like MyPainter in IntelliJ. */
  private static final class Metrics {
    final int rowHeight;
    final double rowCenter;
    final double elementWidth;
    final double elementCenter;
    final float lineThickness;
    final float selectedLineThickness;
    final double circleDiameter;
    final double circleRadius;
    final double selectedCircleDiameter;
    final double selectedCircleRadius;
    final double outerDiameter;
    final double outerRadius;
    final double selectedOuterDiameter;
    final double selectedOuterRadius;
    final double headDelta;

    Metrics(int rowHeight) {
      this.rowHeight = rowHeight;
      rowCenter = floorOdd(rowHeight / 2.0);
      elementWidth = floorOdd(scale(WIDTH_NODE, rowHeight));
      elementCenter = Math.floor(scale(WIDTH_NODE, rowHeight) / 2);
      lineThickness = (float)Math.max(1, floorOdd(scale(THICK_LINE, rowHeight)));
      selectedLineThickness = (float)Math.max(lineThickness + 2, floorOdd(scale(SELECT_THICK_LINE, rowHeight)));
      circleDiameter = floorOdd(2 * scale(CIRCLE_RADIUS, rowHeight));
      circleRadius = Math.floor(circleDiameter / 2);
      selectedCircleDiameter = circleDiameter + selectedLineThickness - lineThickness;
      selectedCircleRadius = Math.floor(selectedCircleDiameter / 2);
      outerDiameter = floorOdd(2 * scale(CIRCLE_RADIUS + HEAD_RADIUS_DELTA, rowHeight));
      outerRadius = Math.floor(outerDiameter / 2);
      selectedOuterDiameter = outerDiameter + selectedLineThickness - lineThickness;
      selectedOuterRadius = Math.floor(selectedOuterDiameter / 2);
      headDelta = Math.floor(scale(HEAD_RADIUS_DELTA, rowHeight));
    }
  }
}
