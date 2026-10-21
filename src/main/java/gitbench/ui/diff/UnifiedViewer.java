package gitbench.ui.diff;

import gitbench.diff.BlockKind;
import gitbench.diff.DiffChange;
import gitbench.diff.DiffModel;
import gitbench.diff.InnerRange;
import gitbench.diff.Side;
import gitbench.diff.UnifiedLine;
import gitbench.diff.UnifiedModel;
import gitbench.ui.GitbenchIcons;
import gitbench.ui.GitbenchIcons.Icon;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextOperationTarget;
import org.eclipse.jface.text.TextPresentation;
import org.eclipse.jface.text.source.CompositeRuler;
import org.eclipse.jface.text.source.IAnnotationModel;
import org.eclipse.jface.text.source.IVerticalRulerColumn;
import org.eclipse.jface.text.source.SourceViewer;
import org.eclipse.jface.text.source.SourceViewerConfiguration;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CLabel;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * The Unified viewer, as IntelliJ UnifiedDiffViewer: one read-only column with the deleted lines of each block over
 * its inserted lines, and two line-number columns. The {@code >>} button on the first line of a block reverts or
 * accepts the block in the working file. Typing is not possible here; it is in the Side-by-side viewer.
 */
final class UnifiedViewer implements DiffPane {
  private static final Color LIGHT_LINE_NUMBERS = new Color(0x99, 0x99, 0x99);
  private static final Color DARK_LINE_NUMBERS = new Color(0x60, 0x63, 0x66);
  private static final int ARROW_COLUMN_WIDTH = 18;
  private static final int ARROW_SIZE = 14;

  private final Host host;
  private final boolean dark;
  private final boolean hasReconciler;
  private final Composite control;
  private final CLabel title;
  private final SourceViewer viewer;
  private final IDocument document = new Document();
  private final ArrowColumn arrows = new ArrowColumn();
  private DiffContent content;
  private UnifiedModel model;
  /** The blocks in unified lines, so the navigation of the Side-by-side viewer works here too. */
  private List<DiffChange> unifiedBlocks = List.of();

  UnifiedViewer(Composite parent, Host host, DiffContent initial, boolean dark) {
    this.host = host;
    this.dark = dark;
    control = new Composite(parent, SWT.NONE);
    GridLayout layout = new GridLayout(1, false);
    layout.marginWidth = 0;
    layout.marginHeight = 0;
    layout.verticalSpacing = 2;
    control.setLayout(layout);
    title = new CLabel(control, SWT.NONE);
    title.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

    CompositeRuler ruler = new CompositeRuler();
    UnifiedRuler leftNumbers = new UnifiedRuler(() -> model, true);
    UnifiedRuler rightNumbers = new UnifiedRuler(() -> model, false);
    for (UnifiedRuler numbers : List.of(leftNumbers, rightNumbers)) {
      numbers.setForeground(dark ? DARK_LINE_NUMBERS : LIGHT_LINE_NUMBERS);
      numbers.setFont(JFaceResources.getTextFont());
    }
    ruler.addDecorator(0, leftNumbers);
    ruler.addDecorator(1, rightNumbers);
    ruler.addDecorator(2, arrows);
    viewer = new SourceViewer(control, ruler, null, false, SWT.H_SCROLL | SWT.V_SCROLL | SWT.MULTI);
    viewer.getControl().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
    viewer.configure(new SourceViewerConfiguration());
    viewer.setDocument(document);
    viewer.setEditable(false);
    TextViewers.followEditorBackground(viewer, ruler, leftNumbers, rightNumbers);
    StyledText text = viewer.getTextWidget();
    text.setFont(JFaceResources.getTextFont());
    hasReconciler = Highlighting.configure(viewer, document, initial.file());

    text.addLineBackgroundListener(e -> {
      if (model == null) {
        return;
      }
      int line = text.getLineAtOffset(e.lineOffset);
      if (line >= model.lines().size()) {
        return;
      }
      UnifiedLine unified = model.lines().get(line);
      if (unified.kind() == UnifiedLine.Kind.CONTEXT) {
        return;
      }
      BlockKind kind = unified.kind() == UnifiedLine.Kind.DELETED ? BlockKind.DELETED : BlockKind.INSERTED;
      DiffChange change = content.changes().get(unified.changeIndex());
      e.lineBackground = change.hasInner() ? DiffColors.ignoredColor(kind, dark, text.getBackground()) : DiffColors.color(kind, dark);
    });
    viewer.addTextPresentationListener(this::addWords);
    // Before StyledText handles the key, so a consumed key such as Ctrl+Alt+R (AltGr on some layouts) types nothing.
    viewer.prependVerifyKeyListener(e -> {
      if (host.handleKey(e.keyCode, e.stateMask)) {
        e.doit = false;
      }
    });
    setContent(initial);
  }

  @Override
  public Control control() {
    return control;
  }

  @Override
  public void setContent(DiffContent newContent) {
    content = newContent;
    model = UnifiedModel.build(newContent.leftText(), newContent.rightText(), newContent.changes());
    List<DiffChange> blocks = new ArrayList<>();
    for (int i = 0; i < newContent.changes().size(); i++) {
      DiffChange change = newContent.changes().get(i);
      int start = model.lineOf(i);
      int end = start + (change.endLine1() - change.startLine1()) + (change.endLine2() - change.startLine2());
      blocks.add(new DiffChange(start, end, start, end, change.kind(), List.of(), List.of()));
    }
    unifiedBlocks = blocks;
    StyledText text = viewer.getTextWidget();
    int top = text.getTopIndex();
    if (!document.get().equals(model.text())) {
      document.set(model.text());
    }
    text.setTopIndex(top);
    title.setText(newContent.leftTitle() + " → " + newContent.rightTitle());
    title.setImage(newContent.rightEditable() ? null : GitbenchIcons.get(Icon.LOCKED));
    TextViewers.refreshPresentation(viewer, hasReconciler);
    text.redraw();
    arrows.redraw();
  }

  @Override
  public boolean navigate(boolean next) {
    StyledText text = viewer.getTextWidget();
    int line = text.getLineAtOffset(text.getCaretOffset());
    int index = next ? DiffModel.next(unifiedBlocks, line, Side.LEFT) : DiffModel.previous(unifiedBlocks, line, Side.LEFT);
    if (index < 0) {
      return false;
    }
    reveal(content.changes().get(index));
    return true;
  }

  @Override
  public void reveal(DiffChange change) {
    int index = content.changes().indexOf(change);
    if (index < 0) {
      return;
    }
    StyledText text = viewer.getTextWidget();
    int line = model.lineOf(index);
    int visible = Math.max(1, text.getClientArea().height / Math.max(1, text.getLineHeight()));
    text.setTopIndex(Math.max(0, line - visible / 2));
    try {
      text.setCaretOffset(document.getLineOffset(line));
    }
    catch (BadLocationException e) {
      text.setCaretOffset(text.getCharCount());
    }
    text.setFocus();
  }

  @Override
  public DiffChange changeAtCaret() {
    if (model == null) {
      return null;
    }
    StyledText text = viewer.getTextWidget();
    int line = text.getLineAtOffset(text.getCaretOffset());
    if (line >= model.lines().size() || model.lines().get(line).changeIndex() < 0) {
      return null;
    }
    return content.changes().get(model.lines().get(line).changeIndex());
  }

  @Override
  public ITextOperationTarget focusedTarget() {
    return viewer.getTextWidget().isFocusControl() ? viewer : null;
  }

  @Override
  public void setFocus() {
    viewer.getTextWidget().setFocus();
  }

  @Override
  public void dispose() {
    control.dispose();
  }

  /** Adds the background of the changed words: deleted words in the deleted color, inserted words in the inserted color. */
  private void addWords(TextPresentation presentation) {
    if (model == null) {
      return;
    }
    for (InnerRange range : model.innerRanges()) {
      if (range.endColumn() <= range.startColumn() || range.line() >= model.lines().size()) {
        continue;
      }
      BlockKind kind = model.lines().get(range.line()).kind() == UnifiedLine.Kind.DELETED ? BlockKind.DELETED : BlockKind.INSERTED;
      try {
        int offset = document.getLineOffset(range.line()) + range.startColumn();
        int length = Math.min(range.endColumn() - range.startColumn(), document.getLength() - offset);
        if (length > 0) {
          TextViewers.mergeWord(presentation, offset, length, DiffColors.color(kind, dark));
        }
      }
      catch (BadLocationException ignored) {
        // The model and the document are rebuilt together; a stale range is skipped.
      }
    }
  }

  /** The ruler column with the {@code >>} button on the first line of each block. */
  private final class ArrowColumn implements IVerticalRulerColumn {
    private Canvas canvas;

    @Override
    public Control createControl(CompositeRuler parentRuler, Composite parentControl) {
      canvas = new Canvas(parentControl, SWT.NONE);
      canvas.addListener(SWT.Paint, this::paint);
      canvas.addListener(SWT.MouseDown, this::clicked);
      canvas.addListener(SWT.MouseMove, e -> {
        DiffChange change = blockAt(e.y);
        canvas.setCursor(change != null ? canvas.getDisplay().getSystemCursor(SWT.CURSOR_HAND) : null);
        canvas.setToolTipText(change == null ? null : content.localChanges() ? "Revert" : "Accept");
      });
      return canvas;
    }

    private void paint(Event e) {
      Rectangle area = canvas.getClientArea();
      e.gc.setBackground(viewer.getTextWidget().getBackground());
      e.gc.fillRectangle(area);
      if (content == null || !content.rightEditable()) {
        return;
      }
      StyledText text = viewer.getTextWidget();
      for (int i = 0; i < unifiedBlocks.size(); i++) {
        int y = text.getLinePixel(unifiedBlocks.get(i).startLine1());
        if (y + text.getLineHeight() < 0 || y > area.height) {
          continue;
        }
        e.gc.drawImage(GitbenchIcons.get(Icon.DIFF_ARROW_RIGHT), 2, y + (text.getLineHeight() - ARROW_SIZE) / 2);
      }
    }

    private void clicked(Event e) {
      DiffChange change = e.button == 1 ? blockAt(e.y) : null;
      if (change != null) {
        host.apply(change, false);
      }
    }

    private DiffChange blockAt(int y) {
      if (content == null || !content.rightEditable()) {
        return null;
      }
      StyledText text = viewer.getTextWidget();
      for (int i = 0; i < unifiedBlocks.size(); i++) {
        int top = text.getLinePixel(unifiedBlocks.get(i).startLine1());
        if (y >= top && y < top + text.getLineHeight()) {
          return content.changes().get(i);
        }
      }
      return null;
    }

    @Override
    public Control getControl() {
      return canvas;
    }

    @Override
    public int getWidth() {
      return ARROW_COLUMN_WIDTH;
    }

    @Override
    public void redraw() {
      if (canvas != null && !canvas.isDisposed()) {
        canvas.redraw();
      }
    }

    @Override
    public void setModel(IAnnotationModel annotationModel) {
    }

    @Override
    public void setFont(Font font) {
    }
  }
}
