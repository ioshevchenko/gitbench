package gitbench.ui.diff;

import org.eclipse.core.runtime.Platform;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.source.SourceViewer;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

/**
 * C and C++ syntax highlighting from CDT in the diff columns. CDT is an optional dependency: without it the diff has
 * no syntax colors. Only this class touches {@link CdtSupport}, so the CDT classes load only when CDT is installed.
 */
public final class CdtHighlighting {
  /** The CDT C and C++ source extensions. Assembler files use another partitioning and stay plain. */
  private static final Set<String> EXTENSIONS = Set.of("c", "h", "cpp", "hpp", "cc", "cxx", "hh", "hxx", "c++", "h++", "inl");

  private CdtHighlighting() {
  }

  /** Configures the viewer for the file. Returns true when a presentation reconciler now paints the text. */
  public static boolean configure(SourceViewer viewer, IDocument document, Path file) {
    if (!isCSource(file) || Platform.getBundle("org.eclipse.cdt.ui") == null) {
      return false;
    }
    try {
      CdtSupport.configure(viewer, document, file);
      return true;
    }
    catch (LinkageError | RuntimeException e) {
      gitbench.ui.Activator.getDefault().getLog().warn("C highlighting is not available in the diff: " + e);
      return false;
    }
  }

  static boolean isCSource(Path file) {
    String name = file.getFileName().toString();
    int dot = name.lastIndexOf('.');
    return dot >= 0 && EXTENSIONS.contains(name.substring(dot + 1).toLowerCase(Locale.ROOT));
  }
}
