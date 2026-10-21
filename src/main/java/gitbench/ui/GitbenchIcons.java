package gitbench.ui;

import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.resource.ImageRegistry;
import org.eclipse.swt.graphics.Image;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Locale;

/** Icons from the IntelliJ New UI set, as PNG files for the light and the dark theme. */
public final class GitbenchIcons {
  public enum Icon {
    REFRESH, ROLLBACK, VIEW_OPTIONS, EXPAND_ALL, COLLAPSE_ALL, HISTORY, SETTINGS, COMMIT_TOOL_WINDOW, PUSH, FOLDER,
    UNVERSIONED_FILES,
    VCS, FETCH, SEARCH, REGEX, MATCH_CASE, CURRENT_BRANCH_LABEL, BRANCH_LABEL, CURRENT_BRANCH_FAVORITE_LABEL, SORT, CLOSE,
    CHEVRON_DOWN,
    ADD, DELETE, LOCATE, UPDATE, DIFF, STAR, INCOMING, OUTGOING,
    DIFF_ARROW_LEFT, DIFF_ARROW_RIGHT, DIFF_ARROW_LEFT_DOWN, DIFF_ARROW_RIGHT_DOWN, PREVIOUS_CHANGE, NEXT_CHANGE, PREVIOUS_FILE,
    NEXT_FILE, SIDE_BY_SIDE, UNIFIED, LOCKED,
    APPLY_NON_CONFLICTS_LEFT, APPLY_NON_CONFLICTS, APPLY_NON_CONFLICTS_RIGHT, MAGIC_RESOLVE_TOOLBAR, MAGIC_RESOLVE, DIFF_REMOVE;

    /** The file name without the extension: {@code VIEW_OPTIONS} → {@code viewOptions}. */
    String fileName() {
      String[] words = name().toLowerCase(Locale.ROOT).split("_");
      StringBuilder name = new StringBuilder(words[0]);
      for (int i = 1; i < words.length; i++) {
        name.append(Character.toUpperCase(words[i].charAt(0))).append(words[i].substring(1));
      }
      return name.toString();
    }
  }

  private GitbenchIcons() {
  }

  public static ImageDescriptor descriptor(Icon icon) {
    String theme = Theme.isDark() ? "dark" : "light";
    try {
      return ImageDescriptor.createFromURL(
        new URL("platform:/plugin/" + Activator.PLUGIN_ID + "/icons/png/" + theme + "/" + icon.fileName() + ".png"));
    }
    catch (MalformedURLException e) {
      return ImageDescriptor.getMissingImageDescriptor();
    }
  }

  public static Image get(Icon icon) {
    ImageRegistry registry = Activator.getDefault().getImageRegistry();
    String key = (Theme.isDark() ? "dark/" : "light/") + icon.name();
    Image image = registry.get(key);
    if (image == null) {
      registry.put(key, descriptor(icon));
      image = registry.get(key);
    }
    return image;
  }
}
