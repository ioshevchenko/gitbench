package gitbench.annotate;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AnnotationColorsTest {
  private static BlameCommit commit(String hash, long time) {
    return new BlameCommit(hash, "A", "a@x", Instant.ofEpochSecond(time), "s", "f.c", null, null);
  }

  @Test
  void paletteHas21ColorsStartingAndEndingAtAnchors() {
    List<Integer> palette = AnnotationColors.palette(false);

    assertThat(palette).hasSize(21);
    assertThat(palette.get(0)).isEqualTo(0xA1BEF7);
    assertThat(palette.get(5)).isEqualTo(0xC9DAFB);
    assertThat(palette.get(20)).isEqualTo(0xF7F8FA);
    assertThat(AnnotationColors.palette(true).get(0)).isEqualTo(0x2F5194);
  }

  @Test
  void newestCommitGetsTheFirstColor() {
    Blame blame = new Blame(List.of("old", "new", "mid"),
                            Map.of("old", commit("old", 100), "new", commit("new", 300), "mid", commit("mid", 200)));

    Map<String, Integer> colors = AnnotationColors.byCommit(blame, false);

    List<Integer> palette = AnnotationColors.palette(false);
    assertThat(colors.get("new")).isEqualTo(palette.get(0));
    assertThat(colors.get("mid")).isEqualTo(palette.get(21 / 3));
    assertThat(colors.get("old")).isEqualTo(palette.get(21 * 2 / 3));
  }

  @Test
  void singleCommitHasNoColors() {
    Blame blame = new Blame(List.of("one", "one"), Map.of("one", commit("one", 100)));

    assertThat(AnnotationColors.byCommit(blame, false)).isEmpty();
  }
}
