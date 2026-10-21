package gitbench.status;

import com.intellij.diff.comparison.DiffTooBigException;
import gitbench.diff.BlockApplier;
import gitbench.diff.BlockKind;
import gitbench.diff.DiffChange;
import gitbench.diff.DiffModel;
import gitbench.diff.DiffText;
import gitbench.diff.Edit;
import gitbench.diff.HighlightPolicy;
import gitbench.diff.IgnorePolicy;
import gitbench.diff.Side;

import java.util.List;

/**
 * The changed lines of a document against its HEAD text, as the IntelliJ LineStatusTracker. A range is a
 * {@link DiffChange}: the left side is the HEAD text, the right side is the document.
 */
public final class LineStatus {
  private LineStatus() {
  }

  /** The changed ranges; texts too different to compare give one range over the whole text. */
  public static List<DiffChange> compute(String base, String current) {
    DiffText left = DiffText.of(base);
    DiffText right = DiffText.of(current);
    try {
      return DiffModel.compute(left, right, IgnorePolicy.DEFAULT, HighlightPolicy.BY_LINE);
    }
    catch (DiffTooBigException e) {
      return List.of(new DiffChange(0, left.lineCount(), 0, right.lineCount(), BlockKind.MODIFIED, List.of(), List.of()));
    }
  }

  /** The index of the first range that starts below {@code line} of the document, or -1. */
  public static int next(List<DiffChange> ranges, int line) {
    for (int i = 0; i < ranges.size(); i++) {
      if (ranges.get(i).startLine2() > line) {
        return i;
      }
    }
    return -1;
  }

  /** The index of the last range that starts above {@code line} of the document, or -1. */
  public static int previous(List<DiffChange> ranges, int line) {
    for (int i = ranges.size() - 1; i >= 0; i--) {
      if (ranges.get(i).startLine2() < line) {
        return i;
      }
    }
    return -1;
  }

  /**
   * The ranges right after an edit, before the next comparison, as the IntelliJ DocumentTracker keeps them: the old
   * lines {@code start..oldEnd} of the document became the lines {@code start..newEnd}. Ranges above stay, ranges below
   * move by the line delta, and ranges that touch the edited lines are dropped until the comparison brings them back.
   */
  public static List<DiffChange> shift(List<DiffChange> ranges, int start, int oldEnd, int newEnd) {
    int delta = newEnd - oldEnd;
    List<DiffChange> result = new java.util.ArrayList<>(ranges.size());
    for (DiffChange range : ranges) {
      int end = Math.max(range.endLine2(), range.startLine2() + 1);
      if (end <= start) {
        result.add(range);
      }
      else if (range.startLine2() > oldEnd) {
        result.add(new DiffChange(range.startLine1(), range.endLine1(), range.startLine2() + delta, range.endLine2() + delta,
                                  range.kind(), range.inner(Side.LEFT), List.of()));
      }
    }
    return result;
  }

  /** The edit of the document that puts the HEAD lines of the range back. */
  public static Edit rollback(String base, String current, DiffChange range) {
    return BlockApplier.replace(DiffText.of(base), DiffText.of(current), range, Side.LEFT);
  }
}
