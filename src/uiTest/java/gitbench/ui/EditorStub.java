package gitbench.ui;

import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.source.CompositeRuler;
import org.eclipse.jface.text.source.SourceViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorSite;
import org.eclipse.ui.IURIEditorInput;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.texteditor.IDocumentProvider;
import org.eclipse.ui.texteditor.ITextEditor;

import java.lang.reflect.Proxy;
import java.nio.file.Path;

/**
 * A text editor for the tests of the ruler columns, without a workbench: a real source viewer with a composite
 * ruler, and an {@link ITextEditor} that gives the file and the document set here. Other editor calls do nothing.
 */
public final class EditorStub {
  public final CompositeRuler ruler = new CompositeRuler();
  public final SourceViewer viewer;
  public final ITextEditor editor;
  private Path file;
  private IDocument document;

  public EditorStub(Composite parent, Path file, IDocument document) {
    viewer = new SourceViewer(parent, ruler, SWT.V_SCROLL | SWT.H_SCROLL);
    show(file, document);
    IWorkbenchPage page = stub(IWorkbenchPage.class);
    IEditorSite site = (IEditorSite)Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{IEditorSite.class},
                                                           (proxy, method, args) -> method.getName().equals("getPage") ? page : nothing(method));
    IDocumentProvider provider = (IDocumentProvider)Proxy.newProxyInstance(
      getClass().getClassLoader(), new Class<?>[]{IDocumentProvider.class},
      (proxy, method, args) -> method.getName().equals("getDocument") ? this.document : nothing(method));
    editor = (ITextEditor)Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{ITextEditor.class}, (proxy, method, args) -> {
      switch (method.getName()) {
        case "getEditorInput":
          return input(this.file);
        case "getDocumentProvider":
          return provider;
        case "getSite":
        case "getEditorSite":
          return site;
        case "equals":
          return proxy == args[0];
        case "hashCode":
          return System.identityHashCode(proxy);
        default:
          return nothing(method);
      }
    });
  }

  /** Changes the input, as a reused editor or Save As does: a new file and a new document. */
  public void show(Path newFile, IDocument newDocument) {
    file = newFile;
    document = newDocument;
    viewer.setDocument(newDocument);
  }

  private IEditorInput input(Path path) {
    return (IEditorInput)Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{IURIEditorInput.class},
                                                (proxy, method, args) -> method.getName().equals("getURI") ? path.toUri() : nothing(method));
  }

  @SuppressWarnings("unchecked")
  private static <T> T stub(Class<T> type) {
    return (T)Proxy.newProxyInstance(EditorStub.class.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> nothing(method));
  }

  private static Object nothing(java.lang.reflect.Method method) {
    Class<?> type = method.getReturnType();
    if (type == boolean.class) {
      return false;
    }
    if (type == int.class || type == long.class || type == short.class || type == byte.class) {
      return type == long.class ? 0L : 0;
    }
    return null;
  }
}
