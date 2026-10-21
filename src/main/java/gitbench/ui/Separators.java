package gitbench.ui;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Sash;

/** One-pixel lines between the parts of a view, as the IntelliJ {@code OnePixelSplitter} and {@code SideBorder}. */
public final class Separators {
  private static final String KEY = "gitbench.onePixel";
  private static final Color LIGHT = new Color(0xEB, 0xEC, 0xF0);
  private static final Color DARK = new Color(0x1E, 0x1F, 0x22);
  private static Display filtered;

  private Separators() {
  }

  /** The IntelliJ New UI border color: {@code Borders.color}. */
  public static Color lineColor() {
    return Theme.isDark() ? DARK : LIGHT;
  }

  /**
   * Draws a line in the middle of each sash of {@code form}; the rest of the sash keeps its width for the mouse. The
   * form makes and drops its sashes when a part hides or shows, so a paint filter of the display finds them.
   */
  public static void onePixel(SashForm form) {
    form.setData(KEY, Boolean.TRUE);
    Display display = form.getDisplay();
    if (filtered != display) {
      filtered = display;
      display.addFilter(SWT.Paint, Separators::paintSash);
    }
  }

  private static void paintSash(Event e) {
    if (!(e.widget instanceof Sash sash) || sash.getParent().getData(KEY) == null) {
      return;
    }
    Point size = sash.getSize();
    e.gc.setBackground(sash.getParent().getBackground());
    e.gc.fillRectangle(0, 0, size.x, size.y);
    e.gc.setBackground(lineColor());
    if ((sash.getStyle() & SWT.VERTICAL) != 0) {
      e.gc.fillRectangle(size.x / 2, 0, 1, size.y);
    }
    else {
      e.gc.fillRectangle(0, size.y / 2, size.x, 1);
    }
  }

  /** Draws a line on the left edge of {@code composite}, which must have a {@link GridLayout}. */
  public static void leftLine(Composite composite) {
    ((GridLayout)composite.getLayout()).marginLeft += 1;
    composite.addListener(SWT.Paint, e -> {
      e.gc.setBackground(lineColor());
      e.gc.fillRectangle(0, 0, 1, composite.getSize().y);
    });
  }
}
