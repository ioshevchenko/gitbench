package gitbench.ui;

import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Control;

import java.util.function.Consumer;

/** Tells whether the active Eclipse theme is dark, and keeps widgets in the colors of the theme. */
public final class Theme {
  /** The part of the foreground in a secondary text color; the rest is the background. */
  private static final double SECONDARY_TEXT_SHARE = 0.6;

  private Theme() {
  }

  public static boolean isDark() {
    String themeId = InstanceScope.INSTANCE.getNode("org.eclipse.e4.ui.css.swt.theme").get("themeid", "");
    return themeId.toLowerCase(java.util.Locale.ROOT).contains("dark");
  }

  /**
   * A gray for secondary text that stays readable in both themes, like the IntelliJ "context help" color. The system
   * "disabled" color is made for light widgets and almost disappears on a dark background.
   */
  public static Color secondaryText(Color foreground, Color background) {
    RGB fg = foreground.getRGB();
    RGB bg = background.getRGB();
    return new Color((int)Math.round(fg.red * SECONDARY_TEXT_SHARE + bg.red * (1 - SECONDARY_TEXT_SHARE)),
                     (int)Math.round(fg.green * SECONDARY_TEXT_SHARE + bg.green * (1 - SECONDARY_TEXT_SHARE)),
                     (int)Math.round(fg.blue * SECONDARY_TEXT_SHARE + bg.blue * (1 - SECONDARY_TEXT_SHARE)));
  }

  /**
   * Gives {@code apply} the background of {@code source} each time it changes. The Eclipse theme colors a widget
   * when it first shows, after the plugin made it, so the color is copied at paint time and not at creation.
   */
  public static void followBackground(Control source, Consumer<Color> apply) {
    Color[] last = {null};
    source.addListener(SWT.Paint, e -> {
      Color background = source.getBackground();
      if (!background.equals(last[0])) {
        last[0] = background;
        apply.accept(background);
      }
    });
  }
}
