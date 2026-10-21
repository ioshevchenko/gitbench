package gitbench.ui.diff;

import gitbench.diff.BlockKind;
import gitbench.diff.DiffChange;
import gitbench.diff.Side;
import gitbench.ui.GitbenchIcons;
import gitbench.ui.GitbenchIcons.Icon;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextOperationTarget;
import org.eclipse.jface.text.source.CompositeRuler;
import org.eclipse.jface.text.source.LineNumberRulerColumn;
import org.eclipse.jface.text.source.SourceViewer;
import org.eclipse.jface.text.source.SourceViewerConfiguration;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CLabel;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;

/**
 * The viewer for a file that exists on one side only, as IntelliJ SimpleOnesideDiffViewer: one column over the whole
 * width, every line in the inserted color for an added file or in the deleted color for a deleted file.
 */
final class OnesideViewer implements DiffPane {
  private final boolean dark;
  private final Composite control;
  private final CLabel title;
  private final SourceViewer viewer;
  private DiffContent content;

  OnesideViewer(Composite parent, Host host, DiffContent initial, boolean dark) {
    this.dark = dark;
    control = new Composite(parent, SWT.NONE);
    GridLayout layout = new GridLayout(1, false);
    layout.marginWidth = 0;
    layout.marginHeight = 0;
    layout.verticalSpacing = 0;
    control.setLayout(layout);
    title = new CLabel(control, SWT.NONE);
    title.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

    boolean added = isAdded(initial);
    IDocument document = added ? initial.right() : initial.left();
    CompositeRuler ruler = new CompositeRuler();
    LineNumberRulerColumn numbers = new LineNumberRulerColumn();
    numbers.setFont(JFaceResources.getTextFont());
    ruler.addDecorator(0, numbers);
    viewer = new SourceViewer(control, ruler, null, false, SWT.H_SCROLL | SWT.V_SCROLL | SWT.MULTI);
    viewer.getControl().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
    viewer.configure(new SourceViewerConfiguration());
    viewer.setDocument(document);
    viewer.setEditable(added && initial.rightEditable());
    StyledText text = viewer.getTextWidget();
    text.setFont(JFaceResources.getTextFont());
    TextViewers.followEditorBackground(viewer, ruler, numbers);
    CdtHighlighting.configure(viewer, document, initial.file());
    text.addLineBackgroundListener(e -> e.lineBackground = DiffColors.color(added ? BlockKind.INSERTED : BlockKind.DELETED, dark));
    viewer.prependVerifyKeyListener(e -> {
      if (host.handleKey(e.keyCode, e.stateMask)) {
        e.doit = false;
      }
    });
    setContent(initial);
  }

  /** The left side is empty for an added or unversioned file. */
  private static boolean isAdded(DiffContent content) {
    return content.emptySide() == Side.LEFT;
  }

  @Override
  public Control control() {
    return control;
  }

  @Override
  public void setContent(DiffContent newContent) {
    content = newContent;
    boolean added = isAdded(newContent);
    title.setText(added ? newContent.rightTitle() : newContent.leftTitle());
    title.setImage(added && newContent.rightEditable() ? null : GitbenchIcons.get(Icon.LOCKED));
    control.layout(true);
  }

  @Override
  public boolean navigate(boolean next) {
    return false;
  }

  @Override
  public void reveal(DiffChange change) {
  }

  @Override
  public DiffChange changeAtCaret() {
    return null;
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
}
