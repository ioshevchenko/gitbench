package gitbench.ui.diff;

import org.eclipse.cdt.ui.CDTUITools;
import org.eclipse.cdt.ui.CUIPlugin;
import org.eclipse.cdt.ui.text.CSourceViewerConfiguration;
import org.eclipse.cdt.ui.text.ICPartitions;
import org.eclipse.core.filebuffers.LocationKind;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentExtension3;
import org.eclipse.jface.text.source.SourceViewer;

import java.nio.file.Path;

/** The CDT calls of {@link CdtHighlighting}, done the same way as the CDT compare viewer CMergeViewer. */
final class CdtSupport {
  private CdtSupport() {
  }

  static void configure(SourceViewer viewer, IDocument document, Path file) {
    // The file buffer of a workspace C file has the C partitioning already; a revision document gets it here.
    if (!(document instanceof IDocumentExtension3 extension) || extension.getDocumentPartitioner(ICPartitions.C_PARTITIONING) == null) {
      CDTUITools.setupCDocument(document, IPath.fromOSString(file.toString()), LocationKind.NORMALIZE);
    }
    CSourceViewerConfiguration configuration = new CSourceViewerConfiguration(
      CDTUITools.getColorManager(), CUIPlugin.getDefault().getCombinedPreferenceStore(), null, ICPartitions.C_PARTITIONING);
    viewer.unconfigure();
    viewer.configure(configuration);
    viewer.invalidateTextPresentation();
  }
}
