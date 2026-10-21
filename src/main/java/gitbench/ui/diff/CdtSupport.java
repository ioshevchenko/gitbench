package gitbench.ui.diff;

import org.eclipse.core.filebuffers.LocationKind;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentExtension3;
import org.eclipse.jface.text.source.SourceViewer;
import org.eclipse.jface.text.source.SourceViewerConfiguration;
import org.eclipse.ui.texteditor.ITextEditor;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.file.Path;

/**
 * C, C++ and assembler colors from CDT, as the CDT compare viewer CMergeViewer sets them up. CDT is not on Maven
 * Central, so the plugin does not compile against it: {@link CdtApi} finds the public CDT API by name in the CDT
 * bundle at run time. Without CDT, or with a CDT that changed this API, the diff shows plain text.
 */
final class CdtSupport {
  /** ICPartitions.C_PARTITIONING: CDT puts both the C and the assembler partitions under this name. */
  static final String C_PARTITIONING = "___c_partitioning";

  private CdtSupport() {
  }

  /**
   * The CDT methods and constructors that the highlighting calls. Resolving them all at once tells early whether
   * this CDT has the expected API; a CI check resolves them against real CDT jars.
   */
  record CdtApi(Method getColorManager, Method getPlugin, Method getCombinedPreferenceStore, Method setupCDocument,
                Method setupAsmDocument, Constructor<?> cConfiguration, Constructor<?> asmConfiguration) {
    static CdtApi resolve(ClassLoader cdt) throws ReflectiveOperationException {
      Class<?> tools = cdt.loadClass("org.eclipse.cdt.ui.CDTUITools");
      Class<?> plugin = cdt.loadClass("org.eclipse.cdt.ui.CUIPlugin");
      Class<?> colorManager = cdt.loadClass("org.eclipse.cdt.ui.text.IColorManager");
      Class<?> cConfiguration = cdt.loadClass("org.eclipse.cdt.ui.text.CSourceViewerConfiguration");
      Class<?> asmConfiguration = cdt.loadClass("org.eclipse.cdt.ui.text.AsmSourceViewerConfiguration");
      Class<?>[] configurationParameters = {colorManager, IPreferenceStore.class, ITextEditor.class, String.class};
      return new CdtApi(tools.getMethod("getColorManager"),
                        plugin.getMethod("getDefault"),
                        plugin.getMethod("getCombinedPreferenceStore"),
                        tools.getMethod("setupCDocument", IDocument.class, IPath.class, LocationKind.class),
                        tools.getMethod("setupAsmDocument", IDocument.class),
                        cConfiguration.getConstructor(configurationParameters),
                        asmConfiguration.getConstructor(configurationParameters));
    }
  }

  /** Sets up the document partitions and the viewer configuration of CDT for a C or an assembler file. */
  static void configure(ClassLoader cdt, SourceViewer viewer, IDocument document, Path file, SyntaxKind kind)
    throws ReflectiveOperationException {
    CdtApi api = CdtApi.resolve(cdt);
    // The file buffer of a workspace C file has the C partitioning already; a revision document gets it here.
    if (!(document instanceof IDocumentExtension3 extension) || extension.getDocumentPartitioner(C_PARTITIONING) == null) {
      if (kind == SyntaxKind.ASSEMBLER) {
        api.setupAsmDocument().invoke(null, document);
      }
      else {
        api.setupCDocument().invoke(null, document, IPath.fromOSString(file.toString()), LocationKind.NORMALIZE);
      }
    }
    Object colors = api.getColorManager().invoke(null);
    Object store = api.getCombinedPreferenceStore().invoke(api.getPlugin().invoke(null));
    Constructor<?> constructor = kind == SyntaxKind.ASSEMBLER ? api.asmConfiguration() : api.cConfiguration();
    SourceViewerConfiguration configuration = (SourceViewerConfiguration)constructor.newInstance(colors, store, null, C_PARTITIONING);
    viewer.unconfigure();
    viewer.configure(configuration);
    viewer.invalidateTextPresentation();
  }
}
