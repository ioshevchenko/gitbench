package gitbench.ui.status;

import gitbench.ui.SwtTest;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.ToolItem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ChangeMarkerPopupTest extends SwtTest {
  private LineStatusTracker tracker;
  private final List<Integer> revealed = new ArrayList<>();
  private final List<Integer> diffs = new ArrayList<>();

  @AfterEach
  void disposeTracker() {
    if (tracker != null) {
      tracker.dispose();
    }
  }

  private Document track(String base, String current, int ranges) throws InterruptedException {
    Document document = new Document(current);
    tracker = new LineStatusTracker(document, () -> Optional.of(base), display);
    long end = System.currentTimeMillis() + 5000;
    while (tracker.ranges().size() != ranges && System.currentTimeMillis() < end) {
      while (display.readAndDispatch()) {
        // Keep going until the queue is empty.
      }
      Thread.sleep(10);
    }
    assertThat(tracker.ranges()).hasSize(ranges);
    return document;
  }

  /** The editor text the popup takes its font and colors from: dark, as in the dark theme. */
  private StyledText editorText;

  private ChangeMarkerPopup open(Document document, int range) {
    return open(document, range, new Point(100, 100));
  }

  private ChangeMarkerPopup open(Document document, int range, Point location) {
    if (editorText == null) {
      editorText = new StyledText(shell, SWT.NONE);
      editorText.setBackground(new Color(0x1E, 0x1F, 0x22));
      editorText.setForeground(new Color(0xBC, 0xBE, 0xC4));
      editorText.setFont(JFaceResources.getTextFont());
    }
    ChangeMarkerPopup popup = new ChangeMarkerPopup(shell, editorText, document, tracker, range, location, new ChangeMarkerPopup.Actions() {
      @Override
      public void showDiff(int documentLine) {
        diffs.add(documentLine);
      }

      @Override
      public void reveal(int documentLine) {
        revealed.add(documentLine);
      }
    });
    pump();
    return popup;
  }

  private static ToolItem item(ChangeMarkerPopup popup, String tip) {
    for (ToolItem item : popup.toolBar().getItems()) {
      if (tip.equals(item.getToolTipText())) {
        return item;
      }
    }
    throw new AssertionError("no button " + tip);
  }

  private void click(ToolItem item) {
    item.notifyListeners(SWT.Selection, new Event());
    pump();
  }

  @Test
  void buttonsFollowIntellijOrder() throws Exception {
    Document document = track("a\nb\nc\n", "a\nB\nc\n", 1);
    ChangeMarkerPopup popup = open(document, 0);

    assertThat(popup.toolBar().getItems()).extracting(ToolItem::getToolTipText)
      .containsExactly("Previous Change", "Next Change", "Rollback Lines", "Show Diff for Lines", "Copy");
  }

  @Test
  void showsHeadTextOfTheBlock() throws Exception {
    Document document = track("a\nb\nb2\nc\n", "a\nB\nc\n", 1);
    ChangeMarkerPopup popup = open(document, 0);

    assertThat(popup.text().getText()).isEqualTo("b\nb2");
  }

  @Test
  void rollbackReplacesTheBlockAndCloses() throws Exception {
    Document document = track("a\nb\nc\n", "a\nB\nc\n", 1);
    ChangeMarkerPopup popup = open(document, 0);

    click(item(popup, "Rollback Lines"));

    assertThat(document.get()).isEqualTo("a\nb\nc\n");
    assertThat(popup.shell().isDisposed()).isTrue();
  }

  @Test
  void nextMovesToTheFollowingRange() throws Exception {
    Document document = track("0\n1\n2\n3\n4\n", "X\n1\n2\n3\nY\n", 2);
    ChangeMarkerPopup popup = open(document, 0);

    click(item(popup, "Next Change"));

    assertThat(popup.rangeIndex()).isEqualTo(1);
    assertThat(revealed).containsExactly(4);
    assertThat(popup.text().getText()).isEqualTo("4");
  }

  @Test
  void nextIsDisabledOnTheLastRange() throws Exception {
    Document document = track("0\n1\n2\n3\n4\n", "X\n1\n2\n3\nY\n", 2);
    ChangeMarkerPopup popup = open(document, 1);

    assertThat(item(popup, "Next Change").getEnabled()).isFalse();
    assertThat(item(popup, "Previous Change").getEnabled()).isTrue();
  }

  @Test
  void showDiffPassesTheBlockLine() throws Exception {
    Document document = track("0\n1\n2\n3\n4\n", "0\n1\n2\n3\nY\n", 1);
    ChangeMarkerPopup popup = open(document, 0);

    click(item(popup, "Show Diff for Lines"));

    assertThat(diffs).containsExactly(4);
  }

  @Test
  void copyIsDisabledForInsertedLines() throws Exception {
    Document document = track("a\nc\n", "a\nb\nc\n", 1);
    ChangeMarkerPopup popup = open(document, 0);

    assertThat(item(popup, "Copy").getEnabled()).isFalse();
    assertThat(popup.text()).isNull();
  }

  @Test
  void escapeClosesThePopup() throws Exception {
    Document document = track("a\nb\nc\n", "a\nB\nc\n", 1);
    ChangeMarkerPopup popup = open(document, 0);

    Event escape = new Event();
    escape.detail = SWT.TRAVERSE_ESCAPE;
    popup.shell().notifyListeners(SWT.Traverse, escape);
    pump();

    assertThat(popup.shell().isDisposed()).isTrue();
  }

  @Test
  void popupUsesTheEditorFontAndColors() throws Exception {
    Document document = track("a\nb\nc\n", "a\nB\nc\n", 1);
    ChangeMarkerPopup popup = open(document, 0);

    assertThat(popup.text().getBackground()).isEqualTo(editorText.getBackground());
    assertThat(popup.text().getForeground()).isEqualTo(editorText.getForeground());
    assertThat(popup.text().getFont()).isEqualTo(editorText.getFont());
    assertThat(popup.toolBar().getBackground()).isEqualTo(editorText.getBackground());
  }

  @Test
  void shortTextHasNoScrollBars() throws Exception {
    Document document = track("a\nb\nb2\nc\n", "a\nB\nc\n", 1);
    ChangeMarkerPopup popup = open(document, 0);

    assertThat(popup.text().getHorizontalBar().isVisible()).isFalse();
    assertThat(popup.text().getVerticalBar().isVisible()).isFalse();
    assertThat(popup.text().getClientArea().height).isGreaterThanOrEqualTo(2 * popup.text().getLineHeight());
  }

  @Test
  void popupStaysOnTheMonitor() throws Exception {
    Document document = track("a\nb\nc\n", "a\nB\nc\n", 1);
    Rectangle area = display.getPrimaryMonitor().getClientArea();

    ChangeMarkerPopup popup = open(document, 0, new Point(area.x + area.width - 5, area.y + area.height - 5));

    assertThat(area.contains(popup.shell().getBounds().x, popup.shell().getBounds().y)).isTrue();
    Rectangle bounds = popup.shell().getBounds();
    assertThat(bounds.x + bounds.width).isLessThanOrEqualTo(area.x + area.width);
    assertThat(bounds.y + bounds.height).isLessThanOrEqualTo(area.y + area.height);
  }

  @Test
  void aClickOutsideClosesThePopupAndAClickInsideKeepsIt() throws Exception {
    Document document = track("a\nb\nc\n", "a\nB\nc\n", 1);
    ChangeMarkerPopup popup = open(document, 0);

    popup.text().notifyListeners(SWT.MouseDown, new Event());
    pump();
    assertThat(popup.shell().isDisposed()).isFalse();

    editorText.notifyListeners(SWT.MouseDown, new Event());
    pump();
    assertThat(popup.shell().isDisposed()).isTrue();
  }
}
