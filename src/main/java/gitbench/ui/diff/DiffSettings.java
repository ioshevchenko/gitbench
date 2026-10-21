package gitbench.ui.diff;

import gitbench.diff.HighlightPolicy;
import gitbench.diff.IgnorePolicy;
import gitbench.ui.Activator;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.osgi.service.prefs.BackingStoreException;

/**
 * The diff settings that all diff tabs share, as IntelliJ TextDiffSettings: the viewer, the Ignore policy and the
 * Highlight policy. A new tab opens with the last values.
 */
final class DiffSettings {
  enum Viewer {
    SIDE_BY_SIDE("Side-by-side viewer"), UNIFIED("Unified viewer");

    private final String label;

    Viewer(String label) {
      this.label = label;
    }

    String label() {
      return label;
    }
  }

  private static final String VIEWER = "diff.viewer";
  private static final String IGNORE = "diff.ignore";
  private static final String HIGHLIGHT = "diff.highlight";

  private DiffSettings() {
  }

  static Viewer viewer() {
    return read(VIEWER, Viewer.class, Viewer.SIDE_BY_SIDE);
  }

  static IgnorePolicy ignore() {
    return read(IGNORE, IgnorePolicy.class, IgnorePolicy.DEFAULT);
  }

  static HighlightPolicy highlight() {
    return read(HIGHLIGHT, HighlightPolicy.class, HighlightPolicy.BY_WORD);
  }

  static void setViewer(Viewer value) {
    write(VIEWER, value);
  }

  static void setIgnore(IgnorePolicy value) {
    write(IGNORE, value);
  }

  static void setHighlight(HighlightPolicy value) {
    write(HIGHLIGHT, value);
  }

  private static <E extends Enum<E>> E read(String key, Class<E> type, E defaultValue) {
    try {
      return Enum.valueOf(type, node().get(key, defaultValue.name()));
    }
    catch (IllegalArgumentException e) {
      return defaultValue;
    }
  }

  private static void write(String key, Enum<?> value) {
    IEclipsePreferences node = node();
    node.put(key, value.name());
    try {
      node.flush();
    }
    catch (BackingStoreException e) {
      Activator.getDefault().getLog().error("Cannot save the diff settings", e);
    }
  }

  private static IEclipsePreferences node() {
    return InstanceScope.INSTANCE.getNode(Activator.PLUGIN_ID);
  }
}
