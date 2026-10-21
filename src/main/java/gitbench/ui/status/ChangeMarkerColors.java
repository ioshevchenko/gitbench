package gitbench.ui.status;

import gitbench.diff.BlockKind;
import org.eclipse.swt.graphics.Color;

/**
 * The IntelliJ New UI colors of the change markers: ADDED_LINES_COLOR, MODIFIED_LINES_COLOR, DELETED_LINES_COLOR.
 * Colors made without a device need no dispose.
 */
public final class ChangeMarkerColors {
  private static final Color LIGHT_ADDED = new Color(0x7F, 0xC7, 0x84);
  private static final Color LIGHT_MODIFIED = new Color(0x88, 0xAD, 0xF7);
  private static final Color LIGHT_DELETED = new Color(0x76, 0x7A, 0x8A);
  private static final Color DARK_ADDED = new Color(0x54, 0x91, 0x59);
  private static final Color DARK_MODIFIED = new Color(0x37, 0x5F, 0xAD);
  private static final Color DARK_DELETED = new Color(0x86, 0x8A, 0x91);

  private ChangeMarkerColors() {
  }

  public static Color color(BlockKind kind, boolean dark) {
    return switch (kind) {
      case INSERTED -> dark ? DARK_ADDED : LIGHT_ADDED;
      case MODIFIED -> dark ? DARK_MODIFIED : LIGHT_MODIFIED;
      case DELETED -> dark ? DARK_DELETED : LIGHT_DELETED;
    };
  }
}
