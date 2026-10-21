package gitbench.ui.diff;

import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.source.SourceViewer;

/** C and C++ syntax highlighting from CDT in the diff columns. */
final class CdtHighlighting {
  private CdtHighlighting() {
  }

  /** Configures the viewer for the file. Returns true when a presentation reconciler now paints the text. */
  static boolean configure(SourceViewer viewer, IDocument document, String fileName) {
    return false;
  }
}
