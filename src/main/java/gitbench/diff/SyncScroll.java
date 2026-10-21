package gitbench.diff;

import java.util.List;

/** Maps a line of one side to the matching line of the other side, as IntelliJ BaseSyncScrollable. */
public final class SyncScroll {
  private SyncScroll() {
  }

  public static int transfer(List<DiffChange> changes, Side from, int line) {
    // The block boundaries form pairs (left, right); find the pair range that holds the line, as ScrollHelper.process.
    int master1 = 0;
    int slave1 = 0;
    int master2 = 0;
    int slave2 = 0;
    boolean found = false;
    for (DiffChange change : changes) {
      for (int boundary = 0; boundary < 2 && !found; boundary++) {
        master1 = master2;
        slave1 = slave2;
        master2 = boundary == 0 ? change.startLine(from) : change.endLine(from);
        slave2 = boundary == 0 ? change.startLine(from.other()) : change.endLine(from.other());
        found = line <= master2;
      }
      if (found) {
        break;
      }
    }
    if (!found) {
      // After the last block the sides go in step up to the end of the texts.
      return line - master2 + slave2;
    }
    return transferLine(line, master1, master2, slave1, slave2);
  }

  private static int transferLine(int line, int start1, int end1, int start2, int end2) {
    if (start1 == line) {
      return start2;
    }
    if (end1 == line) {
      return end2;
    }
    if (end1 < line) {
      return line - end1 + end2;
    }
    return Math.min(start2 + (line - start1), end2);
  }
}
