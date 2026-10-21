package gitbench.ui.log;

import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.assertj.core.api.Assertions.assertThat;

class GraphColorsTest {
  /** The formula of DefaultColorGenerator.calcColor in IntelliJ, with its default saturation 0.4 and brightness 0.65. */
  private static int intellij(int colorId) {
    int r = colorId * 200 + 30;
    int g = colorId * 130 + 50;
    int b = colorId * 90 + 100;
    float[] hsb = Color.RGBtoHSB(Math.abs(r % 100) + 70, Math.abs(g % 100) + 70, Math.abs(b % 100) + 70, null);
    return Color.HSBtoRGB(hsb[0], 0.4f, 0.65f) & 0xFFFFFF;
  }

  @Test
  void matchesIntelliJFormulaForSeveralIds() {
    for (int id : new int[]{1, 7, -5, "main".hashCode(), "with-terminal".hashCode()}) {
      assertThat(GraphColors.rgb(id, false)).as("id %d", id).isEqualTo(intellij(id));
      assertThat(GraphColors.rgb(id, true)).as("id %d dark", id).isEqualTo(intellij(id));
    }
  }

  @Test
  void defaultColorIsBlackInLightAndForegroundInDark() {
    assertThat(GraphColors.rgb(0, false)).isEqualTo(0x000000);
    assertThat(GraphColors.rgb(0, true)).isEqualTo(0xDFE1E5);
  }

  @Test
  void colorIsStablePerId() {
    assertThat(GraphColors.rgb(42, false)).isEqualTo(GraphColors.rgb(42, false));
  }
}
