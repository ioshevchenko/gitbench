package gitbench.merge;

import com.intellij.diff.fragments.MergeLineFragment;
import com.intellij.diff.util.MergeConflictType;
import com.intellij.diff.util.ThreeSide;
import gitbench.diff.BlockKind;
import gitbench.diff.Side;

/**
 * One block of the merge window, as IntelliJ TextMergeChange: its lines in Yours and Theirs, its current lines in the
 * Result (they move when the Result changes), and which sides are resolved.
 */
public final class MergeChange {
  private final int index;
  private final MergeLineFragment fragment;
  private final MergeConflictType type;
  private final MergeModel model;
  private final boolean[] resolved = new boolean[2];
  private boolean onesideAppliedConflict;
  int resultStart;
  int resultEnd;

  MergeChange(int index, MergeLineFragment fragment, MergeConflictType type, MergeModel model) {
    this.index = index;
    this.fragment = fragment;
    this.type = type;
    this.model = model;
    resultStart = fragment.getStartLine(ThreeSide.BASE);
    resultEnd = fragment.getEndLine(ThreeSide.BASE);
  }

  public int index() {
    return index;
  }

  MergeLineFragment fragment() {
    return fragment;
  }

  MergeConflictType type() {
    return type;
  }

  /** The lines of the side; for BASE these are the current lines in the Result. */
  public int startLine(ThreeSide side) {
    return side == ThreeSide.BASE ? resultStart : fragment.getStartLine(side);
  }

  public int endLine(ThreeSide side) {
    return side == ThreeSide.BASE ? resultEnd : fragment.getEndLine(side);
  }

  public boolean isConflict() {
    return type.getType() == MergeConflictType.Type.CONFLICT;
  }

  /** The color of the block; a conflict has its own color, see {@link #isConflict()}. */
  public BlockKind kind() {
    return switch (type.getType()) {
      case INSERTED -> BlockKind.INSERTED;
      case DELETED -> BlockKind.DELETED;
      default -> BlockKind.MODIFIED;
    };
  }

  /** The side differs from the base in this block. */
  public boolean isChange(Side side) {
    return type.isChange(side == Side.LEFT ? com.intellij.diff.util.Side.LEFT : com.intellij.diff.util.Side.RIGHT);
  }

  public boolean isResolved() {
    return resolved[0] && resolved[1];
  }

  public boolean isResolved(Side side) {
    return resolved[side == Side.LEFT ? 0 : 1];
  }

  void setResolved(Side side, boolean value) {
    resolved[side == Side.LEFT ? 0 : 1] = value;
  }

  /** One side of the conflict is in the Result; the other side is now appended, not replaced. */
  public boolean isOnesideAppliedConflict() {
    return onesideAppliedConflict;
  }

  void markOnesideAppliedConflict() {
    onesideAppliedConflict = true;
  }

  /** The magic button applies to this block, as MergeConflictModel.canResolveChangeAutomatically(BASE). */
  public boolean canResolveAutomatically() {
    return model.canResolveAutomatically(this, ThreeSide.BASE);
  }
}
