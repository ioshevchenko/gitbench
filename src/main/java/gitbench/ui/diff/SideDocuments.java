package gitbench.ui.diff;

import gitbench.diff.DiffText;
import gitbench.diff.LoadedContent;
import org.eclipse.core.filebuffers.FileBuffers;
import org.eclipse.core.filebuffers.ITextFileBuffer;
import org.eclipse.core.filebuffers.ITextFileBufferManager;
import org.eclipse.core.filebuffers.LocationKind;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;

import java.nio.charset.Charset;
import java.nio.file.Path;

/**
 * The documents of a diff. A revision gets its own read-only document with LF line separators. The working file is
 * the shared file buffer, the same document as in an open C editor: edits, the dirty flag and Save are common.
 */
final class SideDocuments {
  private static final ITextFileBufferManager MANAGER = FileBuffers.getTextFileBufferManager();

  private SideDocuments() {
  }

  static IDocument revision(LoadedContent content) {
    return new Document(DiffText.of(content.text()).text());
  }

  /** Connects to the file buffer of the working file. Call {@link #disconnect(Path)} when the diff closes. */
  static ITextFileBuffer connect(Path file) throws CoreException {
    IPath location = IPath.fromOSString(file.toString());
    MANAGER.connect(location, LocationKind.NORMALIZE, null);
    return MANAGER.getTextFileBuffer(location, LocationKind.NORMALIZE);
  }

  static void disconnect(Path file) {
    try {
      MANAGER.disconnect(IPath.fromOSString(file.toString()), LocationKind.NORMALIZE, null);
    }
    catch (CoreException e) {
      gitbench.ui.Activator.getDefault().getLog().log(e.getStatus());
    }
  }

  static ITextFileBufferManager manager() {
    return MANAGER;
  }

  /** The encoding of the file in the workspace, or the workspace default for a file outside the projects. */
  static Charset charsetOf(Path file) {
    try {
      IFile[] files = ResourcesPlugin.getWorkspace().getRoot().findFilesForLocationURI(file.toUri());
      String name = files.length > 0 ? files[0].getCharset() : ResourcesPlugin.getEncoding();
      return Charset.forName(name);
    }
    catch (CoreException | IllegalArgumentException e) {
      return Charset.defaultCharset();
    }
  }
}
