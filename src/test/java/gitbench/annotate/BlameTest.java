package gitbench.annotate;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BlameTest {
  private static final String A = "1111111111111111111111111111111111111111";

  @Test
  void shiftedMarksEditedLinesUncommittedAndMovesTheRest() {
    BlameCommit commit = new BlameCommit(A, "Ann", "a@x", Instant.EPOCH, "s", "f.c", null, null);
    Blame blame = new Blame(List.of(A, A, A, A), Map.of(A, commit));

    // Line 1 becomes three lines.
    Blame shifted = blame.shifted(1, 1, 3);

    assertThat(shifted.lineHashes()).hasSize(6);
    assertThat(shifted.commitAt(0)).isPresent();
    assertThat(shifted.commitAt(1)).isEmpty();
    assertThat(shifted.commitAt(2)).isEmpty();
    assertThat(shifted.commitAt(3)).isEmpty();
    assertThat(shifted.commitAt(4)).isPresent();
    assertThat(shifted.commitAt(5)).isPresent();
  }

  @Test
  void shiftedDropsDeletedLines() {
    BlameCommit commit = new BlameCommit(A, "Ann", "a@x", Instant.EPOCH, "s", "f.c", null, null);
    Blame blame = new Blame(List.of(A, A, A, A), Map.of(A, commit));

    // Lines 1..2 are joined into line 1.
    assertThat(blame.shifted(1, 2, 1).lineHashes()).hasSize(3);
  }
}
