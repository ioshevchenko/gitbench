package gitbench.diff;

import com.intellij.diff.comparison.ComparisonManagerImpl;
import com.intellij.diff.fragments.DiffFragment;
import com.intellij.diff.fragments.LineFragment;
import com.intellij.diff.tools.util.text.LineOffsetsUtil;
import com.intellij.openapi.progress.DumbProgressIndicator;

import java.util.ArrayList;
import java.util.List;

/** Compares two texts the way the IntelliJ two-side diff does, and finds blocks for navigation. */
public final class DiffModel {
  private DiffModel() {
  }

  /**
   * Repeats TwosideTextDiffProviderBase.compare and SimpleTextDiffProvider.compareRange: line fragments with inner
   * fragments, then squash and trim by the policies.
   *
   * @throws com.intellij.diff.comparison.DiffTooBigException when the texts are too different to compare
   */
  public static List<DiffChange> compute(DiffText left, DiffText right, IgnorePolicy ignore, HighlightPolicy highlight) {
    if (!highlight.shouldCompare()) {
      return List.of();
    }
    ComparisonManagerImpl manager = ComparisonManagerImpl.getInstanceImpl();
    String text1 = left.text();
    String text2 = right.text();
    List<LineFragment> fragments = manager.compareLinesInner(text1, text2, LineOffsetsUtil.create(text1), LineOffsetsUtil.create(text2),
                                                             ignore.comparisonPolicy(), highlight.fragmentsPolicy(),
                                                             DumbProgressIndicator.INSTANCE);
    fragments = manager.processBlocks(fragments, text1, text2, ignore.comparisonPolicy(), highlight.shouldSquash(), ignore.shouldTrimChunks());
    List<DiffChange> changes = new ArrayList<>(fragments.size());
    for (LineFragment fragment : fragments) {
      changes.add(toChange(fragment, left, right));
    }
    return changes;
  }

  private static DiffChange toChange(LineFragment fragment, DiffText left, DiffText right) {
    BlockKind kind = fragment.getStartLine1() == fragment.getEndLine1() ? BlockKind.INSERTED
                     : fragment.getStartLine2() == fragment.getEndLine2() ? BlockKind.DELETED
                       : BlockKind.MODIFIED;
    List<InnerRange> inner1 = new ArrayList<>();
    List<InnerRange> inner2 = new ArrayList<>();
    List<DiffFragment> innerFragments = fragment.getInnerFragments();
    if (innerFragments != null) {
      for (DiffFragment inner : innerFragments) {
        addRanges(inner1, left, fragment.getStartOffset1() + inner.getStartOffset1(), fragment.getStartOffset1() + inner.getEndOffset1());
        addRanges(inner2, right, fragment.getStartOffset2() + inner.getStartOffset2(), fragment.getStartOffset2() + inner.getEndOffset2());
      }
    }
    return new DiffChange(fragment.getStartLine1(), fragment.getEndLine1(), fragment.getStartLine2(), fragment.getEndLine2(), kind,
                          inner1, inner2);
  }

  /** Splits a text range into one range per line. An empty range stays one empty range: it marks an insertion point. */
  private static void addRanges(List<InnerRange> ranges, DiffText text, int start, int end) {
    int startLine = text.lineOf(start);
    if (start == end) {
      ranges.add(new InnerRange(startLine, start - text.lineStart(startLine), start - text.lineStart(startLine)));
      return;
    }
    int endLine = text.lineOf(end);
    for (int line = startLine; line <= endLine; line++) {
      int lineStart = text.lineStart(line);
      int from = Math.max(start, lineStart);
      int to = Math.min(end, text.lineEnd(line));
      if (to > from) {
        ranges.add(new InnerRange(line, from - lineStart, to - lineStart));
      }
    }
  }

  /** The index of the block that Next Difference goes to, or -1. Repeats PrevNextDifferenceIterableBase.goNext. */
  public static int next(List<DiffChange> changes, int line, Side side) {
    for (int i = 0; i < changes.size(); i++) {
      if (changes.get(i).startLine(side) > line) {
        return i;
      }
    }
    return -1;
  }

  /** The index of the block that Previous Difference goes to, or -1. Repeats PrevNextDifferenceIterableBase.goPrev. */
  public static int previous(List<DiffChange> changes, int line, Side side) {
    if (changes.isEmpty() || line == 0) {
      return -1;
    }
    DiffChange first = changes.get(0);
    if (first.endLine(side) > line || first.startLine(side) >= line) {
      return -1;
    }
    for (int i = 0; i < changes.size(); i++) {
      DiffChange next = i < changes.size() - 1 ? changes.get(i + 1) : null;
      if (next == null || next.endLine(side) > line || next.startLine(side) >= line) {
        return i;
      }
    }
    return -1;
  }
}
