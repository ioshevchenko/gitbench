package gitbench.status;

import gitbench.diff.BlockKind;
import gitbench.diff.DiffChange;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LineStatusTest {
  @Test
  void identicalTextsHaveNoRanges() {
    assertThat(LineStatus.compute("a\nb\n", "a\nb\n")).isEmpty();
  }

  @Test
  void addedLinesAreInserted() {
    List<DiffChange> ranges = LineStatus.compute("a\nb\n", "a\nx\nb\n");

    assertThat(ranges).hasSize(1);
    DiffChange range = ranges.get(0);
    assertThat(range.kind()).isEqualTo(BlockKind.INSERTED);
    assertThat(range.startLine2()).isEqualTo(1);
    assertThat(range.endLine2()).isEqualTo(2);
    assertThat(range.startLine1()).isEqualTo(range.endLine1());
  }

  @Test
  void changedLineIsModified() {
    List<DiffChange> ranges = LineStatus.compute("a\nb\nc\n", "a\nB\nc\n");

    assertThat(ranges).singleElement().satisfies(r -> {
      assertThat(r.kind()).isEqualTo(BlockKind.MODIFIED);
      assertThat(r.startLine2()).isEqualTo(1);
      assertThat(r.endLine2()).isEqualTo(2);
    });
  }

  @Test
  void removedLinesAreDeleted() {
    List<DiffChange> ranges = LineStatus.compute("a\nb\nc\n", "a\nc\n");

    assertThat(ranges).singleElement().satisfies(r -> {
      assertThat(r.kind()).isEqualTo(BlockKind.DELETED);
      assertThat(r.startLine2()).isEqualTo(1);
      assertThat(r.endLine2()).isEqualTo(1);
      assertThat(r.startLine1()).isEqualTo(1);
      assertThat(r.endLine1()).isEqualTo(2);
    });
  }

  @Test
  void crlfDocumentAgainstLfHeadHasNoRanges() {
    assertThat(LineStatus.compute("a\nb\n", "a\r\nb\r\n")).isEmpty();
  }

  @Test
  void nextAndPreviousSkipTheCaretLine() {
    List<DiffChange> ranges = LineStatus.compute("0\n1\n2\n3\n4\n5\n6\n", "0\nX\n2\n3\nY\n5\n6\n");

    assertThat(ranges).hasSize(2);
    assertThat(LineStatus.next(ranges, 0)).isEqualTo(0);
    assertThat(LineStatus.next(ranges, 1)).isEqualTo(1);
    assertThat(LineStatus.next(ranges, 4)).isEqualTo(-1);
    assertThat(LineStatus.previous(ranges, 4)).isEqualTo(0);
    assertThat(LineStatus.previous(ranges, 5)).isEqualTo(1);
    assertThat(LineStatus.previous(ranges, 1)).isEqualTo(-1);
  }

  @Test
  void rollbackRestoresModifiedLines() {
    assertRollback("a\nb\nc\n", "a\nB\nB2\nc\n");
  }

  @Test
  void rollbackRestoresDeletedLines() {
    assertRollback("a\nb\nc\n", "a\nc\n");
  }

  @Test
  void rollbackRemovesInsertedLines() {
    assertRollback("a\nc\n", "a\nb\nc\n");
  }

  @Test
  void rollbackRestoresLastLineWithoutNewline() {
    assertRollback("a\nb", "a\nc");
  }

  private static DiffChange modified(int line) {
    return new DiffChange(line, line + 1, line, line + 1, BlockKind.MODIFIED, List.of(), List.of());
  }

  @Test
  void shiftMovesRangesBelowTheEdit() {
    // Enter on line 0: old lines 0..0 become new lines 0..1.
    List<DiffChange> shifted = LineStatus.shift(List.of(modified(4)), 0, 0, 1);

    assertThat(shifted).singleElement().satisfies(r -> {
      assertThat(r.startLine2()).isEqualTo(5);
      assertThat(r.endLine2()).isEqualTo(6);
      assertThat(r.startLine1()).isEqualTo(4);
    });
  }

  @Test
  void shiftKeepsRangesAboveTheEdit() {
    assertThat(LineStatus.shift(List.of(modified(1)), 3, 5, 3)).containsExactly(modified(1));
  }

  @Test
  void shiftDropsRangesTouchedByTheEdit() {
    assertThat(LineStatus.shift(List.of(modified(2), modified(7)), 2, 2, 2)).containsExactly(modified(7));
  }

  private static void assertRollback(String base, String current) {
    List<DiffChange> ranges = LineStatus.compute(base, current);
    assertThat(ranges).hasSize(1);

    String restored = LineStatus.rollback(base, current, ranges.get(0)).applyTo(current);

    assertThat(restored).isEqualTo(base);
  }
}
