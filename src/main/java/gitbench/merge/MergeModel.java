package gitbench.merge;

import com.intellij.diff.comparison.ComparisonManagerImpl;
import com.intellij.diff.comparison.ComparisonMergeUtil;
import com.intellij.diff.comparison.ComparisonPolicy;
import com.intellij.diff.fragments.MergeLineFragment;
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
  private final List<MergeChange> changes = new ArrayList<>();

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
    for (MergeChange change : changes) {
      int[] range = updateRangeOnModification(change.resultStart, change.resultEnd, line1, line2, shift);
      change.resultStart = range[0];
      change.resultEnd = range[1];
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
