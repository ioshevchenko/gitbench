package gitbench.ui.diff;

import gitbench.diff.UnifiedLine;
import gitbench.diff.UnifiedModel;
import org.eclipse.jface.text.source.LineNumberRulerColumn;

import java.util.function.Supplier;

/**
 * One line-number column of the unified diff: the line numbers of one side, empty where the line does not exist on
 * that side. The unified gutter of IntelliJ shows two such columns, left side first.
 */
final class UnifiedRuler extends LineNumberRulerColumn {
  private final Supplier<UnifiedModel> model;
  private final boolean leftSide;

  UnifiedRuler(Supplier<UnifiedModel> model, boolean leftSide) {
    this.model = model;
    this.leftSide = leftSide;
  }

  @Override
  protected String createDisplayString(int line) {
    UnifiedModel current = model.get();
    if (current == null || line >= current.lines().size()) {
      return "";
    }
    UnifiedLine unified = current.lines().get(line);
    int number = leftSide ? unified.leftLine() : unified.rightLine();
    return number < 0 ? "" : Integer.toString(number + 1);
  }

  @Override
  protected int computeNumberOfDigits() {
    UnifiedModel current = model.get();
    int max = 1;
    if (current != null) {
      for (UnifiedLine line : current.lines()) {
        max = Math.max(max, (leftSide ? line.leftLine() : line.rightLine()) + 1);
      }
    }
    // One more place than the digits: the numbers are right-aligned, so the two columns do not run together.
    return Math.max(2, Integer.toString(max).length()) + 1;
  }
}
