package gitbench.ui.diff;

import gitbench.diff.BlockKind;
import gitbench.diff.DiffChange;
import gitbench.diff.DiffModel;
import gitbench.diff.InnerRange;
import gitbench.diff.Side;
import gitbench.diff.SyncScroll;
import gitbench.ui.GitbenchIcons;
import gitbench.ui.GitbenchIcons.Icon;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextOperationTarget;
import org.eclipse.jface.text.ITextPresentationListener;
import org.eclipse.jface.text.TextPresentation;
import org.eclipse.jface.text.source.SourceViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CLabel;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Layout;
import org.eclipse.swt.widgets.Listener;

import java.util.ArrayList;
import java.util.List;

/**
 * The Side-by-side viewer, as IntelliJ SimpleDiffViewer: two text columns with line numbers and the curved band
 * between them. The {@code >>} buttons on the band copy a block to the right side when it can be edited.
 */
final class SplitViewer implements DiffPane {
  private static final int DIVIDER_WIDTH = 30;
  private static final int ARROW_SIZE = 14;
  private static final String REPLACE_SHORTCUT = " (Ctrl+Alt+R)";

  private final Host host;
  private final boolean dark;
  private final boolean hasReconciler;
  private final Composite control;
  private final CLabel leftTitle;
  private final CLabel rightTitle;
  private final SourceViewer left;
  private final SourceViewer right;
  private final Canvas divider;
  private final List<ArrowHit> arrows = new ArrayList<>();
  private final Listener ctrlFilter;
  private DiffContent content;
  private Side focused = Side.RIGHT;
  private boolean ctrlDown;
  private boolean syncing;

  /** A painted {@code >>} button and the block it applies. */
  private record ArrowHit(Rectangle bounds, DiffChange change, boolean append) {
  }

  SplitViewer(Composite parent, Host host, DiffContent initial, boolean dark) {
    this.host = host;
    this.dark = dark;
    control = new Composite(parent, SWT.NONE);
    leftTitle = new CLabel(control, SWT.NONE);
    rightTitle = new CLabel(control, SWT.NONE);
    left = TextViewers.create(control, dark, initial.left(), false);
    divider = new Canvas(control, SWT.DOUBLE_BUFFERED);
    right = TextViewers.create(control, dark, initial.right(), initial.rightEditable());
    hasReconciler = Highlighting.configure(left, initial.left(), initial.file())
                    & Highlighting.configure(right, initial.right(), initial.file());
    control.setLayout(new SplitLayout());

    installSide(left, Side.LEFT);
    installSide(right, Side.RIGHT);
    divider.addListener(SWT.Paint, this::paintDivider);
    divider.addListener(SWT.MouseDown, this::dividerClicked);
    divider.addListener(SWT.MouseMove, this::dividerHovered);
    ctrlFilter = e -> {
      boolean down = e.type == SWT.KeyDown ? (e.keyCode == SWT.CTRL || (e.stateMask & SWT.CTRL) != 0)
                                           : (e.stateMask & SWT.CTRL) != 0 && e.keyCode != SWT.CTRL;
      if (down != ctrlDown) {
        ctrlDown = down;
        if (!divider.isDisposed()) {
          divider.redraw();
        }
      }
    };
    Display display = control.getDisplay();
    display.addFilter(SWT.KeyDown, ctrlFilter);
    display.addFilter(SWT.KeyUp, ctrlFilter);
    setContent(initial);
  }

  private void installSide(SourceViewer viewer, Side side) {
    StyledText text = viewer.getTextWidget();
    text.addLineBackgroundListener(e -> {
      if (content == null) {
        return;
      }
      int line = text.getLineAtOffset(e.lineOffset);
      DiffChange change = changeCovering(line, side);
      if (change != null) {
        e.lineBackground = change.hasInner() ? DiffColors.ignoredColor(change.kind(), dark, text.getBackground())
                                             : DiffColors.color(change.kind(), dark);
      }
    });
    text.addListener(SWT.Paint, e -> paintInsertionLines(e.gc, text, side));
    viewer.addTextPresentationListener(new WordHighlighter(viewer, side));
    viewer.addViewportListener(offset -> {
      syncScroll(side);
      divider.redraw();
    });
    text.getHorizontalBar().addListener(SWT.Selection, e -> {
      if (!syncing) {
        syncing = true;
        try {
          other(side).getTextWidget().setHorizontalPixel(text.getHorizontalPixel());
        }
        finally {
          syncing = false;
        }
      }
    });
    text.addListener(SWT.FocusIn, e -> focused = side);
    // Before StyledText handles the key, so a consumed key such as Ctrl+Alt+R (AltGr on some layouts) types nothing.
    viewer.prependVerifyKeyListener(e -> {
      if (host.handleKey(e.keyCode, e.stateMask)) {
        e.doit = false;
      }
    });
    text.addListener(SWT.Resize, e -> divider.redraw());
  }

  @Override
  public Control control() {
    return control;
  }

  /** The band between the columns, for the UI tests. */
  Canvas divider() {
    return divider;
  }

  /** The text widget of a column, for the UI tests. */
  StyledText text(Side side) {
    return viewer(side).getTextWidget();
  }

  @Override
  public void setContent(DiffContent newContent) {
    DiffContent old = content;
    content = newContent;
    if (old == null || old.left() != newContent.left()) {
      left.setDocument(newContent.left());
    }
    if (old == null || old.right() != newContent.right()) {
      right.setDocument(newContent.right());
    }
    right.setEditable(newContent.rightEditable());
    leftTitle.setText(newContent.leftTitle());
    leftTitle.setImage(GitbenchIcons.get(Icon.LOCKED));
    rightTitle.setText(newContent.rightTitle());
    rightTitle.setImage(newContent.rightEditable() ? null : GitbenchIcons.get(Icon.LOCKED));
    TextViewers.refreshPresentation(left, hasReconciler);
    TextViewers.refreshPresentation(right, hasReconciler);
    left.getTextWidget().redraw();
    right.getTextWidget().redraw();
    divider.redraw();
    control.layout(true);
  }

  @Override
  public boolean navigate(boolean next) {
    if (content == null) {
      return false;
    }
    StyledText text = viewer(focused).getTextWidget();
    int line = text.getLineAtOffset(text.getCaretOffset());
    int index = next ? DiffModel.next(content.changes(), line, focused) : DiffModel.previous(content.changes(), line, focused);
    if (index < 0) {
      return false;
    }
    goTo(content.changes().get(index), focused);
    return true;
  }

  @Override
  public void reveal(DiffChange change) {
    goTo(change, focused);
  }

  /** Scrolls the block to the middle of the column, as IntelliJ scrollToChange, and puts the caret on its first line. */
  private void goTo(DiffChange change, Side side) {
    SourceViewer viewer = viewer(side);
    StyledText text = viewer.getTextWidget();
    int line = Math.min(change.startLine(side), Math.max(0, text.getLineCount() - 1));
    int visible = Math.max(1, text.getClientArea().height / Math.max(1, text.getLineHeight()));
    text.setTopIndex(Math.max(0, line - visible / 2));
    try {
      text.setCaretOffset(viewer.getDocument().getLineOffset(line));
    }
    catch (BadLocationException e) {
      text.setCaretOffset(text.getCharCount());
    }
    syncScroll(side);
    text.setFocus();
    divider.redraw();
  }

  @Override
  public DiffChange changeAtCaret() {
    if (content == null) {
      return null;
    }
    StyledText text = viewer(focused).getTextWidget();
    int line = text.getLineAtOffset(text.getCaretOffset());
    for (DiffChange change : content.changes()) {
      int start = change.startLine(focused);
      int end = change.endLine(focused);
      if (start <= line && line < end || start == end && start == line) {
        return change;
      }
    }
    return null;
  }

  @Override
  public ITextOperationTarget focusedTarget() {
    return viewer(focused).getTextWidget().isFocusControl() ? viewer(focused) : null;
  }

  @Override
  public void setFocus() {
    viewer(focused).getTextWidget().setFocus();
  }

  @Override
  public void dispose() {
    Display display = control.getDisplay();
    display.removeFilter(SWT.KeyDown, ctrlFilter);
    display.removeFilter(SWT.KeyUp, ctrlFilter);
    control.dispose();
  }

  private SourceViewer viewer(Side side) {
    return side == Side.LEFT ? left : right;
  }

  private SourceViewer other(Side side) {
    return viewer(side.other());
  }

  private DiffChange changeCovering(int line, Side side) {
    for (DiffChange change : content.changes()) {
      if (change.startLine(side) <= line && line < change.endLine(side)) {
        return change;
      }
      if (change.startLine(side) > line) {
        break;
      }
    }
    return null;
  }

  /** Keeps the middle lines of the columns together, as IntelliJ SyncScrollSupport. */
  private void syncScroll(Side master) {
    if (syncing || content == null) {
      return;
    }
    syncing = true;
    try {
      StyledText from = viewer(master).getTextWidget();
      StyledText to = other(master).getTextWidget();
      int visible = Math.max(1, from.getClientArea().height / Math.max(1, from.getLineHeight()));
      int center = from.getTopIndex() + visible / 2;
      int target = SyncScroll.transfer(content.changes(), master, center);
      to.setTopIndex(Math.max(0, target - visible / 2));
    }
    finally {
      syncing = false;
    }
  }

  /** A block without lines on this side is drawn as a line at its insertion point. */
  private void paintInsertionLines(GC gc, StyledText text, Side side) {
    if (content == null) {
      return;
    }
    int width = text.getClientArea().width;
    for (DiffChange change : content.changes()) {
      if (change.startLine(side) != change.endLine(side)) {
        continue;
      }
      int y = DividerPainter.lineToY(text, change.startLine(side), text);
      if (y < -1 || y > text.getClientArea().height + 1) {
        continue;
      }
      gc.setForeground(DiffColors.color(change.kind(), dark));
      gc.setLineWidth(1);
      gc.drawLine(0, Math.max(0, y - 1), width, Math.max(0, y - 1));
    }
  }

  private void paintDivider(Event e) {
    Rectangle area = divider.getClientArea();
    e.gc.setBackground(DiffColors.separators(dark));
    e.gc.fillRectangle(area);
    arrows.clear();
    if (content == null) {
      return;
    }
    StyledText leftText = left.getTextWidget();
    StyledText rightText = right.getTextWidget();
    DividerPainter.paint(e.gc, area.width, area.height, leftText, rightText, divider, content.changes(), dark);
    if (!content.rightEditable()) {
      return;
    }
    for (DiffChange change : content.changes()) {
      int y = DividerPainter.lineToY(leftText, change.startLine1(), divider);
      int lineHeight = leftText.getLineHeight();
      if (y + lineHeight < 0 || y > area.height) {
        continue;
      }
      boolean append = ctrlDown && change.kind() == BlockKind.MODIFIED;
      Image image = GitbenchIcons.get(append ? Icon.DIFF_ARROW_RIGHT_DOWN : Icon.DIFF_ARROW_RIGHT);
      int top = y + (lineHeight - ARROW_SIZE) / 2;
      e.gc.drawImage(image, 2, top);
      arrows.add(new ArrowHit(new Rectangle(2, top, ARROW_SIZE, ARROW_SIZE), change, append));
    }
  }

  private void dividerClicked(Event e) {
    if (e.button != 1) {
      return;
    }
    ArrowHit hit = arrowAt(e.x, e.y);
    if (hit != null) {
      host.apply(hit.change(), hit.append());
    }
  }

  private void dividerHovered(Event e) {
    ArrowHit hit = arrowAt(e.x, e.y);
    divider.setCursor(hit != null ? divider.getDisplay().getSystemCursor(SWT.CURSOR_HAND) : null);
    String tooltip = hit == null ? null
                     : hit.append() ? "Append"
                       : (content.localChanges() ? "Revert" : "Accept") + REPLACE_SHORTCUT;
    if (!java.util.Objects.equals(divider.getToolTipText(), tooltip)) {
      divider.setToolTipText(tooltip);
    }
  }

  private ArrowHit arrowAt(int x, int y) {
    for (ArrowHit arrow : arrows) {
      if (arrow.bounds().contains(x, y)) {
        return arrow;
      }
    }
    return null;
  }

  /** Adds the background of the changed words to each presentation of one column. */
  private final class WordHighlighter implements ITextPresentationListener {
    private final SourceViewer viewer;
    private final Side side;

    WordHighlighter(SourceViewer viewer, Side side) {
      this.viewer = viewer;
      this.side = side;
    }

    @Override
    public void applyTextPresentation(TextPresentation presentation) {
      if (content == null) {
        return;
      }
      IDocument document = viewer.getDocument();
      int lineCount = document.getNumberOfLines();
      for (DiffChange change : content.changes()) {
        Color color = DiffColors.color(change.kind(), dark);
        for (InnerRange range : change.inner(side)) {
          if (range.endColumn() <= range.startColumn() || range.line() >= lineCount) {
            continue;
          }
          try {
            int offset = document.getLineOffset(range.line()) + range.startColumn();
            int length = Math.min(range.endColumn() - range.startColumn(), document.getLength() - offset);
            if (length > 0 && overlaps(presentation.getExtent(), offset, length)) {
              presentation.mergeStyleRange(new StyleRange(offset, length, null, color));
            }
          }
          catch (BadLocationException ignored) {
            // The document changed after the comparison; the next comparison repaints.
          }
        }
      }
    }
  }

  private static boolean overlaps(org.eclipse.jface.text.IRegion extent, int offset, int length) {
    return offset < extent.getOffset() + extent.getLength() && extent.getOffset() < offset + length;
  }

  /** Header labels in the first row; the left column, the divider and the right column in the second. */
  private final class SplitLayout extends Layout {
    @Override
    protected Point computeSize(Composite composite, int wHint, int hHint, boolean flushCache) {
      return new Point(wHint == SWT.DEFAULT ? 400 : wHint, hHint == SWT.DEFAULT ? 300 : hHint);
    }

    @Override
    protected void layout(Composite composite, boolean flushCache) {
      Rectangle area = composite.getClientArea();
      int titleHeight = leftTitle.computeSize(SWT.DEFAULT, SWT.DEFAULT).y;
      int column = (area.width - DIVIDER_WIDTH) / 2;
      leftTitle.setBounds(area.x, area.y, column, titleHeight);
      rightTitle.setBounds(area.x + column + DIVIDER_WIDTH, area.y, area.width - column - DIVIDER_WIDTH, titleHeight);
      int bodyY = area.y + titleHeight;
      int bodyHeight = Math.max(0, area.height - titleHeight);
      left.getControl().setBounds(area.x, bodyY, column, bodyHeight);
      divider.setBounds(area.x + column, bodyY, DIVIDER_WIDTH, bodyHeight);
      right.getControl().setBounds(area.x + column + DIVIDER_WIDTH, bodyY, area.width - column - DIVIDER_WIDTH, bodyHeight);
    }
  }
}
