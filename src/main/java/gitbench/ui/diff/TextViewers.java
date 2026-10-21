package gitbench.ui.diff;

import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.TextPresentation;
import org.eclipse.jface.text.source.CompositeRuler;
import org.eclipse.jface.text.source.LineNumberRulerColumn;
import org.eclipse.jface.text.source.SourceViewer;
import org.eclipse.jface.text.source.SourceViewerConfiguration;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.widgets.Composite;

/** Creates the text viewers of the diff and repaints their word highlighting. */
final class TextViewers {
  private static final Color LIGHT_LINE_NUMBERS = new Color(0x99, 0x99, 0x99);
  private static final Color DARK_LINE_NUMBERS = new Color(0x60, 0x63, 0x66);

  private TextViewers() {
  }

  /** A source viewer with line numbers and the editor text font. {@link CdtHighlighting} adds the syntax colors. */
  static SourceViewer create(Composite parent, boolean dark, IDocument document, boolean editable) {
    CompositeRuler ruler = new CompositeRuler();
    LineNumberRulerColumn numbers = new LineNumberRulerColumn();
    numbers.setForeground(dark ? DARK_LINE_NUMBERS : LIGHT_LINE_NUMBERS);
    ruler.addDecorator(0, numbers);
    SourceViewer viewer = new SourceViewer(parent, ruler, null, false, SWT.H_SCROLL | SWT.V_SCROLL | SWT.MULTI);
    StyledText text = viewer.getTextWidget();
    text.setFont(JFaceResources.getTextFont());
    numbers.setFont(JFaceResources.getTextFont());
    // The default configuration brings the undo manager that Undo and Redo of the editor use.
    viewer.configure(new SourceViewerConfiguration());
    viewer.setDocument(document != null ? document : new Document());
    followEditorBackground(viewer, ruler, numbers);
    viewer.setEditable(editable);
    return viewer;
  }

  /**
   * Paints the ruler in the background of the text. The line-number column is white by default, because SWT system
   * colors do not follow the dark theme.
   */
  static void followEditorBackground(SourceViewer viewer, CompositeRuler ruler, LineNumberRulerColumn... columns) {
    gitbench.ui.Theme.followBackground(viewer.getTextWidget(), background -> {
      for (LineNumberRulerColumn column : columns) {
        column.setBackground(background);
      }
      org.eclipse.swt.widgets.Control rulerControl = ruler.getControl();
      if (rulerControl != null && !rulerControl.isDisposed()) {
        rulerControl.setBackground(background);
        if (rulerControl instanceof Composite composite) {
          for (org.eclipse.swt.widgets.Control child : composite.getChildren()) {
            child.setBackground(background);
          }
        }
        rulerControl.redraw();
      }
    });
  }

  /**
   * Repaints the word highlighting. With a presentation reconciler the reconciler repaints and the presentation
   * listeners add the words; without one, an empty presentation goes through the listeners here.
   */
  static void refreshPresentation(SourceViewer viewer, boolean hasReconciler) {
    if (viewer.getTextWidget().isDisposed()) {
      return;
    }
    if (hasReconciler) {
      viewer.invalidateTextPresentation();
      return;
    }
    int length = viewer.getDocument().getLength();
    TextPresentation presentation = new TextPresentation(new Region(0, length), 64);
    presentation.setDefaultStyleRange(new StyleRange(0, length, null, null));
    viewer.changeTextPresentation(presentation, true);
  }
}
