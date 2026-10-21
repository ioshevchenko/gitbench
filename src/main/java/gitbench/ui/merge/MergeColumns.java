package gitbench.ui.merge;

import com.intellij.diff.util.ThreeSide;
import gitbench.diff.DiffChange;
import gitbench.diff.Side;
import gitbench.diff.SyncScroll;
import gitbench.merge.MergeChange;
import gitbench.merge.MergeModel;
import gitbench.ui.GitbenchIcons;
import gitbench.ui.GitbenchIcons.Icon;
import gitbench.ui.diff.CdtHighlighting;
import gitbench.ui.diff.DiffColors;
import gitbench.ui.diff.DividerPainter;
import gitbench.ui.diff.TextViewers;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.source.SourceViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CLabel;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Layout;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The three columns of the merge window, as IntelliJ MergeThreesideViewer: Yours, Result and Theirs, with a curved
 * band between each pair. The band on the left holds {@code >>} and {@code x} for Yours and the magic button of a
 * simple conflict; the band on the right holds {@code <<} and {@code x} for Theirs.
 */
final class MergeColumns {
  /** What the block buttons and the keys do. */
  interface Actions {
    void replace(MergeChange change, Side side);

    void ignore(MergeChange change, Side side);

    void magic(MergeChange change);

    /** Returns true when the key is consumed. */
    boolean key(int keyCode, int stateMask);
  }

  private static final int DIVIDER_WIDTH = 44;
  private static final int ICON_SIZE = 14;

  private final boolean dark;
  private final Actions actions;
  private final Composite control;
  private final CLabel[] titles = new CLabel[3];
  private final SourceViewer yours;
  private final SourceViewer result;
  private final SourceViewer theirs;
  private final Canvas leftDivider;
  private final Canvas rightDivider;
  private final List<Hit> hits = new ArrayList<>();
  private MergeModel model;
  private boolean syncing;

  /** A painted block button. */
  private record Hit(Canvas divider, Rectangle bounds, MergeChange change, String action, String tooltip) {
  }

  MergeColumns(Composite parent, boolean dark, Path file, IDocument yoursDocument, IDocument resultDocument, IDocument theirsDocument,
               String yoursTitle, String theirsTitle, Actions actions) {
    this.dark = dark;
    this.actions = actions;
    control = new Composite(parent, SWT.NONE);
    String[] texts = {yoursTitle, "Result", theirsTitle};
    for (int i = 0; i < 3; i++) {
      titles[i] = new CLabel(control, SWT.NONE);
      titles[i].setText(texts[i]);
      if (i != 1) {
        titles[i].setImage(GitbenchIcons.get(Icon.LOCKED));
      }
    }
    yours = TextViewers.create(control, dark, yoursDocument, false);
    leftDivider = new Canvas(control, SWT.DOUBLE_BUFFERED);
    result = TextViewers.create(control, dark, resultDocument, true);
    rightDivider = new Canvas(control, SWT.DOUBLE_BUFFERED);
    theirs = TextViewers.create(control, dark, theirsDocument, false);
    CdtHighlighting.configure(yours, yoursDocument, file);
    CdtHighlighting.configure(result, resultDocument, file);
    CdtHighlighting.configure(theirs, theirsDocument, file);
    control.setLayout(new ColumnsLayout());

    install(yours, ThreeSide.LEFT);
    install(result, ThreeSide.BASE);
    install(theirs, ThreeSide.RIGHT);
    for (Canvas divider : List.of(leftDivider, rightDivider)) {
      divider.addListener(SWT.Paint, e -> paintDivider(e, divider));
      divider.addListener(SWT.MouseDown, e -> clicked(e, divider));
      divider.addListener(SWT.MouseMove, e -> hovered(e, divider));
    }
  }

  Control control() {
    return control;
  }

  void setModel(MergeModel mergeModel) {
    model = mergeModel;
    refresh();
  }

  /** Repaints the highlighting and the bands after any change of the blocks. */
  void refresh() {
    for (SourceViewer viewer : List.of(yours, result, theirs)) {
      viewer.getTextWidget().redraw();
    }
    leftDivider.redraw();
    rightDivider.redraw();
  }

  /** The line of the caret in the Result. */
  int resultCaretLine() {
    StyledText text = result.getTextWidget();
    return text.getLineAtOffset(text.getCaretOffset());
  }

  /** Scrolls the block to the middle of the Result and puts the caret on its first line. */
  void reveal(MergeChange change) {
    StyledText text = result.getTextWidget();
    int line = Math.min(change.startLine(ThreeSide.BASE), Math.max(0, text.getLineCount() - 1));
    int visible = Math.max(1, text.getClientArea().height / Math.max(1, text.getLineHeight()));
    text.setTopIndex(Math.max(0, line - visible / 2));
    try {
      text.setCaretOffset(result.getDocument().getLineOffset(line));
    }
    catch (BadLocationException e) {
      text.setCaretOffset(text.getCharCount());
    }
    syncFrom(ThreeSide.BASE);
    text.setFocus();
    refresh();
  }

  void setFocus() {
    result.getTextWidget().setFocus();
  }

  private void install(SourceViewer viewer, ThreeSide side) {
    StyledText text = viewer.getTextWidget();
    text.addLineBackgroundListener(e -> {
      if (model == null) {
        return;
      }
      int line = text.getLineAtOffset(e.lineOffset);
      for (MergeChange change : model.changes()) {
        if (shown(change, side) && change.startLine(side) <= line && line < change.endLine(side)) {
          e.lineBackground = colorOf(change);
          return;
        }
      }
    });
    text.addListener(SWT.Paint, e -> paintInsertionLines(e.gc, text, side));
    viewer.addViewportListener(offset -> {
      syncFrom(side);
      leftDivider.redraw();
      rightDivider.redraw();
    });
    viewer.prependVerifyKeyListener(e -> {
      if (actions.key(e.keyCode, e.stateMask)) {
        e.doit = false;
      }
    });
    text.addListener(SWT.Resize, e -> refresh());
  }

  /** A block is painted on a side while it is unresolved and changes that side; the Result shows every unresolved block. */
  private static boolean shown(MergeChange change, ThreeSide side) {
    if (change.isResolved()) {
      return false;
    }
    return switch (side) {
      case LEFT -> change.isChange(Side.LEFT);
      case RIGHT -> change.isChange(Side.RIGHT);
      case BASE -> true;
    };
  }

  private Color colorOf(MergeChange change) {
    return change.isConflict() ? DiffColors.conflict(dark) : DiffColors.color(change.kind(), dark);
  }

  private void paintInsertionLines(GC gc, StyledText text, ThreeSide side) {
    if (model == null) {
      return;
    }
    int width = text.getClientArea().width;
    for (MergeChange change : model.changes()) {
      if (!shown(change, side) || change.startLine(side) != change.endLine(side)) {
        continue;
      }
      int y = DividerPainter.lineToY(text, change.startLine(side), text);
      gc.setForeground(colorOf(change));
      gc.drawLine(0, Math.max(0, y - 1), width, Math.max(0, y - 1));
    }
  }

  private void paintDivider(Event e, Canvas divider) {
    Rectangle area = divider.getClientArea();
    e.gc.setBackground(DiffColors.separators(dark));
    e.gc.fillRectangle(area);
    hits.removeIf(hit -> hit.divider() == divider);
    if (model == null) {
      return;
    }
    boolean left = divider == leftDivider;
    StyledText from = (left ? yours : result).getTextWidget();
    StyledText to = (left ? result : theirs).getTextWidget();
    ThreeSide fromSide = left ? ThreeSide.LEFT : ThreeSide.BASE;
    ThreeSide toSide = left ? ThreeSide.BASE : ThreeSide.RIGHT;
    Side side = left ? Side.LEFT : Side.RIGHT;
    for (MergeChange change : model.changes()) {
      if (change.isResolved() || !change.isChange(side)) {
        continue;
      }
      int start1 = DividerPainter.lineToY(from, change.startLine(fromSide), divider);
      int end1 = DividerPainter.lineToY(from, change.endLine(fromSide), divider);
      int start2 = DividerPainter.lineToY(to, change.startLine(toSide), divider);
      int end2 = DividerPainter.lineToY(to, change.endLine(toSide), divider);
      if (Math.max(end1, end2) < 0 && Math.max(start1, start2) < 0 || Math.min(start1, start2) > area.height) {
        continue;
      }
      DividerPainter.paintPolygon(e.gc, area.width, start1, end1, start2, end2, colorOf(change));
      paintButtons(e.gc, divider, change, side, left ? start1 : start2, left ? start2 : start1, from.getLineHeight(), area.width);
    }
  }

  /** The buttons of a block: the arrow and the cross by the side column, the magic button by the Result. */
  private void paintButtons(GC gc, Canvas divider, MergeChange change, Side side, int sideY, int resultY, int lineHeight, int width) {
    int top = sideY + (lineHeight - ICON_SIZE) / 2;
    boolean left = side == Side.LEFT;
    if (!change.isResolved(side)) {
      boolean append = change.isConflict() && change.isOnesideAppliedConflict();
      Icon arrow = left ? (append ? Icon.DIFF_ARROW_RIGHT_DOWN : Icon.DIFF_ARROW_RIGHT)
                        : (append ? Icon.DIFF_ARROW_LEFT_DOWN : Icon.DIFF_ARROW_LEFT);
      int arrowX = left ? 1 : width - ICON_SIZE - 1;
      int crossX = left ? ICON_SIZE + 1 : width - 2 * ICON_SIZE - 1;
      button(gc, divider, arrow, arrowX, top, change, "replace", append ? "Append" : "Accept");
      button(gc, divider, Icon.DIFF_REMOVE, crossX, top, change, "ignore", "Ignore");
    }
    if (left && change.isConflict() && change.canResolveAutomatically()) {
      button(gc, divider, Icon.MAGIC_RESOLVE, width - ICON_SIZE - 1, resultY + (lineHeight - ICON_SIZE) / 2, change, "magic",
             "Resolve Automatically");
    }
  }

  private void button(GC gc, Canvas divider, Icon icon, int x, int y, MergeChange change, String action, String tooltip) {
    gc.drawImage(GitbenchIcons.get(icon), x, y);
    hits.add(new Hit(divider, new Rectangle(x, y, ICON_SIZE, ICON_SIZE), change, action, tooltip));
  }

  private Hit hitAt(Canvas divider, int x, int y) {
    for (Hit hit : hits) {
      if (hit.divider() == divider && hit.bounds().contains(x, y)) {
        return hit;
      }
    }
    return null;
  }

  private void clicked(Event e, Canvas divider) {
    Hit hit = e.button == 1 ? hitAt(divider, e.x, e.y) : null;
    if (hit == null) {
      return;
    }
    Side side = divider == leftDivider ? Side.LEFT : Side.RIGHT;
    switch (hit.action()) {
      case "replace" -> actions.replace(hit.change(), side);
      case "ignore" -> actions.ignore(hit.change(), side);
      default -> actions.magic(hit.change());
    }
  }

  private void hovered(Event e, Canvas divider) {
    Hit hit = hitAt(divider, e.x, e.y);
    divider.setCursor(hit != null ? divider.getDisplay().getSystemCursor(SWT.CURSOR_HAND) : null);
    String tooltip = hit != null ? hit.tooltip() : null;
    if (!Objects.equals(divider.getToolTipText(), tooltip)) {
      divider.setToolTipText(tooltip);
    }
  }

  /** Keeps the middle lines of the three columns together through the block boundaries, as IntelliJ SyncScrollSupport. */
  private void syncFrom(ThreeSide master) {
    if (syncing || model == null) {
      return;
    }
    syncing = true;
    try {
      List<DiffChange> yoursToResult = ranges(ThreeSide.LEFT);
      List<DiffChange> theirsToResult = ranges(ThreeSide.RIGHT);
      StyledText masterText = viewer(master).getTextWidget();
      int visible = Math.max(1, masterText.getClientArea().height / Math.max(1, masterText.getLineHeight()));
      int center = masterText.getTopIndex() + visible / 2;
      int resultLine = switch (master) {
        case LEFT -> SyncScroll.transfer(yoursToResult, Side.LEFT, center);
        case RIGHT -> SyncScroll.transfer(theirsToResult, Side.LEFT, center);
        case BASE -> center;
      };
      if (master != ThreeSide.BASE) {
        result.getTextWidget().setTopIndex(Math.max(0, resultLine - visible / 2));
      }
      if (master != ThreeSide.LEFT) {
        yours.getTextWidget().setTopIndex(Math.max(0, SyncScroll.transfer(yoursToResult, Side.RIGHT, resultLine) - visible / 2));
      }
      if (master != ThreeSide.RIGHT) {
        theirs.getTextWidget().setTopIndex(Math.max(0, SyncScroll.transfer(theirsToResult, Side.RIGHT, resultLine) - visible / 2));
      }
    }
    finally {
      syncing = false;
    }
  }

  /** The blocks as pairs of a side range (left) and its Result range (right), for {@link SyncScroll}. */
  private List<DiffChange> ranges(ThreeSide side) {
    return model.changes().stream()
      .map(c -> new DiffChange(c.startLine(side), c.endLine(side), c.startLine(ThreeSide.BASE), c.endLine(ThreeSide.BASE), c.kind(),
                               List.of(), List.of()))
      .toList();
  }

  private SourceViewer viewer(ThreeSide side) {
    return switch (side) {
      case LEFT -> yours;
      case BASE -> result;
      case RIGHT -> theirs;
    };
  }

  /** Titles in the first row; Yours, the left band, Result, the right band and Theirs in the second. */
  private final class ColumnsLayout extends Layout {
    @Override
    protected Point computeSize(Composite composite, int wHint, int hHint, boolean flushCache) {
      return new Point(wHint == SWT.DEFAULT ? 900 : wHint, hHint == SWT.DEFAULT ? 500 : hHint);
    }

    @Override
    protected void layout(Composite composite, boolean flushCache) {
      Rectangle area = composite.getClientArea();
      int titleHeight = titles[0].computeSize(SWT.DEFAULT, SWT.DEFAULT).y;
      int column = (area.width - 2 * DIVIDER_WIDTH) / 3;
      int[] x = {area.x, area.x + column + DIVIDER_WIDTH, area.x + 2 * (column + DIVIDER_WIDTH)};
      int lastWidth = area.width - 2 * (column + DIVIDER_WIDTH);
      int bodyY = area.y + titleHeight;
      int bodyHeight = Math.max(0, area.height - titleHeight);
      for (int i = 0; i < 3; i++) {
        titles[i].setBounds(x[i], area.y, i == 2 ? lastWidth : column, titleHeight);
      }
      yours.getControl().setBounds(x[0], bodyY, column, bodyHeight);
      leftDivider.setBounds(x[0] + column, bodyY, DIVIDER_WIDTH, bodyHeight);
      result.getControl().setBounds(x[1], bodyY, column, bodyHeight);
      rightDivider.setBounds(x[1] + column, bodyY, DIVIDER_WIDTH, bodyHeight);
      theirs.getControl().setBounds(x[2], bodyY, lastWidth, bodyHeight);
    }
  }
}
