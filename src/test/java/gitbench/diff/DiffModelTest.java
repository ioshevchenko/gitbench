package gitbench.diff;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DiffModelTest {
  private static List<DiffChange> compare(String left, String right, IgnorePolicy ignore, HighlightPolicy highlight) {
    return DiffModel.compute(DiffText.of(left), DiffText.of(right), ignore, highlight);
  }

  private static List<DiffChange> compare(String left, String right) {
    return compare(left, right, IgnorePolicy.DEFAULT, HighlightPolicy.BY_WORD);
  }

  @Test
  void identicalTextsHaveNoChanges() {
    assertThat(compare("a\nb\n", "a\nb\n")).isEmpty();
  }

  @Test
  void insertedLineIsInserted() {
    List<DiffChange> changes = compare("a\nb\n", "a\nx\nb\n");

    assertThat(changes).hasSize(1);
    DiffChange change = changes.get(0);
    assertThat(change.kind()).isEqualTo(BlockKind.INSERTED);
    assertThat(List.of(change.startLine1(), change.endLine1(), change.startLine2(), change.endLine2())).containsExactly(1, 1, 1, 2);
  }

  @Test
  void deletedLineIsDeleted() {
    List<DiffChange> changes = compare("a\nx\nb\n", "a\nb\n");

    assertThat(changes).singleElement().extracting(DiffChange::kind).isEqualTo(BlockKind.DELETED);
  }

  @Test
  void changedWordIsHighlightedInsideLine() {
    List<DiffChange> changes = compare("int a = 1;", "int a = 2;");

    assertThat(changes).hasSize(1);
    DiffChange change = changes.get(0);
    assertThat(change.kind()).isEqualTo(BlockKind.MODIFIED);
    assertThat(change.hasInner()).isTrue();
    assertThat(change.inner1()).containsExactly(new InnerRange(0, 8, 9));
    assertThat(change.inner2()).containsExactly(new InnerRange(0, 8, 9));
  }

  @Test
  void innerRangeOnSecondLineOfBlockHasThatLine() {
    List<DiffChange> changes = compare("x\nint a = 1;\nint b = 1;\ny\n", "x\nint a = 1;\nint b = 2;\ny\n");

    assertThat(changes).singleElement().satisfies(change -> {
      assertThat(change.inner1()).containsExactly(new InnerRange(2, 8, 9));
      assertThat(change.inner2()).containsExactly(new InnerRange(2, 8, 9));
    });
  }

  @Test
  void byLineHasNoInnerRanges() {
    List<DiffChange> changes = compare("int a = 1;", "int a = 2;", IgnorePolicy.DEFAULT, HighlightPolicy.BY_LINE);

    assertThat(changes).singleElement().satisfies(change -> {
      assertThat(change.hasInner()).isFalse();
      assertThat(change.inner1()).isEmpty();
      assertThat(change.inner2()).isEmpty();
    });
  }

  @Test
  void ignoreWhitespacesHidesIndentChange() {
    assertThat(compare("  a\n", "\ta\n", IgnorePolicy.IGNORE_WHITESPACES, HighlightPolicy.BY_WORD)).isEmpty();
    assertThat(compare("  a\n", "\ta\n", IgnorePolicy.DEFAULT, HighlightPolicy.BY_WORD)).hasSize(1);
  }

  @Test
  void crlfAndLfTextsWithSameLinesHaveNoChanges() {
    assertThat(compare("a\r\nb\r\n", "a\nb\n")).isEmpty();
  }

  @Test
  void doNotHighlightReturnsNothing() {
    assertThat(compare("a\n", "b\n", IgnorePolicy.DEFAULT, HighlightPolicy.DO_NOT_HIGHLIGHT)).isEmpty();
  }

  @Test
  void nextAndPreviousFindBlocksAroundLine() {
    List<DiffChange> changes = compare("a\nb\nc\nd\ne\nf\n", "a\nB\nc\nd\nE\nf\n");
    assertThat(changes).hasSize(2);

    assertThat(DiffModel.next(changes, 0, Side.LEFT)).isEqualTo(0);
    assertThat(DiffModel.next(changes, 1, Side.LEFT)).isEqualTo(1);
    assertThat(DiffModel.next(changes, 4, Side.LEFT)).isEqualTo(-1);
    assertThat(DiffModel.previous(changes, 5, Side.RIGHT)).isEqualTo(1);
    assertThat(DiffModel.previous(changes, 4, Side.RIGHT)).isEqualTo(0);
    assertThat(DiffModel.previous(changes, 1, Side.RIGHT)).isEqualTo(-1);
  }

  @Test
  void diffTextNormalizesLineSeparators() {
    DiffText text = DiffText.of("a\r\nb\rc");

    assertThat(text.text()).isEqualTo("a\nb\nc");
    assertThat(text.lineCount()).isEqualTo(3);
    assertThat(text.lineStart(1)).isEqualTo(2);
    assertThat(text.lineEnd(1)).isEqualTo(3);
  }

  @Test
  void policyLabelsMatchIntellij() {
    assertThat(IgnorePolicy.IGNORE_WHITESPACES_CHUNKS.label()).isEqualTo("Ignore whitespaces and empty lines");
    assertThat(HighlightPolicy.BY_WORD_SPLIT.label()).isEqualTo("Split changes");
  }
}
