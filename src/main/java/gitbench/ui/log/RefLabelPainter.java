package gitbench.ui.log;

import gitbench.log.GitRef;
import gitbench.log.RefType;
import gitbench.ui.GitbenchIcons;
import gitbench.ui.GitbenchIcons.Icon;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;

import java.util.List;

/**
 * Paints the branch and tag labels at the right side of the subject cell, as {@code LabelPainter} in IntelliJ does:
 * one icon per ref (slightly overlapping), then the names joined with " &amp; ", cut with "…" when there is no room.
 */
final class RefLabelPainter {
  private static final int MAX_ICONS = 3;
  private static final int ICON_SIZE = 16;
  private static final int ICON_STEP = 6;
  private static final int GAP = 4;
  private static final String ELLIPSIS = "…";

  private RefLabelPainter() {
  }

  /** The refs to show: everything except HEAD. */
  static List<GitRef> visible(List<GitRef> refs) {
    return refs.stream().filter(ref -> ref.type() != RefType.HEAD).toList();
  }

  /** Returns the width the labels need, but not more than {@code maxWidth}. */
  static int width(GC gc, List<GitRef> refs, int maxWidth) {
    List<GitRef> shown = visible(refs);
    if (shown.isEmpty()) {
      return 0;
    }
    int full = iconsWidth(shown) + GAP + gc.textExtent(text(shown)).x + GAP;
    return Math.min(full, maxWidth);
  }

  /** Paints the labels so that they end at {@code right}. */
  static void paint(GC gc, List<GitRef> refs, int right, int y, int height, int width, Color textColor) {
    List<GitRef> shown = visible(refs);
    if (shown.isEmpty() || width <= 0) {
      return;
    }
    int x = right - width;
    int icons = Math.min(shown.size(), MAX_ICONS);
    for (int i = icons - 1; i >= 0; i--) {
      Image image = GitbenchIcons.get(iconOf(shown.get(i)));
      gc.drawImage(image, x + i * ICON_STEP, y + (height - ICON_SIZE) / 2);
    }
    x += iconsWidth(shown) + GAP;
    String text = fit(gc, text(shown), right - GAP - x);
    Point extent = gc.textExtent(text);
    Color old = gc.getForeground();
    gc.setForeground(textColor);
    gc.drawText(text, x, y + (height - extent.y) / 2, true);
    gc.setForeground(old);
  }

  private static Icon iconOf(GitRef ref) {
    return ref.current() ? Icon.CURRENT_BRANCH_LABEL : Icon.BRANCH_LABEL;
  }

  private static int iconsWidth(List<GitRef> refs) {
    return ICON_SIZE + (Math.min(refs.size(), MAX_ICONS) - 1) * ICON_STEP;
  }

  private static String text(List<GitRef> refs) {
    return String.join(" & ", refs.stream().map(GitRef::name).toList());
  }

  private static String fit(GC gc, String text, int available) {
    if (available <= 0) {
      return "";
    }
    if (gc.textExtent(text).x <= available) {
      return text;
    }
    for (int length = text.length() - 1; length > 0; length--) {
      String cut = text.substring(0, length) + ELLIPSIS;
      if (gc.textExtent(cut).x <= available) {
        return cut;
      }
    }
    return ELLIPSIS;
  }
}
