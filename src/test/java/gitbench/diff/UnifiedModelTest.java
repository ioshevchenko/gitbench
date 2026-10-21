package gitbench.diff;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UnifiedModelTest {
  private static UnifiedModel build(String left, String right) {
    DiffText l = DiffText.of(left);
    DiffText r = DiffText.of(right);
    return UnifiedModel.build(l, r, DiffModel.compute(l, r, IgnorePolicy.DEFAULT, HighlightPolicy.BY_WORD));
  }

  @Test
  void contextLinesKeepBothNumbers() {
    UnifiedModel model = build("a\nb\nc", "a\nx\nb\nc");

    assertThat(model.text()).isEqualTo("a\nx\nb\nc");
    assertThat(model.lines()).containsExactly(
      new UnifiedLine(UnifiedLine.Kind.CONTEXT, 0, 0, -1),
      new UnifiedLine(UnifiedLine.Kind.INSERTED, -1, 1, 0),
      new UnifiedLine(UnifiedLine.Kind.CONTEXT, 1, 2, -1),
      new UnifiedLine(UnifiedLine.Kind.CONTEXT, 2, 3, -1));
  }

  @Test
  void modifiedBlockShowsDeletedThenInserted() {
    UnifiedModel model = build("a\nold\nc", "a\nnew\nc");

    assertThat(model.text()).isEqualTo("a\nold\nnew\nc");
    assertThat(model.lines().get(1)).isEqualTo(new UnifiedLine(UnifiedLine.Kind.DELETED, 1, -1, 0));
    assertThat(model.lines().get(2)).isEqualTo(new UnifiedLine(UnifiedLine.Kind.INSERTED, -1, 1, 0));
    assertThat(model.lineOf(0)).isEqualTo(1);
    assertThat(model.unifiedLineOf(Side.LEFT, 2)).isEqualTo(3);
    assertThat(model.unifiedLineOf(Side.RIGHT, 1)).isEqualTo(2);
  }

  @Test
  void innerRangesMoveToUnifiedCoordinates() {
    UnifiedModel model = build("x\nint a = 1;\n", "x\nint a = 2;\n");

    assertThat(model.innerRanges()).containsExactly(new InnerRange(1, 8, 9), new InnerRange(2, 8, 9));
  }
}
