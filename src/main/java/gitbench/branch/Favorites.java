package gitbench.branch;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * The favorite branches of one repository. As in IntelliJ, {@code main} and {@code master} are favorites until the
 * user changes the list. The storage is a pair of functions, so the Eclipse preference store plugs in from the UI.
 */
public final class Favorites {
  private static final String DEFAULTS = "main\nmaster";

  private final Function<String, String> get;
  private final BiConsumer<String, String> put;
  private final String key;

  public Favorites(Function<String, String> get, BiConsumer<String, String> put, Path repositoryRoot) {
    this.get = get;
    this.put = put;
    this.key = "favorites." + repositoryRoot.toString().replace('\\', '/');
  }

  public Set<String> all() {
    String stored = get.apply(key);
    String value = stored == null || stored.isEmpty() ? DEFAULTS : stored;
    Set<String> names = new LinkedHashSet<>(Arrays.asList(value.split("\n")));
    names.remove("");
    names.remove(EMPTY_MARK);
    return names;
  }

  public boolean contains(String name) {
    return all().contains(name);
  }

  public void toggle(String name) {
    Set<String> names = all();
    if (!names.remove(name)) {
      names.add(name);
    }
    put.accept(key, names.isEmpty() ? EMPTY_MARK : String.join("\n", names));
  }

  /** Stored when the user removed every favorite, so the defaults do not come back. Git forbids a branch named "-". */
  private static final String EMPTY_MARK = "-";
}
