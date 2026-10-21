package gitbench.ui.diff;

import org.eclipse.core.expressions.EvaluationContext;
import org.eclipse.core.expressions.EvaluationResult;
import org.eclipse.core.expressions.ExpressionConverter;
import org.eclipse.core.expressions.IEvaluationContext;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtensionRegistry;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.content.IContentType;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.presentation.IPresentationReconciler;
import org.eclipse.jface.text.source.SourceViewer;

import java.nio.file.Path;

/**
 * Colors from the plugins that give highlighting to the Eclipse Generic Editor: TM4E with its TextMate grammars,
 * language server plugins, and others. They register a presentation reconciler for a content type at the extension
 * point below; the diff takes the one for the content type of the file, as the Generic Editor does.
 */
final class GenericEditorHighlighting {
  static final String EXTENSION_POINT = "org.eclipse.ui.genericeditor.presentationReconcilers";

  private GenericEditorHighlighting() {
  }

  /** Installs the reconciler of the most specific content type of the file. Returns false when there is none. */
  static boolean configure(SourceViewer viewer, IDocument document, Path file) throws CoreException {
    IExtensionRegistry registry = Platform.getExtensionRegistry();
    if (registry == null) {
      return false;
    }
    IConfigurationElement[] elements = registry.getConfigurationElementsFor(EXTENSION_POINT);
    for (IContentType type : Platform.getContentTypeManager().findContentTypesFor(file.getFileName().toString())) {
      IConfigurationElement best = null;
      int bestDepth = -1;
      for (IConfigurationElement element : elements) {
        IContentType registered = Platform.getContentTypeManager().getContentType(element.getAttribute("contentType"));
        if (registered != null && type.isKindOf(registered) && depth(registered) > bestDepth && enabled(element, viewer, document)) {
          best = element;
          bestDepth = depth(registered);
        }
      }
      if (best != null) {
        IPresentationReconciler reconciler = (IPresentationReconciler)best.createExecutableExtension("class");
        reconciler.install(viewer);
        return true;
      }
    }
    return false;
  }

  /** The number of base types above the content type: a deeper type is more specific. */
  private static int depth(IContentType type) {
    int depth = 0;
    for (IContentType base = type.getBaseType(); base != null; base = base.getBaseType()) {
      depth++;
    }
    return depth;
  }

  /** Evaluates enabledWhen with the variables the Generic Editor gives; this window has no editor and no input. */
  private static boolean enabled(IConfigurationElement element, SourceViewer viewer, IDocument document) {
    IConfigurationElement[] enabledWhen = element.getChildren("enabledWhen");
    if (enabledWhen.length == 0) {
      return true;
    }
    try {
      EvaluationContext context = new EvaluationContext(null, new Object());
      context.setAllowPluginActivation(true);
      context.addVariable("viewer", viewer);
      context.addVariable("document", document);
      context.addVariable("editor", IEvaluationContext.UNDEFINED_VARIABLE);
      context.addVariable("editorInput", IEvaluationContext.UNDEFINED_VARIABLE);
      IConfigurationElement[] expression = enabledWhen[0].getChildren();
      return expression.length > 0
             && ExpressionConverter.getDefault().perform(expression[0]).evaluate(context) == EvaluationResult.TRUE;
    }
    catch (CoreException e) {
      return false;
    }
  }
}
