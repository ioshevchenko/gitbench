package gitbench.ui.merge;

import gitbench.merge.ResultText;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentListener;

/**
 * The Result column over its text document. Every change, from the model or from typing, passes the document
 * listener, which moves the blocks before the change as the IntelliJ MergeModelBase document listener.
 */
final class DocumentResultText extends ResultText {
  private final IDocument document;

  DocumentResultText(IDocument document, Runnable afterChange) {
    this.document = document;
    document.addDocumentListener(new IDocumentListener() {
      @Override
      public void documentAboutToBeChanged(DocumentEvent event) {
        beforeChange(event.getOffset(), event.getLength(), event.getText() == null ? "" : event.getText());
      }

      @Override
      public void documentChanged(DocumentEvent event) {
        afterChange.run();
      }
    });
  }

  @Override
  public String text() {
    return document.get();
  }

  @Override
  protected void doReplace(int offset, int length, String newText) {
    try {
      document.replace(offset, length, newText);
    }
    catch (BadLocationException e) {
      throw new IllegalStateException("The merge result changed under the block", e);
    }
  }
}
