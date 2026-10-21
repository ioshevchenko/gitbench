package gitbench.ui.status;

import gitbench.diff.BlockKind;
import gitbench.diff.DiffChange;
import gitbench.diff.DiffModel;
import gitbench.diff.DiffText;
import gitbench.diff.HighlightPolicy;
import gitbench.diff.IgnorePolicy;
import gitbench.diff.InnerRange;
import gitbench.diff.Side;
import gitbench.status.LineStatus;
import gitbench.ui.GitbenchIcons;
import gitbench.ui.GitbenchIcons.Icon;
import gitbench.ui.Theme;
import gitbench.ui.diff.DiffColors;
import gitbench.ui.diff.DocumentEdits;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;

import java.util.List;

/**
 * The popup of a change marker, as the IntelliJ LineStatusMarkerPopupPanel: a toolbar with Previous Change, Next
 * Change, Rollback Lines, Show Diff for Lines and Copy, and below it the HEAD text of the block with the changed
 * words. Esc or a click elsewhere closes it.
 */
public final class ChangeMarkerPopup {
  /** The most lines of HEAD text the popup shows before it scrolls. */
  private static final int MAX_VISIBLE_LINES = 20;
  private static final int MAX_WIDTH = 800;

  /** What the popup asks of the editor. */
  public interface Actions {
    /** Opens the diff of the file at the block that starts at this line of the document. */
    void showDiff(int documentLine);

    /** Scrolls the editor to this line of the document. */
    void reveal(int documentLine);
  }

  private final Shell shell;
  private final IDocument document;
  private final LineStatusTracker tracker;
  private final Actions actions;
  /** The ranges when the popup opened; the navigation moves along them. */
  private final List<DiffChange> ranges;
  private final ToolBar toolBar;
  private final ToolItem previous;
  private final ToolItem next;
  private final ToolItem copy;
  private StyledText text;
  private int rangeIndex;
  private boolean activated;

  public ChangeMarkerPopup(Shell parent, IDocument document, LineStatusTracker tracker, int rangeIndex, Point location, Actions actions) {
    this.document = document;
    this.tracker = tracker;
    this.actions = actions;
    this.ranges = tracker.ranges();
    this.rangeIndex = rangeIndex;
    shell = new Shell(parent, SWT.ON_TOP | SWT.TOOL | SWT.BORDER);
    GridLayout layout = new GridLayout(1, false);
    layout.marginWidth = 0;
    layout.marginHeight = 0;
    layout.verticalSpacing = 0;
    shell.setLayout(layout);
    toolBar = new ToolBar(shell, SWT.FLAT | SWT.HORIZONTAL);
    previous = item(Icon.UP, "Previous Change", () -> move(-1));
    next = item(Icon.DOWN, "Next Change", () -> move(1));
    item(Icon.ROLLBACK, "Rollback Lines", this::rollback);
    item(Icon.DIFF, "Show Diff for Lines", () -> {
      close();
      actions.showDiff(range().startLine2());
    });
    copy = item(Icon.COPY, "Copy", this::copy);
    Listener escape = e -> {
      if (e.detail == SWT.TRAVERSE_ESCAPE) {
        e.doit = false;
        close();
      }
    };
    shell.addListener(SWT.Traverse, escape);
    toolBar.addListener(SWT.Traverse, escape);
    shell.addListener(SWT.Activate, e -> activated = true);
    // A popup that never got the focus must not close at once; one that had it closes when the user clicks elsewhere.
    shell.addListener(SWT.Deactivate, e -> {
      if (activated) {
        shell.getDisplay().asyncExec(this::close);
      }
    });
    show();
    shell.setLocation(location);
    shell.open();
  }

  Shell shell() {
    return shell;
  }

  ToolBar toolBar() {
    return toolBar;
  }

  /** The HEAD text of the block; null for added lines, which have none. */
  StyledText text() {
    return text;
  }

  int rangeIndex() {
    return rangeIndex;
  }

  private ToolItem item(Icon icon, String tip, Runnable action) {
    ToolItem item = new ToolItem(toolBar, SWT.PUSH);
    item.setImage(GitbenchIcons.get(icon));
    item.setToolTipText(tip);
    item.addListener(SWT.Selection, e -> action.run());
    return item;
  }

  private DiffChange range() {
    return ranges.get(rangeIndex);
  }

  /** Fills the popup for the current range. */
  private void show() {
    previous.setEnabled(rangeIndex > 0);
    next.setEnabled(rangeIndex < ranges.size() - 1);
    DiffChange range = range();
    copy.setEnabled(range.kind() != BlockKind.INSERTED);
    if (text != null) {
      text.dispose();
      text = null;
    }
    if (range.kind() != BlockKind.INSERTED) {
      text = new StyledText(shell, SWT.READ_ONLY | SWT.H_SCROLL | SWT.V_SCROLL);
      text.setFont(JFaceResources.getTextFont());
      text.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
      text.addListener(SWT.Traverse, e -> {
        if (e.detail == SWT.TRAVERSE_ESCAPE) {
          e.doit = false;
          close();
        }
      });
      String head = headText(range);
      text.setText(head);
      paintWords(range, head);
    }
    Point size = shell.computeSize(SWT.DEFAULT, SWT.DEFAULT);
    int lineHeight = text == null ? 0 : text.getLineHeight();
    int maxHeight = toolBar.computeSize(SWT.DEFAULT, SWT.DEFAULT).y + lineHeight * MAX_VISIBLE_LINES;
    shell.setSize(Math.min(size.x, MAX_WIDTH), Math.min(size.y, Math.max(maxHeight, toolBar.computeSize(SWT.DEFAULT, SWT.DEFAULT).y)));
    shell.layout(true, true);
  }

  /** The HEAD lines of the range, without the last line separator. */
  private String headText(DiffChange range) {
    DiffText base = DiffText.of(tracker.base().orElse(""));
    if (range.startLine1() == range.endLine1()) {
      return "";
    }
    return base.text().substring(base.lineStart(range.startLine1()), base.lineEnd(range.endLine1() - 1));
  }

  /** The document lines of the range, without the last line separator. */
  private String documentText(DiffChange range) {
    if (range.startLine2() == range.endLine2()) {
      return "";
    }
    DiffText current = DiffText.of(document.get());
    return current.text().substring(current.lineStart(range.startLine2()), current.lineEnd(range.endLine2() - 1));
  }

  /** As a diff block: lighter lines with the changed words in the full color, or full lines without words. */
  private void paintWords(DiffChange range, String head) {
    boolean dark = Theme.isDark();
    Color full = DiffColors.color(range.kind(), dark);
    List<InnerRange> words = List.of();
    if (range.kind() == BlockKind.MODIFIED) {
      DiffText headText = DiffText.of(head);
      words = DiffModel.compute(headText, DiffText.of(documentText(range)), IgnorePolicy.DEFAULT, HighlightPolicy.BY_WORD).stream()
        .flatMap(change -> change.inner(Side.LEFT).stream()).toList();
    }
    Color lines = words.isEmpty() ? full : DiffColors.ignoredColor(range.kind(), dark, text.getBackground());
    text.setLineBackground(0, text.getLineCount(), lines);
    for (InnerRange word : words) {
      if (word.endColumn() > word.startColumn() && word.line() < text.getLineCount()) {
        int offset = text.getOffsetAtLine(word.line()) + word.startColumn();
        StyleRange style = new StyleRange(offset, word.endColumn() - word.startColumn(), null, full);
        text.setStyleRange(style);
      }
    }
  }

  private void move(int delta) {
    int target = rangeIndex + delta;
    if (target < 0 || target >= ranges.size()) {
      return;
    }
    rangeIndex = target;
    show();
    actions.reveal(range().startLine2());
  }

  /** Puts the HEAD lines back with one document change, so one Undo in the editor takes it back. */
  private void rollback() {
    String base = tracker.base().orElse(null);
    if (base != null) {
      try {
        DocumentEdits.apply(document, LineStatus.rollback(base, document.get(), range()));
      }
      catch (BadLocationException e) {
        // The document changed after the ranges were computed; the next comparison shows the new ranges.
      }
    }
    close();
  }

  private void copy() {
    Clipboard clipboard = new Clipboard(shell.getDisplay());
    try {
      clipboard.setContents(new Object[]{headText(range()) + "\n"}, new Transfer[]{TextTransfer.getInstance()});
    }
    finally {
      clipboard.dispose();
    }
    close();
  }

  private void close() {
    if (!shell.isDisposed()) {
      shell.dispose();
    }
  }
}
