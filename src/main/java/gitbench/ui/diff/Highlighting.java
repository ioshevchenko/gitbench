package gitbench.ui.diff;

import gitbench.ui.Activator;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.source.SourceViewer;
import org.osgi.framework.Bundle;
import org.osgi.framework.wiring.BundleWiring;

import java.nio.file.Path;

/**
 * Syntax colors in the diff and merge columns: CDT for C, C++ and assembler, then a Generic Editor highlighter for
 * the content type of the file, then plain text. Eclipse has no single API for "color this file as its editor
 * does": each editor keeps its configuration to itself.
 */
public final class Highlighting {
  private Highlighting() {
  }

  /** Configures the viewer for the file. Returns true when a presentation reconciler now paints the text. */
  public static boolean configure(SourceViewer viewer, IDocument document, Path file) {
    try {
      SyntaxKind kind = SyntaxKind.of(file);
      if (kind != SyntaxKind.OTHER) {
        ClassLoader cdt = cdtClassLoader();
        if (cdt != null) {
          CdtSupport.configure(cdt, viewer, document, file, kind);
          return true;
        }
      }
      return GenericEditorHighlighting.configure(viewer, document, file);
    }
    catch (ReflectiveOperationException | LinkageError | RuntimeException | org.eclipse.core.runtime.CoreException e) {
      // Outside the running platform, as in the UI tests, there is no registry and no log.
      if (Activator.getDefault() != null) {
        Activator.getDefault().getLog().warn("Syntax highlighting is not available in the diff: " + e);
      }
      return false;
    }
  }

  /** The class loader of the CDT UI bundle, or null without CDT. */
  private static ClassLoader cdtClassLoader() {
    Bundle bundle = Platform.getBundle("org.eclipse.cdt.ui");
    if (bundle == null) {
      return null;
    }
    BundleWiring wiring = bundle.adapt(BundleWiring.class);
    if (wiring == null) {
      try {
        bundle.start(Bundle.START_TRANSIENT);
      }
      catch (org.osgi.framework.BundleException e) {
        return null;
      }
      wiring = bundle.adapt(BundleWiring.class);
    }
    return wiring == null ? null : wiring.getClassLoader();
  }
}
