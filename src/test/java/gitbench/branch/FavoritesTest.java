package gitbench.branch;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FavoritesTest {
  private final Map<String, String> store = new HashMap<>();
  private final Path root = Path.of("D:/Projects/firmware");

  private Favorites favorites() {
    return new Favorites(store::get, store::put, root);
  }

  @Test
  void defaultFavoritesAreMainAndMaster() {
    assertThat(favorites().all()).containsExactlyInAnyOrder("main", "master");
  }

  @Test
  void toggleAddsAndRemoves() {
    favorites().toggle("dev");
    assertThat(favorites().contains("dev")).isTrue();
    favorites().toggle("main");
    assertThat(favorites().contains("main")).isFalse();
    favorites().toggle("dev");
    assertThat(favorites().all()).containsExactly("master");
  }

  @Test
  void storedPerRepository() {
    favorites().toggle("dev");
    assertThat(new Favorites(store::get, store::put, Path.of("D:/Projects/other")).contains("dev")).isFalse();
  }
}
