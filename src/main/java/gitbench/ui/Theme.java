package gitbench.ui;

import org.eclipse.core.runtime.preferences.InstanceScope;

/** Tells whether the active Eclipse theme is dark. */
public final class Theme {
  private Theme() {
  }

  public static boolean isDark() {
    String themeId = InstanceScope.INSTANCE.getNode("org.eclipse.e4.ui.css.swt.theme").get("themeid", "");
    return themeId.toLowerCase(java.util.Locale.ROOT).contains("dark");
  }
}
