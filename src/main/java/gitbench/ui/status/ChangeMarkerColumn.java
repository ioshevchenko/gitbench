package gitbench.ui.status;

import gitbench.git.GitException;
import gitbench.git.GitRepository;
import gitbench.git.GitRunner;
import gitbench.git.RepositoryLocator;
import gitbench.status.LineStatusBase;
import gitbench.ui.Activator;
import gitbench.ui.RepositoryEvents;
import gitbench.ui.Theme;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextListener;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.IViewportListener;
import org.eclipse.jface.text.JFaceTextUtil;
import org.eclipse.jface.text.source.CompositeRuler;
import org.eclipse.jface.text.source.IAnnotationModel;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IPartListener2;
import org.eclipse.ui.IURIEditorInput;
import org.eclipse.ui.IWorkbenchPartReference;
import org.eclipse.ui.editors.text.IEncodingSupport;
import org.eclipse.ui.texteditor.ITextEditor;
import org.eclipse.ui.texteditor.rulers.AbstractContributedRulerColumn;

import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.function.Consumer;

/**
 * The ruler column with the change markers of a text editor against HEAD, as the IntelliJ editor gutter. Eclipse
 * makes one column per editor through the extension point {@code org.eclipse.ui.workbench.texteditor.rulerColumns}.
 */
public final class ChangeMarkerColumn extends AbstractContributedRulerColumn {
  /** The column of each open editor, for the Next Change, Previous Change and Rollback Lines commands; UI thread only. */
  private static final Map<ITextEditor, ChangeMarkerColumn> COLUMNS = new WeakHashMap<>();

  private Canvas canvas;
  private ITextViewer viewer;
  private StyledText text;
  private LineStatusTracker tracker;
  /** Found on the tracker thread; read by the repository listener. */
  private volatile LineStatusBase.Key key;
  private int hovered = -1;
  private IPartListener2 activationListener;
  private final Consumer<GitRepository> repositoryListener = repository -> {
    LineStatusBase.Key current = key;
    if (current != null && current.repository().equals(repository) && canvas != null && !canvas.isDisposed()) {
      canvas.getDisplay().asyncExec(() -> {
        if (tracker != null) {
          tracker.reloadBase();
        }
      });
    }
  };
  private final IViewportListener viewportListener = verticalOffset -> redraw();
  private final ITextListener textListener = event -> redraw();

  public static Optional<ChangeMarkerColumn> of(ITextEditor editor) {
    return Optional.ofNullable(COLUMNS.get(editor));
  }

  @Override
  public Control createControl(CompositeRuler parentRuler, Composite parentControl) {
    viewer = parentRuler.getTextViewer();
    text = viewer.getTextWidget();
    canvas = new Canvas(parentControl, SWT.NO_FOCUS | SWT.DOUBLE_BUFFERED);
    canvas.addListener(SWT.Paint, this::paint);
    canvas.addListener(SWT.MouseMove, e -> setHovered(ChangeMarkerPainter.rangeAt(ranges(), geometry, e.y)));
    canvas.addListener(SWT.MouseExit, e -> setHovered(-1));
    canvas.addListener(SWT.MouseDown, this::mouseDown);
    canvas.addListener(SWT.Dispose, e -> dispose());
    viewer.addViewportListener(viewportListener);
    viewer.addTextListener(textListener);
    start();
    return canvas;
  }

  private void start() {
    ITextEditor editor = getEditor();
    if (editor == null) {
      return;
    }
    Optional<Path> file = fileOf(editor.getEditorInput());
    IDocument document = editor.getDocumentProvider() == null ? null : editor.getDocumentProvider().getDocument(editor.getEditorInput());
    if (file.isEmpty() || document == null) {
      return;
    }
    Charset charset = charsetOf(editor);
    GitRunner runner = Activator.getDefault().gitRunner();
    tracker = new LineStatusTracker(document, () -> loadBase(runner, file.get(), charset), canvas.getDisplay());
    tracker.addListener(this::redraw);
    COLUMNS.put(editor, this);
    RepositoryEvents.listen(repositoryListener);
    activationListener = new IPartListener2() {
      @Override
      public void partActivated(IWorkbenchPartReference ref) {
        // A commit or a checkout in a console changes HEAD without an event.
        if (ref.getPart(false) == editor && tracker != null) {
          tracker.reloadBase();
        }
      }
    };
    editor.getSite().getPage().addPartListener(activationListener);
  }

  private Optional<String> loadBase(GitRunner runner, Path file, Charset charset) {
    try {
      Optional<LineStatusBase.Key> found = LineStatusBase.find(new RepositoryLocator(runner), file);
      key = found.orElse(null);
      return found.isEmpty() ? Optional.empty() : LineStatusBase.load(runner, found.get(), charset);
    }
    catch (GitException e) {
      return Optional.empty();
    }
  }

  private static Optional<Path> fileOf(IEditorInput input) {
    if (input instanceof IURIEditorInput uriInput) {
      URI uri = uriInput.getURI();
      if (uri != null && "file".equals(uri.getScheme())) {
        return Optional.of(Path.of(uri));
      }
    }
    return Optional.empty();
  }

  private static Charset charsetOf(ITextEditor editor) {
    IEncodingSupport encoding = editor.getAdapter(IEncodingSupport.class);
    String name = encoding == null ? null : encoding.getEncoding() != null ? encoding.getEncoding() : encoding.getDefaultEncoding();
    try {
      return name == null ? StandardCharsets.UTF_8 : Charset.forName(name);
    }
    catch (RuntimeException e) {
      return StandardCharsets.UTF_8;
    }
  }

  /** The tracker of the editor; empty for a file that is not on disk. */
  Optional<LineStatusTracker> tracker() {
    return Optional.ofNullable(tracker);
  }

  ITextViewer viewer() {
    return viewer;
  }

  java.util.List<gitbench.diff.DiffChange> ranges() {
    return tracker == null ? java.util.List.of() : tracker.ranges();
  }

  final LineGeometry geometry = new LineGeometry() {
    @Override
    public int lineHeight() {
      return text.getLineHeight();
    }

    @Override
    public int top(int modelLine) {
      int lines = viewer.getDocument().getNumberOfLines();
      for (int line = modelLine; line < lines; line++) {
        int widgetLine = JFaceTextUtil.modelLineToWidgetLine(viewer, line);
        if (widgetLine >= 0) {
          return text.getLinePixel(widgetLine);
        }
      }
      // Below the last line, or below a folded region at the end: the bottom of the text.
      return text.getLinePixel(text.getLineCount() - 1) + text.getLineHeight();
    }

    @Override
    public boolean visible(int modelLine) {
      return modelLine >= viewer.getDocument().getNumberOfLines() || JFaceTextUtil.modelLineToWidgetLine(viewer, modelLine) >= 0;
    }
  };

  private void paint(Event e) {
    e.gc.setBackground(canvas.getParent().getBackground());
    e.gc.fillRectangle(canvas.getClientArea());
    if (viewer.getDocument() != null) {
      ChangeMarkerPainter.paint(e.gc, ranges(), geometry, hovered, Theme.isDark());
    }
  }

  private void setHovered(int index) {
    if (index != hovered) {
      hovered = index;
      canvas.setCursor(index >= 0 ? canvas.getDisplay().getSystemCursor(SWT.CURSOR_HAND) : null);
      canvas.redraw();
    }
  }

  /** Task 4 opens the popup of the marker here. */
  private void mouseDown(Event e) {
  }

  @Override
  public Control getControl() {
    return canvas;
  }

  @Override
  public int getWidth() {
    return ChangeMarkerPainter.WIDTH;
  }

  @Override
  public void redraw() {
    if (canvas != null && !canvas.isDisposed()) {
      canvas.redraw();
    }
  }

  @Override
  public void setModel(IAnnotationModel model) {
  }

  @Override
  public void setFont(Font font) {
  }

  @Override
  public void columnRemoved() {
    dispose();
  }

  private void dispose() {
    RepositoryEvents.unlisten(repositoryListener);
    if (tracker != null) {
      tracker.dispose();
      tracker = null;
    }
    ITextEditor editor = getEditor();
    if (editor != null) {
      COLUMNS.remove(editor);
      if (activationListener != null) {
        editor.getSite().getPage().removePartListener(activationListener);
        activationListener = null;
      }
    }
    if (viewer != null) {
      viewer.removeViewportListener(viewportListener);
      viewer.removeTextListener(textListener);
    }
  }
}
