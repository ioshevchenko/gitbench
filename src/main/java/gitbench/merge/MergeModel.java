package gitbench.merge;

import com.intellij.diff.comparison.ByWord;
import com.intellij.diff.comparison.ComparisonManagerImpl;
import com.intellij.diff.comparison.ComparisonMergeUtil;
import com.intellij.diff.comparison.ComparisonPolicy;
import com.intellij.diff.fragments.DiffFragment;
import com.intellij.diff.fragments.MergeLineFragment;
import com.intellij.diff.util.MergeConflictType;
import com.intellij.diff.tools.util.text.LineOffsets;
import com.intellij.diff.tools.util.text.LineOffsetsUtil;
import com.intellij.diff.util.MergeConflictResolutionStrategy;
import com.intellij.diff.util.MergeRangeUtil;
import com.intellij.diff.util.ThreeSide;
import com.intellij.openapi.progress.DumbProgressIndicator;
import gitbench.diff.DiffChange;
import gitbench.diff.DiffModel;
import gitbench.diff.DiffText;
import gitbench.diff.IgnorePolicy;
import gitbench.diff.InnerRange;
import gitbench.diff.Side;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The blocks of a three-way merge and what the merge window does with them. Repeats the IntelliJ MergeConflictModel
 * (replace, ignore, resolve automatically) and MergeModelBase (the block lines in the Result follow its changes).
 */
public final class MergeModel {
  private final DiffText yours;
  private final DiffText base;
  private final DiffText theirs;
  private final ResultText result;
  private static final char NEW_LINE = 10;

  private final List<MergeChange> changes = new ArrayList<>();
  /** Counts the changes of the Result, for the cache of {@link #isRangeModified}. */
  private long resultVersion;

  private MergeModel(DiffText yours, DiffText base, DiffText theirs, ResultText result) {
    this.yours = yours;
    this.base = base;
    this.theirs = theirs;
    this.result = result;
  }

  /** Compares the three texts. The Result must hold the base text; it then changes only through the model or the user. */
  public static MergeModel create(DiffText yours, DiffText base, DiffText theirs, IgnorePolicy ignore, ResultText result) {
    MergeModel model = new MergeModel(yours, base, theirs, result);
    ComparisonPolicy policy = ignore.comparisonPolicy();
    List<CharSequence> sequences = List.of(yours.text(), base.text(), theirs.text());
    List<LineOffsets> offsets = sequences.stream().map(LineOffsetsUtil::create).toList();
    List<MergeLineFragment> fragments = ComparisonManagerImpl.getInstanceImpl()
      .mergeLines(yours.text(), base.text(), theirs.text(), policy, DumbProgressIndicator.INSTANCE);
    for (MergeLineFragment fragment : fragments) {
      model.changes.add(new MergeChange(model.changes.size(), fragment,
                                        MergeRangeUtil.getLineMergeType(fragment, sequences, offsets, policy), model));
    }
    result.attach(model);
    return model;
  }

  public List<MergeChange> changes() {
    return changes;
  }

  /** Unresolved blocks that are not conflicts, as MergeThreesideViewer.getChangesCount. */
  public int changesCount() {
    return (int)changes.stream().filter(c -> !c.isResolved() && !c.isConflict()).count();
  }

  public int conflictsCount() {
    return (int)changes.stream().filter(c -> !c.isResolved() && c.isConflict()).count();
  }

  public boolean isAllResolved() {
    return changes.stream().allMatch(MergeChange::isResolved);
  }

  /** The status text of the merge window, as DiffBundle merge.differences.status.text. */
  public static String statusText(int changesCount, int conflictsCount) {
    String changesText = changesCount == 0 ? "No changes" : changesCount == 1 ? "1 change" : changesCount + " changes";
    String conflictsText = conflictsCount == 0 ? "No conflicts" : conflictsCount == 1 ? "1 conflict" : conflictsCount + " conflicts";
    return changesText + ", " + conflictsText;
  }

  /** {@code >>} or {@code <<}: puts the side of the block into the Result, as MergeConflictModel.replaceChange. */
  public void replaceChange(MergeChange change, Side side) {
    replaceChange(change, side, false);
  }

  private void replaceChange(MergeChange change, Side side, boolean resolveChange) {
    if (change.isResolved(side)) {
      return;
    }
    if (!change.isChange(side)) {
      markResolved(change);
      return;
    }
    ThreeSide source = side == Side.LEFT ? ThreeSide.LEFT : ThreeSide.RIGHT;
    ThreeSide opposite = side == Side.LEFT ? ThreeSide.RIGHT : ThreeSide.LEFT;
    List<String> newContent = lines(side == Side.LEFT ? yours : theirs, change.startLine(source), change.endLine(source));
    if (change.isConflict()) {
      if (change.isOnesideAppliedConflict()) {
        appendChange(change, newContent);
      }
      else {
        replaceResult(change, newContent);
      }
      if (resolveChange || change.startLine(opposite) == change.endLine(opposite)) {
        markResolved(change);
      }
      else {
        change.markOnesideAppliedConflict();
        change.setResolved(side, true);
      }
    }
    else {
      replaceResult(change, newContent);
      markResolved(change);
    }
  }

  /** {@code x}: drops the side of the block without changing the Result, as MergeConflictModel.ignoreChange. */
  public void ignoreChange(MergeChange change, Side side) {
    if (!change.isConflict()) {
      markResolved(change);
    }
    else {
      change.setResolved(side, true);
    }
  }

  /** Apply non-conflicting changes: Left, Right, or All (BASE). */
  public void applyNonConflicting(ThreeSide side) {
    for (MergeChange change : changes) {
      if (!change.isConflict()) {
        resolveAutomatically(change, side);
      }
    }
  }

  /** The magic button of the toolbar: everything that can be resolved automatically, as applyResolvableConflictedChanges. */
  public void resolveSimpleConflicts() {
    for (MergeChange change : changes) {
      resolveAutomatically(change, ThreeSide.BASE);
    }
  }

  /** The magic button of one block. */
  public void resolveAutomatically(MergeChange change) {
    resolveAutomatically(change, ThreeSide.BASE);
  }

  /** Accept Left or Accept Right: the whole side becomes the Result. */
  public void acceptAll(Side side) {
    String text = (side == Side.LEFT ? yours : theirs).text();
    result.replace(0, result.text().length(), text);
    for (MergeChange change : changes) {
      markResolved(change);
    }
  }

  /** The first unresolved block below the line of the Result, or null. */
  public MergeChange next(int line) {
    List<MergeChange> unresolved = unresolved();
    int index = DiffModel.next(asRanges(unresolved), line, Side.LEFT);
    return index < 0 ? null : unresolved.get(index);
  }

  /** The unresolved block that Previous Difference goes to from the line of the Result, or null. */
  public MergeChange previous(int line) {
    List<MergeChange> unresolved = unresolved();
    int index = DiffModel.previous(asRanges(unresolved), line, Side.LEFT);
    return index < 0 ? null : unresolved.get(index);
  }

  /**
   * Moves the blocks before a change of the Result in lines {@code [line1, line2)} that adds {@code shift} lines,
   * as MergeModelBase.processDocumentChange with DiffUtil.updateRangeOnModification.
   */
  void beforeResultChange(int line1, int line2, int shift) {
    resultVersion++;
    for (MergeChange change : changes) {
      int[] range = updateRangeOnModification(change.resultStart, change.resultEnd, line1, line2, shift);
      change.resultStart = range[0];
      change.resultEnd = range[1];
      // MergeConflictModel.processDocumentChange: a deleted block that the user erased from the Result is done.
      if (change.resultStart == change.resultEnd && change.type().getType() == MergeConflictType.Type.DELETED && !change.isResolved()) {
        markResolved(change);
      }
    }
  }

  /** The state of the Result and of every block, for Undo of a block action. */
  public record Snapshot(String text, int[] starts, int[] ends, boolean[] resolvedLeft, boolean[] resolvedRight, boolean[] oneside) {
    /** True when both snapshots hold the same text and the same block states; an action that changes nothing is not undone. */
    public boolean sameState(Snapshot other) {
      return text.equals(other.text) && java.util.Arrays.equals(starts, other.starts) && java.util.Arrays.equals(ends, other.ends)
             && java.util.Arrays.equals(resolvedLeft, other.resolvedLeft) && java.util.Arrays.equals(resolvedRight, other.resolvedRight)
             && java.util.Arrays.equals(oneside, other.oneside);
    }
  }

  public Snapshot snapshot() {
    int count = changes.size();
    Snapshot snapshot = new Snapshot(result.text(), new int[count], new int[count], new boolean[count], new boolean[count], new boolean[count]);
    for (int i = 0; i < count; i++) {
      MergeChange change = changes.get(i);
      snapshot.starts()[i] = change.resultStart;
      snapshot.ends()[i] = change.resultEnd;
      snapshot.resolvedLeft()[i] = change.isResolved(Side.LEFT);
      snapshot.resolvedRight()[i] = change.isResolved(Side.RIGHT);
      snapshot.oneside()[i] = change.isOnesideAppliedConflict();
    }
    return snapshot;
  }

  /** Puts back the Result text and the block states of the snapshot. */
  public void restore(Snapshot snapshot) {
    result.replace(0, result.text().length(), snapshot.text());
    for (int i = 0; i < changes.size(); i++) {
      MergeChange change = changes.get(i);
      change.resultStart = snapshot.starts()[i];
      change.resultEnd = snapshot.ends()[i];
      change.setResolved(Side.LEFT, snapshot.resolvedLeft()[i]);
      change.setResolved(Side.RIGHT, snapshot.resolvedRight()[i]);
      change.setOnesideAppliedConflict(snapshot.oneside()[i]);
    }
    resultVersion++;
  }

  /**
   * The changed words of a block in each column, as DiffUtil.compareThreesideInner: a side that the block does not
   * change, or that is resolved, has none. The ranges are lines and columns of that column; BASE is the Result.
   */
  public record InnerDifferences(List<InnerRange> left, List<InnerRange> base, List<InnerRange> right) {
    static final InnerDifferences NONE = new InnerDifferences(List.of(), List.of(), List.of());
  }

  public InnerDifferences innerDifferences(MergeChange change) {
    if (change.isResolved()) {
      return InnerDifferences.NONE;
    }
    String left = chunk(change, ThreeSide.LEFT);
    String baseChunk = chunk(change, ThreeSide.BASE);
    String right = chunk(change, ThreeSide.RIGHT);
    int present = (left != null ? 1 : 0) + (baseChunk != null ? 1 : 0) + (right != null ? 1 : 0);
    if (present < 2) {
      return InnerDifferences.NONE;
    }
    List<InnerRange> leftRanges = new ArrayList<>();
    List<InnerRange> baseRanges = new ArrayList<>();
    List<InnerRange> rightRanges = new ArrayList<>();
    if (present == 3) {
      for (DiffFragment fragment : ByWord.compare(baseChunk, left, ComparisonPolicy.DEFAULT, DumbProgressIndicator.INSTANCE)) {
        addRange(baseRanges, change, ThreeSide.BASE, baseChunk, fragment.getStartOffset1(), fragment.getEndOffset1());
        addRange(leftRanges, change, ThreeSide.LEFT, left, fragment.getStartOffset2(), fragment.getEndOffset2());
      }
      for (DiffFragment fragment : ByWord.compare(baseChunk, right, ComparisonPolicy.DEFAULT, DumbProgressIndicator.INSTANCE)) {
        addRange(baseRanges, change, ThreeSide.BASE, baseChunk, fragment.getStartOffset1(), fragment.getEndOffset1());
        addRange(rightRanges, change, ThreeSide.RIGHT, right, fragment.getStartOffset2(), fragment.getEndOffset2());
      }
      return new InnerDifferences(leftRanges, baseRanges, rightRanges);
    }
    ThreeSide side1 = left != null ? ThreeSide.LEFT : ThreeSide.BASE;
    ThreeSide side2 = right != null ? ThreeSide.RIGHT : ThreeSide.BASE;
    String chunk1 = side1 == ThreeSide.LEFT ? left : baseChunk;
    String chunk2 = side2 == ThreeSide.RIGHT ? right : baseChunk;
    List<InnerRange> ranges1 = side1 == ThreeSide.LEFT ? leftRanges : baseRanges;
    List<InnerRange> ranges2 = side2 == ThreeSide.RIGHT ? rightRanges : baseRanges;
    for (DiffFragment fragment : ByWord.compare(chunk1, chunk2, ComparisonPolicy.DEFAULT, DumbProgressIndicator.INSTANCE)) {
      addRange(ranges1, change, side1, chunk1, fragment.getStartOffset1(), fragment.getEndOffset1());
      addRange(ranges2, change, side2, chunk2, fragment.getStartOffset2(), fragment.getEndOffset2());
    }
    return new InnerDifferences(leftRanges, baseRanges, rightRanges);
  }

  /** MergeThreesideViewer.getChunks: the block text of a side, or null when the side is unchanged, resolved or empty. */
  private String chunk(MergeChange change, ThreeSide side) {
    if (side != ThreeSide.BASE) {
      Side twoSide = side == ThreeSide.LEFT ? Side.LEFT : Side.RIGHT;
      if (!change.isChange(twoSide) || change.isResolved(twoSide)) {
        return null;
      }
    }
    int start = change.startLine(side);
    int end = change.endLine(side);
    if (start == end) {
      return null;
    }
    return switch (side) {
      case LEFT -> linesText(yours, start, end);
      case RIGHT -> linesText(theirs, start, end);
      case BASE -> result.lines(start, end);
    };
  }

  /** Adds one range per line of the chunk part {@code [from, to)}; the chunk starts at the first line of the block. */
  private static void addRange(List<InnerRange> ranges, MergeChange change, ThreeSide side, String chunk, int from, int to) {
    int line = change.startLine(side);
    int lineStart = 0;
    for (int i = 0; i < from; i++) {
      if (chunk.charAt(i) == NEW_LINE) {
        line++;
        lineStart = i + 1;
      }
    }
    int start = from;
    while (true) {
      int lineEnd = chunk.indexOf(NEW_LINE, start);
      int end = lineEnd < 0 || lineEnd > to ? to : lineEnd;
      if (end > start || from == to) {
        ranges.add(new InnerRange(line, start - lineStart, end - lineStart));
      }
      if (end >= to) {
        return;
      }
      line++;
      lineStart = end + 1;
      start = lineStart;
    }
  }

  boolean canResolveAutomatically(MergeChange change, ThreeSide side) {
    if (change.isConflict()) {
      return side == ThreeSide.BASE && change.type().canBeResolved()
             && !change.isResolved(Side.LEFT) && !change.isResolved(Side.RIGHT)
             && (change.type().getResolutionStrategy() != MergeConflictResolutionStrategy.TEXT || !isRangeModified(change));
    }
    boolean changesSide = side == ThreeSide.BASE || change.isChange(side == ThreeSide.LEFT ? Side.LEFT : Side.RIGHT);
    return !change.isResolved() && changesSide && !isRangeModified(change);
  }

  /** MergeConflictModel.resolveChangeAutomatically. */
  private void resolveAutomatically(MergeChange change, ThreeSide side) {
    if (!canResolveAutomatically(change, side)) {
      return;
    }
    if (change.isConflict()) {
      CharSequence resolved = ComparisonMergeUtil.tryResolveConflict(
        linesText(yours, change.startLine(ThreeSide.LEFT), change.endLine(ThreeSide.LEFT)),
        result.lines(change.resultStart, change.resultEnd),
        linesText(theirs, change.startLine(ThreeSide.RIGHT), change.endLine(ThreeSide.RIGHT)));
      if (resolved == null) {
        return;
      }
      replaceResult(change, resolved.toString().isEmpty() ? List.of() : Arrays.asList(resolved.toString().split("\n", -1)));
      markResolved(change);
    }
    else {
      Side master = side == ThreeSide.LEFT ? Side.LEFT
                    : side == ThreeSide.RIGHT ? Side.RIGHT
                      : change.isChange(Side.LEFT) ? Side.LEFT : Side.RIGHT;
      replaceChange(change, master, false);
    }
  }

  /** The Result lines of the block differ from its base lines: the user edited them. */
  private boolean isRangeModified(MergeChange change) {
    // The bands ask this on every repaint; the answer changes only when the Result changes.
    if (change.modifiedVersion == resultVersion) {
      return change.modified;
    }
    change.modifiedVersion = resultVersion;
    change.modified = computeRangeModified(change);
    return change.modified;
  }

  private boolean computeRangeModified(MergeChange change) {
    String baseContent = linesText(base, change.fragment().getStartLine(ThreeSide.BASE), change.fragment().getEndLine(ThreeSide.BASE));
    return !baseContent.equals(result.lines(change.resultStart, change.resultEnd));
  }

  private static void markResolved(MergeChange change) {
    change.setResolved(Side.LEFT, true);
    change.setResolved(Side.RIGHT, true);
  }

  /** MergeModelBase.replaceChange. */
  private void replaceResult(MergeChange change, List<String> newContent) {
    int start = change.resultStart;
    int end = change.resultEnd;
    result.replaceLines(start, end, newContent);
    if (start == end) {
      moveChangesAfterInsertion(change, start, start + newContent.size());
    }
  }

  /** MergeModelBase.appendChange. */
  private void appendChange(MergeChange change, List<String> newContent) {
    int start = change.resultStart;
    int end = change.resultEnd;
    result.replaceLines(end, end, newContent);
    moveChangesAfterInsertion(change, start, end + newContent.size());
  }

  /** MergeModelBase.moveChangesAfterInsertion: the inserted lines belong to the block, the neighbours keep their order. */
  private void moveChangesAfterInsertion(MergeChange change, int newStart, int newEnd) {
    change.resultStart = newStart;
    change.resultEnd = newEnd;
    boolean beforeChange = true;
    for (MergeChange other : changes) {
      int start = other.resultStart;
      int end = other.resultEnd;
      if (end < newStart) {
        continue;
      }
      if (start > newEnd) {
        break;
      }
      if (other == change) {
        beforeChange = false;
        continue;
      }
      other.resultStart = beforeChange ? Math.min(start, newStart) : newEnd;
      other.resultEnd = beforeChange ? Math.min(end, newStart) : Math.max(end, newEnd);
    }
    resultVersion++;
  }

  /** DiffUtil.updateRangeOnModification, not greedy. */
  static int[] updateRangeOnModification(int start, int end, int changeStart, int changeEnd, int shift) {
    if (end <= changeStart) {
      return new int[]{start, end};
    }
    if (start >= changeEnd) {
      return new int[]{start + shift, end + shift};
    }
    if (start <= changeStart && end >= changeEnd) {
      return new int[]{start, end + shift};
    }
    int newChangeEnd = changeEnd + shift;
    if (start >= changeStart && end <= changeEnd) {
      return new int[]{newChangeEnd, newChangeEnd};
    }
    if (start < changeStart) {
      return new int[]{start, changeStart};
    }
    return new int[]{newChangeEnd, end + shift};
  }

  private List<MergeChange> unresolved() {
    return changes.stream().filter(c -> !c.isResolved()).toList();
  }

  private static List<DiffChange> asRanges(List<MergeChange> blocks) {
    return blocks.stream()
      .map(c -> new DiffChange(c.resultStart, c.resultEnd, c.resultStart, c.resultEnd, c.kind(), List.of(), List.of()))
      .toList();
  }

  private static List<String> lines(DiffText text, int start, int end) {
    List<String> lines = new ArrayList<>(end - start);
    for (int line = start; line < end; line++) {
      lines.add(text.text().substring(text.lineStart(line), text.lineEnd(line)));
    }
    return lines;
  }

  private static String linesText(DiffText text, int start, int end) {
    return start >= end ? "" : text.text().substring(text.lineStart(start), text.lineEnd(end - 1));
  }
}
