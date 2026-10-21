package gitbench.ui;

import gitbench.git.ChangeKind;
import org.eclipse.swt.graphics.Color;

/** File status colors of the IntelliJ New UI light and dark color schemes. */
public final class StatusColors {
  private static final Color MODIFIED_LIGHT = new Color(0x00, 0x33, 0xB3);
  private static final Color MODIFIED_DARK = new Color(0x70, 0xAE, 0xFF);
  private static final Color ADDED_LIGHT = new Color(0x06, 0x7D, 0x17);
  private static final Color ADDED_DARK = new Color(0x73, 0xBD, 0x79);
  private static final Color DELETED_LIGHT = new Color(0x6C, 0x70, 0x7E);
  private static final Color DELETED_DARK = new Color(0x6F, 0x73, 0x7A);
  private static final Color UNVERSIONED_LIGHT = new Color(0xB2, 0x32, 0x47);
  private static final Color UNVERSIONED_DARK = new Color(0xE8, 0x8F, 0x89);
  private static final Color CONFLICT_LIGHT = new Color(0xDE, 0x1B, 0x2E);
  private static final Color CONFLICT_DARK = new Color(0xDE, 0x6A, 0x66);

  private StatusColors() {
  }

  public static Color foreground(ChangeKind kind) {
    boolean dark = Theme.isDark();
    return switch (kind) {
      case MODIFIED, RENAMED -> dark ? MODIFIED_DARK : MODIFIED_LIGHT;
      case ADDED -> dark ? ADDED_DARK : ADDED_LIGHT;
      case DELETED -> dark ? DELETED_DARK : DELETED_LIGHT;
      case UNVERSIONED -> dark ? UNVERSIONED_DARK : UNVERSIONED_LIGHT;
      case CONFLICT -> dark ? CONFLICT_DARK : CONFLICT_LIGHT;
    };
  }
}
