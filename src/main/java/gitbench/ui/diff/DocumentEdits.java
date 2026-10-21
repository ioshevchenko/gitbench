package gitbench.ui.diff;

import gitbench.diff.Edit;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.TextUtilities;

/** Applies a diff edit to a text document that may use CRLF. */
public final class DocumentEdits {
  private DocumentEdits() {
  }

  /**
   * The edit is in lines and columns of the LF text; a column is the same in a CRLF line, because the CR is after the
   * line content. The '\n' of the edit text becomes the line separator of the document.
   */
  public static void apply(IDocument document, Edit edit) throws BadLocationException {
    int start = document.getLineOffset(edit.startLine()) + edit.startColumn();
    int end = document.getLineOffset(edit.endLine()) + edit.endColumn();
    String delimiter = TextUtilities.getDefaultLineDelimiter(document);
    document.replace(start, end - start, edit.text().replace("\n", delimiter));
  }
}
