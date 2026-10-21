package gitbench.merge;

import com.intellij.diff.util.ThreeSide;
import gitbench.diff.DiffText;
import gitbench.diff.IgnorePolicy;
import gitbench.diff.Side;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MergeModelTest {
  /** A result held in a string; each change goes through {@link ResultText#beforeChange}, as a document listener does. */
  static final class StringResultText extends ResultText {
    private String text;

    StringResultText(String text) {
      this.text = text;
    }

    @Override
    public String text() {
      return text;
    }

    @Override
    protected void doReplace(int offset, int length, String newText) {
      beforeChange(offset, length, newText);
      text = text.substring(0, offset) + newText + text.substring(offset + length);
    }

    /** A manual edit by the user, at a line and column. */
    void type(int line, int column, int length, String newText) {
      DiffText current = DiffText.of(text);
      replace(current.lineStart(line) + column, length, newText);
    }
  }

  private StringResultText result;

  private MergeModel model(String yours, String base, String theirs) {
    result = new StringResultText(base);
    return MergeModel.create(DiffText.of(yours), DiffText.of(base), DiffText.of(theirs), IgnorePolicy.DEFAULT, result);
  }

  @Test
  void nonConflictingLeftChangeIsChange() {
    MergeModel model = model("a\nB\nc\n", "a\nb\nc\n", "a\nb\nc\n");

    assertThat(model.changes()).singleElement().satisfies(change -> {
      assertThat(change.isConflict()).isFalse();
      assertThat(change.isChange(Side.LEFT)).isTrue();
      assertThat(change.isChange(Side.RIGHT)).isFalse();
      assertThat(change.startLine(ThreeSide.BASE)).isEqualTo(1);
      assertThat(change.endLine(ThreeSide.BASE)).isEqualTo(2);
    });
  }

  @Test
  void bothSidesChangeSameLineIsConflict() {
    MergeModel model = model("a\nX\nc\n", "a\nb\nc\n", "a\nY\nc\n");

    assertThat(model.changes()).singleElement().satisfies(change -> assertThat(change.isConflict()).isTrue());
    assertThat(model.conflictsCount()).isEqualTo(1);
    assertThat(model.changesCount()).isEqualTo(0);
  }

  @Test
  void replaceLeftPutsYoursIntoResult() {
    MergeModel model = model("a\nB\nc\n", "a\nb\nc\n", "a\nb\nc\n");

    model.replaceChange(model.changes().get(0), Side.LEFT);

    assertThat(result.text()).isEqualTo("a\nB\nc\n");
    assertThat(model.changes().get(0).isResolved()).isTrue();
    assertThat(model.isAllResolved()).isTrue();
  }

  @Test
  void secondSideOfConflictIsAppended() {
    MergeModel model = model("a\nX\nc\n", "a\nb\nc\n", "a\nY\nc\n");
    MergeChange conflict = model.changes().get(0);

    model.replaceChange(conflict, Side.LEFT);
    assertThat(result.text()).isEqualTo("a\nX\nc\n");
    assertThat(conflict.isOnesideAppliedConflict()).isTrue();
    assertThat(conflict.isResolved(Side.LEFT)).isTrue();
    assertThat(conflict.isResolved()).isFalse();

    model.replaceChange(conflict, Side.RIGHT);
    assertThat(result.text()).isEqualTo("a\nX\nY\nc\n");
    assertThat(conflict.isResolved()).isTrue();
    assertThat(conflict.startLine(ThreeSide.BASE)).isEqualTo(1);
    assertThat(conflict.endLine(ThreeSide.BASE)).isEqualTo(3);
  }

  @Test
  void ignoreMarksSideResolvedWithoutEdit() {
    MergeModel model = model("a\nX\nc\n", "a\nb\nc\n", "a\nY\nc\n");
    MergeChange conflict = model.changes().get(0);

    model.ignoreChange(conflict, Side.LEFT);

    assertThat(result.text()).isEqualTo("a\nb\nc\n");
    assertThat(conflict.isResolved(Side.LEFT)).isTrue();
    assertThat(conflict.isResolved()).isFalse();
  }

  @Test
  void applyNonConflictingAllLeavesOnlyConflicts() {
    MergeModel model = model("L\nb\nX\nd\ne\n", "a\nb\nc\nd\ne\n", "a\nb\nY\nd\nR\n");

    model.applyNonConflicting(ThreeSide.BASE);

    assertThat(result.text()).isEqualTo("L\nb\nc\nd\nR\n");
    assertThat(model.changesCount()).isEqualTo(0);
    assertThat(model.conflictsCount()).isEqualTo(1);
  }

  @Test
  void applyNonConflictingLeftTakesOnlyLeftChanges() {
    MergeModel model = model("L\nb\nc\n", "a\nb\nc\n", "a\nb\nR\n");

    model.applyNonConflicting(ThreeSide.LEFT);

    assertThat(result.text()).isEqualTo("L\nb\nc\n");
    assertThat(model.changesCount()).isEqualTo(1);
  }

  @Test
  void simpleConflictIsResolvedByMagic() {
    MergeModel model = model("int a = 1, b = 0;\n", "int a = 0, b = 0;\n", "int a = 0, b = 3;\n");
    // Both sides change the same line, but different words: IntelliJ's magic resolves it.
    assertThat(model.changes().get(0).isConflict()).isTrue();
    assertThat(model.changes().get(0).canResolveAutomatically()).isTrue();

    model.resolveSimpleConflicts();

    assertThat(result.text()).isEqualTo("int a = 1, b = 3;\n");
    assertThat(model.isAllResolved()).isTrue();
  }

  @Test
  void manualEditAboveShiftsChanges() {
    MergeModel model = model("a\nb\nX\nd\n", "a\nb\nc\nd\n", "a\nb\nc\nd\n");

    result.type(0, 1, 0, "\nnew line");

    assertThat(result.text()).isEqualTo("a\nnew line\nb\nc\nd\n");
    assertThat(model.changes().get(0).startLine(ThreeSide.BASE)).isEqualTo(3);
    assertThat(model.changes().get(0).endLine(ThreeSide.BASE)).isEqualTo(4);
  }

  @Test
  void replaceAfterManualEditTargetsShiftedLines() {
    MergeModel model = model("a\nb\nX\nd\n", "a\nb\nc\nd\n", "a\nb\nc\nd\n");
    result.type(0, 1, 0, "\nnew line");

    model.replaceChange(model.changes().get(0), Side.LEFT);

    assertThat(result.text()).isEqualTo("a\nnew line\nb\nX\nd\n");
  }

  @Test
  void manualEditInsideChangeKeepsItUnresolved() {
    MergeModel model = model("a\nX\nc\n", "a\nb\nc\n", "a\nY\nc\n");

    result.type(1, 0, 1, "own");

    MergeChange conflict = model.changes().get(0);
    assertThat(result.text()).isEqualTo("a\nown\nc\n");
    assertThat(conflict.isResolved()).isFalse();
    assertThat(conflict.startLine(ThreeSide.BASE)).isEqualTo(1);
    assertThat(conflict.endLine(ThreeSide.BASE)).isEqualTo(2);
  }

  @Test
  void acceptAllTakesWholeSide() {
    MergeModel model = model("L\nb\nX\n", "a\nb\nc\n", "a\nb\nY\n");

    model.acceptAll(Side.RIGHT);

    assertThat(result.text()).isEqualTo("a\nb\nY\n");
    assertThat(model.isAllResolved()).isTrue();
  }

  @Test
  void countsMatchIntellijStatus() {
    MergeModel model = model("L\nb\nX\nd\ne\n", "a\nb\nc\nd\ne\n", "a\nb\nY\nd\nR\n");

    assertThat(model.changesCount()).isEqualTo(2);
    assertThat(model.conflictsCount()).isEqualTo(1);
    assertThat(MergeModel.statusText(2, 1)).isEqualTo("2 changes, 1 conflict");
    assertThat(MergeModel.statusText(0, 0)).isEqualTo("No changes, No conflicts");
    assertThat(MergeModel.statusText(1, 3)).isEqualTo("1 change, 3 conflicts");
  }

  @Test
  void nextAndPreviousGoOverUnresolvedChanges() {
    MergeModel model = model("L\nb\nX\nd\ne\n", "a\nb\nc\nd\ne\n", "a\nb\nY\nd\nR\n");

    assertThat(model.next(0)).isSameAs(model.changes().get(1));
    // A conflict applied from one side is still unresolved, as in IntelliJ; ignoring both sides resolves it.
    model.ignoreChange(model.changes().get(1), Side.LEFT);
    model.ignoreChange(model.changes().get(1), Side.RIGHT);
    assertThat(model.next(0)).isSameAs(model.changes().get(2));
    assertThat(model.previous(4)).isSameAs(model.changes().get(0));
  }
}
