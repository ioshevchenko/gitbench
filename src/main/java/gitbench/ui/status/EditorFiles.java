package gitbench.ui.status;

import org.eclipse.jface.text.IDocument;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IURIEditorInput;
import org.eclipse.ui.editors.text.IEncodingSupport;
import org.eclipse.ui.texteditor.ITextEditor;

import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Optional;

/** The file, the document and the charset of a text editor, for the ruler columns. */
public final class EditorFiles {
  private EditorFiles() {
  }

  /** The file on disk; empty for an editor input that is not a local file. */
  public static Optional<Path> file(IEditorInput input) {
    if (input instanceof IURIEditorInput uriInput) {
      URI uri = uriInput.getURI();
      if (uri != null && "file".equals(uri.getScheme())) {
        return Optional.of(Path.of(uri));
      }
    }
    return Optional.empty();
  }

  public static Optional<IDocument> document(ITextEditor editor) {
    if (editor.getDocumentProvider() == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(editor.getDocumentProvider().getDocument(editor.getEditorInput()));
  }

  /** The encoding the editor reads the file with; UTF-8 when it does not say. */
  public static Charset charset(ITextEditor editor) {
    IEncodingSupport encoding = editor.getAdapter(IEncodingSupport.class);
    String name = encoding == null ? null : encoding.getEncoding() != null ? encoding.getEncoding() : encoding.getDefaultEncoding();
    try {
      return name == null ? StandardCharsets.UTF_8 : Charset.forName(name);
    }
    catch (RuntimeException e) {
      return StandardCharsets.UTF_8;
    }
  }
}
