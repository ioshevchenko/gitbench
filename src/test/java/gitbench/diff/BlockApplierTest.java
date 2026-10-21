package gitbench.diff;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BlockApplierTest {
  private static List<DiffChange> changes(DiffText left, DiffText right) {
    return DiffModel.compute(left, right, IgnorePolicy.DEFAULT, HighlightPolicy.BY_WORD);
  }

  @Test
  void replaceLeftOntoRightRestoresLeftLines() {
    DiffText left = DiffText.of("a\nold\nc\n");
    DiffText right = DiffText.of("a\nnew\nmore\nc\n");
    DiffChange change = changes(left, right).get(0);

    Edit edit = BlockApplier.replace(left, right, change, Side.LEFT);

    assertThat(edit.applyTo(right.text())).isEqualTo("a\nold\nc\n");
  }

  @Test
  void appendKeepsTargetLinesAndAddsSourceLines() {
    DiffText left = DiffText.of("a\nold\nc\n");
    DiffText right = DiffText.of("a\nnew\nc\n");
    DiffChange change = changes(left, right).get(0);

    Edit edit = BlockApplier.append(left, right, change, Side.LEFT);

    assertThat(edit.applyTo(right.text())).isEqualTo("a\nnew\nold\nc\n");
  }

  @Test
  void replaceInsertedBlockDeletesItsLines() {
    DiffText left = DiffText.of("a\nc\n");
    DiffText right = DiffText.of("a\nb\nc\n");
    DiffChange change = changes(left, right).get(0);

    assertThat(BlockApplier.replace(left, right, change, Side.LEFT).applyTo(right.text())).isEqualTo("a\nc\n");
  }

  @Test
  void replaceDeletedBlockInsertsSourceLines() {
    DiffText left = DiffText.of("a\nb\nc\n");
    DiffText right = DiffText.of("a\nc\n");
    DiffChange change = changes(left, right).get(0);

    assertThat(BlockApplier.replace(left, right, change, Side.LEFT).applyTo(right.text())).isEqualTo("a\nb\nc\n");
  }

  @Test
  void replaceAtEndOfFileWithoutTrailingNewline() {
    DiffText left = DiffText.of("a\nb");
    DiffText right = DiffText.of("a\nb\nc");
    DiffChange change = changes(left, right).get(0);

    assertThat(BlockApplier.replace(left, right, change, Side.LEFT).applyTo(right.text())).isEqualTo("a\nb");
    assertThat(BlockApplier.replace(right, left, change, Side.RIGHT).applyTo(left.text())).isEqualTo("a\nb\nc");
  }

  @Test
  void editAddressesLinesAndColumns() {
    DiffText left = DiffText.of("a\nold\nc\n");
    DiffText right = DiffText.of("a\nnew\nc\n");

    Edit edit = BlockApplier.replace(left, right, changes(left, right).get(0), Side.LEFT);

    assertThat(edit).isEqualTo(new Edit(1, 0, 1, 3, "old"));
  }
}
