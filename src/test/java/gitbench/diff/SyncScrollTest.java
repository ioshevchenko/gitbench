package gitbench.diff;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SyncScrollTest {
  @Test
  void transfersLinesAroundAndInsideBlock() {
    // Left lines 2..4 became right lines 2..3; one line was also inserted at left line 6.
    List<DiffChange> changes = List.of(
      new DiffChange(2, 5, 2, 4, BlockKind.MODIFIED, List.of(), List.of()),
      new DiffChange(6, 6, 5, 6, BlockKind.INSERTED, List.of(), List.of()));

    assertThat(SyncScroll.transfer(changes, Side.LEFT, 0)).isEqualTo(0);
    assertThat(SyncScroll.transfer(changes, Side.LEFT, 2)).isEqualTo(2);
    assertThat(SyncScroll.transfer(changes, Side.LEFT, 3)).isEqualTo(3);
    assertThat(SyncScroll.transfer(changes, Side.LEFT, 4)).isEqualTo(4);
    assertThat(SyncScroll.transfer(changes, Side.LEFT, 5)).isEqualTo(4);
    assertThat(SyncScroll.transfer(changes, Side.LEFT, 6)).isEqualTo(5);
    assertThat(SyncScroll.transfer(changes, Side.LEFT, 10)).isEqualTo(10);
    assertThat(SyncScroll.transfer(changes, Side.RIGHT, 6)).isEqualTo(6);
    assertThat(SyncScroll.transfer(changes, Side.RIGHT, 3)).isEqualTo(3);
  }

  @Test
  void noChangesKeepTheLine() {
    assertThat(SyncScroll.transfer(List.of(), Side.RIGHT, 7)).isEqualTo(7);
  }
}
