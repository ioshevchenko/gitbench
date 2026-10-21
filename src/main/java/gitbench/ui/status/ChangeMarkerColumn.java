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
import org.eclipse.jface.text.source.CompositeRuler;
import org.eclipse.jface.text.source.IAnnotationModel;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.ui.IPartListener2;
import org.eclipse.ui.IWorkbenchPartReference;
import org.eclipse.ui.texteditor.ITextEditor;
import org.eclipse.ui.texteditor.rulers.AbstractContributedRulerColumn;

import java.nio.charset.Charset;
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
  /** The file and the document the tracker follows; the editor input can change under the column. */
  private Path boundFile;
  private IDocument boundDocument;
  /** The repository and the path of the file, looked up once per input on the tracker thread. */
  private volatile LineStatusBase.Key key;
  private volatile boolean keySearched;
  private int hovered = -1;
  private LineGeometry geometry;
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
    geometry = new ViewerGeometry(viewer);
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
    bind();
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

  /** Starts a tracker for the current file and document of the editor; a file that is not on disk gets none. */
  private void bind() {
    ITextEditor editor = getEditor();
    Optional<Path> file = EditorFiles.file(editor.getEditorInput());
    Optional<IDocument> document = EditorFiles.document(editor);
    boundFile = file.orElse(null);
    boundDocument = document.orElse(null);
    key = null;
    keySearched = false;
    if (file.isEmpty() || document.isEmpty()) {
      return;
    }
    Charset charset = EditorFiles.charset(editor);
    GitRunner runner = Activator.probeRunner();
    tracker = new LineStatusTracker(document.get(), () -> loadBase(runner, file.get(), charset), canvas.getDisplay());
    tracker.addListener(this::redraw);
  }

  /**
   * Eclipse keeps the columns when the editor input changes (a reused editor, Save As, a rename) and only sets the
   * model again; the markers then follow the new file and document.
   */
  private void rebindIfInputChanged() {
    ITextEditor editor = getEditor();
    if (editor == null || canvas == null || canvas.isDisposed()) {
      return;
    }
    Path file = EditorFiles.file(editor.getEditorInput()).orElse(null);
    IDocument document = EditorFiles.document(editor).orElse(null);
    if (java.util.Objects.equals(file, boundFile) && document == boundDocument) {
      return;
    }
    if (tracker != null) {
      tracker.dispose();
      tracker = null;
    }
    bind();
    redraw();
  }

  private Optional<String> loadBase(GitRunner runner, Path file, Charset charset) {
    try {
      if (!keySearched) {
        key = LineStatusBase.find(new RepositoryLocator(runner), file).orElse(null);
        keySearched = true;
      }
      LineStatusBase.Key found = key;
      return found == null ? Optional.empty() : LineStatusBase.load(runner, found, charset);
    }
    catch (GitException e) {
      return Optional.empty();
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

  private void mouseDown(Event e) {
    if (e.button != 1) {
      return;
    }
    int index = ChangeMarkerPainter.rangeAt(ranges(), geometry, e.y);
    if (index >= 0) {
      openPopup(index);
    }
  }

  /** Opens the popup of the range under its marker, as a click on the marker does. */
  void openPopup(int index) {
    if (tracker == null || index < 0 || index >= tracker.ranges().size()) {
      return;
    }
    gitbench.diff.DiffChange range = tracker.ranges().get(index);
    int y = geometry.top(range.endLine2());
    if (range.startLine2() == range.endLine2()) {
      y += geometry.lineHeight() / 2;
    }
    org.eclipse.swt.graphics.Point location = canvas.toDisplay(ChangeMarkerPainter.WIDTH, y);
    new ChangeMarkerPopup(canvas.getShell(), tracker.document(), tracker, index, location, new ChangeMarkerPopup.Actions() {
      @Override
      public void showDiff(int documentLine) {
        ChangeMarkerColumn.this.showDiff(documentLine);
      }

      @Override
      public void reveal(int documentLine) {
        revealLine(documentLine);
      }
    });
  }

  /** Moves the caret to the start of the document line and scrolls to it. */
  void revealLine(int line) {
    try {
      IDocument document = tracker.document();
      int offset = document.getLineOffset(Math.min(line, document.getNumberOfLines() - 1));
      getEditor().selectAndReveal(offset, 0);
    }
    catch (org.eclipse.jface.text.BadLocationException e) {
      // The document got shorter; there is nothing to show.
    }
  }

  /** Show Diff for Lines: the diff of the file against HEAD at this block, as the Commit view opens it. */
  private void showDiff(int line) {
    LineStatusBase.Key current = key;
    if (current == null || tracker == null) {
      return;
    }
    gitbench.git.ChangeKind kind = tracker.base().map(String::isEmpty).orElse(false)
                                   ? gitbench.git.ChangeKind.ADDED : gitbench.git.ChangeKind.MODIFIED;
    gitbench.diff.DiffRequest request =
      gitbench.diff.DiffRequests.forLocalChange(current.repository(), new gitbench.git.FileChange(current.path(), null, kind));
    gitbench.ui.diff.DiffOpener.open(getEditor().getSite().getPage(), java.util.List.of(request), 0, line);
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
    rebindIfInputChanged();
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
