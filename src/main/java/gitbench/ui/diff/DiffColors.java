package gitbench.ui.diff;

import gitbench.diff.BlockKind;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.RGB;

/**
 * Diff colors of the IntelliJ New UI schemes Light and Dark: DIFF_INSERTED, DIFF_MODIFIED, DIFF_DELETED and
 * DIFF_SEPARATORS_BACKGROUND. Colors made without a device need no dispose.
 */
final class DiffColors {
  private static final Color LIGHT_INSERTED = new Color(0xBE, 0xE6, 0xBE);
  private static final Color LIGHT_MODIFIED = new Color(0xC2, 0xD8, 0xF2);
  private static final Color LIGHT_DELETED = new Color(0xD6, 0xD6, 0xD6);
  private static final Color DARK_INSERTED = new Color(0x29, 0x44, 0x36);
  private static final Color DARK_MODIFIED = new Color(0x38, 0x55, 0x70);
  private static final Color DARK_DELETED = new Color(0x48, 0x4A, 0x4A);
  private static final Color LIGHT_SEPARATORS = new Color(0xE4, 0xE6, 0xEB);
  private static final Color DARK_SEPARATORS = new Color(0x2B, 0x2D, 0x30);
  /** TextDiffTypeFactory.MIDDLE_COLOR_FACTOR: the part of the editor background in an "ignored" color. */
  private static final double MIDDLE_COLOR_FACTOR = 0.6;

  private DiffColors() {
  }

  /** The full color of a block: words, the divider, and lines of a block without word highlighting. */
  static Color color(BlockKind kind, boolean dark) {
    return switch (kind) {
      case INSERTED -> dark ? DARK_INSERTED : LIGHT_INSERTED;
      case MODIFIED -> dark ? DARK_MODIFIED : LIGHT_MODIFIED;
      case DELETED -> dark ? DARK_DELETED : LIGHT_DELETED;
    };
  }

  /** The line color of a block with word highlighting, as TextDiffType.getIgnoredColor. */
  static Color ignoredColor(BlockKind kind, boolean dark, Color editorBackground) {
    return mix(color(kind, dark), editorBackground, MIDDLE_COLOR_FACTOR);
  }

  static Color separators(boolean dark) {
    return dark ? DARK_SEPARATORS : LIGHT_SEPARATORS;
  }

  /** ColorUtil.mix: {@code c1 * (1 - balance) + c2 * balance}. */
  private static Color mix(Color c1, Color c2, double balance) {
    RGB a = c1.getRGB();
    RGB b = c2.getRGB();
    return new Color((int)Math.round(a.red * (1 - balance) + b.red * balance),
                     (int)Math.round(a.green * (1 - balance) + b.green * balance),
                     (int)Math.round(a.blue * (1 - balance) + b.blue * balance));
  }
}
