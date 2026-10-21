package gitbench.ui.log;

import java.awt.Color;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Colors of the graph lines: the formula of {@code DefaultColorGenerator} from IntelliJ. The color id comes from
 * the graph (the hash of a branch name or a fragment index). Id 0 is the default: black, or the text color in dark.
 * Plain Java on purpose, so the formula has a unit test without SWT.
 */
public final class GraphColors {
  private static final float SATURATION = 0.4f;
  private static final float BRIGHTNESS = 0.65f;
  private static final int DEFAULT_LIGHT = 0x000000;
  private static final int DEFAULT_DARK = 0xDFE1E5;

  private static final Map<Integer, Integer> CACHE = new ConcurrentHashMap<>();

  private GraphColors() {
  }

  /** Returns the color as {@code 0xRRGGBB}. */
  public static int rgb(int colorId, boolean dark) {
    if (colorId == 0) {
      return dark ? DEFAULT_DARK : DEFAULT_LIGHT;
    }
    return CACHE.computeIfAbsent(colorId, GraphColors::calc);
  }

  private static int calc(int colorId) {
    int r = colorId * 200 + 30;
    int g = colorId * 130 + 50;
    int b = colorId * 90 + 100;
    float[] hsb = Color.RGBtoHSB(rangeFix(r), rangeFix(g), rangeFix(b), null);
    return Color.HSBtoRGB(hsb[0], SATURATION, BRIGHTNESS) & 0xFFFFFF;
  }

  private static int rangeFix(int n) {
    return Math.abs(n % 100) + 70;
  }
}
