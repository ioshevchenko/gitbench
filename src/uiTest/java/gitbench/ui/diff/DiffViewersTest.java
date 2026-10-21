package gitbench.ui.diff;

import gitbench.diff.DiffChange;
import gitbench.diff.DiffModel;
import gitbench.diff.DiffText;
import gitbench.diff.HighlightPolicy;
import gitbench.diff.IgnorePolicy;
import gitbench.diff.UnifiedModel;
import gitbench.ui.SwtTest;
import org.eclipse.jface.text.Document;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The Side-by-side and Unified viewers on real widgets. */
class DiffViewersTest extends SwtTest {
  /** Records what the viewers ask of the editor. */
  private static final class RecordingHost implements DiffPane.Host {
    final List<DiffChange> applied = new ArrayList<>();
    final List<Boolean> appended = new ArrayList<>();

    @Override
    public void apply(DiffChange change, boolean append) {
      applied.add(change);
      appended.add(append);
    }

    @Override
    public boolean handleKey(int keyCode, int stateMask) {
      return false;
    }

    @Override
    public void contentChanged() {
    }
  }

  private static DiffContent content(String left, String right, IgnorePolicy ignore) {
    DiffText leftText = DiffText.of(left);
    DiffText rightText = DiffText.of(right);
    List<DiffChange> changes = DiffModel.compute(leftText, rightText, ignore, HighlightPolicy.BY_WORD);
    return new DiffContent(new Document(leftText.text()), new Document(right), "left", "right", true, true, Path.of("a.txt"), null,
                           leftText, rightText, changes);
  }

  private static String lines(int count, int insertAt, int inserted) {
    StringBuilder text = new StringBuilder();
    for (int i = 0; i < count; i++) {
      if (i == insertAt) {
        for (int j = 0; j < inserted; j++) {
          text.append("inserted ").append(j).append('\n');
        }
      }
      text.append("line ").append(i).append('\n');
    }
    return text.toString();
  }

  private static StyledText text(Composite root, int index) {
    List<StyledText> texts = new ArrayList<>();
    collectTexts(root, texts);
    return texts.get(index);
  }

  private static void collectTexts(Composite parent, List<StyledText> result) {
    for (Control child : parent.getChildren()) {
      if (child instanceof StyledText styledText) {
        result.add(styledText);
      }
      else if (child instanceof Composite composite) {
        collectTexts(composite, result);
      }
    }
  }

  @Test
  void arrowOnTheBandAppliesTheBlock() {
    RecordingHost host = new RecordingHost();
    DiffContent content = content("a\nb\nold\nd\n", "a\nb\nnew\nd\n", IgnorePolicy.DEFAULT);
    SplitViewer viewer = new SplitViewer(shell, host, content, false);
    show();
    Canvas divider = viewer.divider();
    paint(divider);
    StyledText left = viewer.text(gitbench.diff.Side.LEFT);
    DiffChange change = content.changes().get(0);

    int y = DividerPainter.lineToY(left, change.startLine1(), divider) + left.getLineHeight() / 2;
    click(divider, 2 + 7, y);

    assertThat(host.applied).containsExactly(change);
    assertThat(host.appended).containsExactly(false);
    viewer.dispose();
  }

  @Test
  void clickOutsideTheArrowDoesNothing() {
    RecordingHost host = new RecordingHost();
    DiffContent content = content("a\nb\nold\nd\n", "a\nb\nnew\nd\n", IgnorePolicy.DEFAULT);
    SplitViewer viewer = new SplitViewer(shell, host, content, false);
    show();
    Canvas divider = viewer.divider();
    paint(divider);

    click(divider, 2 + 7, 1);

    assertThat(host.applied).isEmpty();
  }

  @Test
  void scrollingTheLeftColumnScrollsTheRightOneThroughTheBlock() {
    DiffContent content = content(lines(200, -1, 0), lines(200, 20, 10), IgnorePolicy.DEFAULT);
    SplitViewer viewer = new SplitViewer(shell, new RecordingHost(), content, false);
    show();
    StyledText left = viewer.text(gitbench.diff.Side.LEFT);
    StyledText right = viewer.text(gitbench.diff.Side.RIGHT);

    left.setTopIndex(100);
    left.getVerticalBar().notifyListeners(SWT.Selection, new Event());
    pump();

    // Below the block the right column has ten more lines, so it stays ten lines further down.
    assertThat(right.getTopIndex() - left.getTopIndex()).isEqualTo(10);
  }

  @Test
  void unifiedViewerShowsTrimmedBlankLinesWithoutFailing() {
    DiffContent content = content("a\nb\n", "a\n\nb\n", IgnorePolicy.IGNORE_WHITESPACES_CHUNKS);
    UnifiedViewer viewer = new UnifiedViewer(shell, new RecordingHost(), content, false);
    show();

    StyledText unified = text(shell, 0);
    assertThat(unified.getText()).isEqualTo(UnifiedModel.build(content.leftText(), content.rightText(), content.changes()).text());
    viewer.dispose();
  }

  @Test
  void unifiedViewerShowsDeletedAndInsertedLines() {
    DiffContent content = content("a\nold\nc\n", "a\nnew\nc\n", IgnorePolicy.DEFAULT);
    new UnifiedViewer(shell, new RecordingHost(), content, false);
    show();

    assertThat(text(shell, 0).getText()).isEqualTo("a\nold\nnew\nc\n");
  }
}
