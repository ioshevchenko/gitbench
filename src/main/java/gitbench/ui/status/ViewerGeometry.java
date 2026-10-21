package gitbench.ui.status;

import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.JFaceTextUtil;
import org.eclipse.swt.custom.StyledText;

/** The line positions of a text viewer for a ruler column; lines in a folded region are not visible. */
public final class ViewerGeometry implements LineGeometry {
  private final ITextViewer viewer;

  public ViewerGeometry(ITextViewer viewer) {
    this.viewer = viewer;
  }

  private StyledText text() {
    return viewer.getTextWidget();
  }

  @Override
  public int lineHeight() {
    return text().getLineHeight();
  }

  /** A folded line has the top of its fold line, the visible line above it, so a block in a fold shows on the fold line. */
  @Override
  public int top(int modelLine) {
    int lines = viewer.getDocument().getNumberOfLines();
    if (modelLine >= lines) {
      return text().getLinePixel(text().getLineCount() - 1) + text().getLineHeight();
    }
    for (int line = modelLine; line >= 0; line--) {
      int widgetLine = JFaceTextUtil.modelLineToWidgetLine(viewer, line);
      if (widgetLine >= 0) {
        return text().getLinePixel(widgetLine);
      }
    }
    return 0;
  }

  @Override
  public int firstVisibleLine() {
    return Math.max(0, JFaceTextUtil.getPartialTopIndex(viewer));
  }

  @Override
  public int lastVisibleLine() {
    return JFaceTextUtil.getPartialBottomIndex(viewer);
  }

  @Override
  public boolean visible(int modelLine) {
    return modelLine >= viewer.getDocument().getNumberOfLines() || JFaceTextUtil.modelLineToWidgetLine(viewer, modelLine) >= 0;
  }

  /** The document line at {@code y} of the column, or -1 below the text. */
  public int lineAt(int y) {
    StyledText text = text();
    int widgetLine = JFaceTextUtil.getLineIndex(text, y);
    if (widgetLine < 0 || y > text.getLinePixel(text.getLineCount() - 1) + text.getLineHeight()) {
      return -1;
    }
    return JFaceTextUtil.widgetLine2ModelLine(viewer, widgetLine);
  }
}
