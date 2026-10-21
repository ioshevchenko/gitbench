package gitbench.ui.annotate;

import gitbench.annotate.AnnotationColors;
import gitbench.annotate.Blame;
import gitbench.annotate.BlameCommit;
import gitbench.annotate.BlameService;
import gitbench.diff.DiffRequest;
import gitbench.diff.DiffSide;
import gitbench.git.GitException;
import gitbench.git.GitRepository;
import gitbench.git.GitRunner;
import gitbench.git.RepositoryLocator;
import gitbench.status.LineStatusBase;
import gitbench.ui.Activator;
import gitbench.ui.RepositoryEvents;
import gitbench.ui.Theme;
import gitbench.ui.diff.DiffOpener;
import gitbench.ui.log.LogLocator;
import gitbench.ui.status.EditorFiles;
import gitbench.ui.status.ViewerGeometry;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentListener;
import org.eclipse.jface.text.ITextListener;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.IViewportListener;
import org.eclipse.jface.text.source.CompositeRuler;
import org.eclipse.jface.text.source.IAnnotationModel;
import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.ui.texteditor.ITextEditor;
import org.eclipse.ui.texteditor.rulers.AbstractContributedRulerColumn;
import org.eclipse.ui.texteditor.rulers.IColumnSupport;
import org.eclipse.ui.texteditor.rulers.RulerColumnDescriptor;
import org.eclipse.ui.texteditor.rulers.RulerColumnRegistry;

import java.nio.charset.Charset;
import java.nio.file.Path;
import java.time.Clock;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * The annotate column, as the IntelliJ annotation gutter: the date and the author of each line, with the background by
 * the age of the commit. It is hidden until Annotate with Git Blame shows it in one editor. While it shows, it blames
 * the editor text again after each pause in typing, so new lines stay empty.
 */
public final class AnnotateColumn extends AbstractContributedRulerColumn {
  public static final String ID = "gitbench.columns.annotate";
  /** IntelliJ also waits for a pause in typing before it annotates again. */
  private static final int BLAME_DELAY_MS = 500;
  private static final DateTimeFormatter TOOLTIP_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault());
  private static final int SHORT_HASH = 8;
  private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool(runnable -> {
    Thread thread = new Thread(runnable, "Gitbench annotate");
    thread.setDaemon(true);
    return thread;
  });

  private CompositeRuler ruler;
  private ITextViewer viewer;
  private ViewerGeometry geometry;
  private Canvas canvas;
  /** Kept apart from the canvas: a blame may end after the canvas is disposed. */
  private org.eclipse.swt.widgets.Display display;
  private AnnotatePainter painter;
  private IDocument document;
  private Path file;
  private Charset charset;
  /** The repository and the path of the file, looked up once per input on the blame thread. */
  private volatile LineStatusBase.Key key;
  private volatile boolean keySearched;
  /** The first and the last line of the text that the current change replaces. */
  private int editStart;
  private int editOldEnd;
  private Blame blame;
  private Map<String, Integer> colors = Map.of();
  private int width;
  private int generation;
  private boolean disposed;
  private final Runnable blameLater = this::reblame;
  private final IDocumentListener documentListener = new IDocumentListener() {
    @Override
    public void documentAboutToBeChanged(DocumentEvent event) {
      editStart = lineOf(event.getOffset());
      editOldEnd = lineOf(event.getOffset() + event.getLength());
    }

    @Override
    public void documentChanged(DocumentEvent event) {
      int newEnd = lineOf(event.getOffset() + (event.getText() == null ? 0 : event.getText().length()));
      if (org.eclipse.swt.widgets.Display.getCurrent() == display) {
        edited(editStart, editOldEnd, newEnd);
      }
      else {
        // A change on another thread: the state belongs to the UI thread, so the old blame goes until the next one.
        onUiThread(() -> {
          blame = null;
          edited(0, 0, 0);
        });
      }
    }
  };
  private final Consumer<GitRepository> repositoryListener = repository -> {
    LineStatusBase.Key current = key;
    if (current != null && current.repository().equals(repository) && canvas != null && !canvas.isDisposed()) {
      onUiThread(this::reblame);
    }
  };
  private final IViewportListener viewportListener = verticalOffset -> redraw();
  private final ITextListener textListener = event -> redraw();

  /** Shows or hides the column in the editor, as Annotate with Git Blame and Close Annotations do. */
  public static void setVisible(ITextEditor editor, boolean visible) {
    IColumnSupport support = editor.getAdapter(IColumnSupport.class);
    RulerColumnDescriptor descriptor = RulerColumnRegistry.getDefault().getColumnDescriptor(ID);
    if (support != null && descriptor != null) {
      support.setColumnVisible(descriptor, visible);
    }
  }

  public static boolean isVisible(ITextEditor editor) {
    IColumnSupport support = editor.getAdapter(IColumnSupport.class);
    RulerColumnDescriptor descriptor = RulerColumnRegistry.getDefault().getColumnDescriptor(ID);
    return support != null && descriptor != null && support.isColumnVisible(descriptor);
  }

  @Override
  public Control createControl(CompositeRuler parentRuler, Composite parentControl) {
    ruler = parentRuler;
    display = parentControl.getDisplay();
    viewer = parentRuler.getTextViewer();
    geometry = new ViewerGeometry(viewer);
    painter = new AnnotatePainter(viewer.getTextWidget().getFont());
    canvas = new Canvas(parentControl, SWT.NO_FOCUS | SWT.DOUBLE_BUFFERED);
    canvas.addListener(SWT.Paint, this::paint);
    canvas.addListener(SWT.MouseMove, e -> canvas.setToolTipText(tooltip(geometry.lineAt(e.y))));
    canvas.addListener(SWT.MouseDown, this::mouseDown);
    canvas.addListener(SWT.MenuDetect, this::showMenu);
    canvas.addListener(SWT.Dispose, e -> dispose());
    viewer.addViewportListener(viewportListener);
    viewer.addTextListener(textListener);
    RepositoryEvents.listen(repositoryListener);
    bind();
    return canvas;
  }

  /** Follows the current file and document of the editor; a file that is not on disk gets no blame. */
  private void bind() {
    ITextEditor editor = getEditor();
    file = editor == null ? null : EditorFiles.file(editor.getEditorInput()).orElse(null);
    document = editor == null ? null : EditorFiles.document(editor).orElse(null);
    charset = editor == null ? null : EditorFiles.charset(editor);
    key = null;
    keySearched = false;
    blame = null;
    colors = Map.of();
    if (file != null && document != null) {
      document.addDocumentListener(documentListener);
      reblame();
    }
  }

  /** Eclipse keeps the columns when the editor input changes and only sets the model again. */
  private void rebindIfInputChanged() {
    ITextEditor editor = getEditor();
    if (disposed || editor == null) {
      return;
    }
    Path newFile = EditorFiles.file(editor.getEditorInput()).orElse(null);
    IDocument newDocument = EditorFiles.document(editor).orElse(null);
    if (java.util.Objects.equals(newFile, file) && newDocument == document) {
      return;
    }
    if (document != null) {
      document.removeDocumentListener(documentListener);
    }
    generation++;
    bind();
    redraw();
  }

  /** Moves the blame with the edit at once, so no annotation stands on a wrong line; then blames again after a pause. */
  private void edited(int start, int oldEnd, int newEnd) {
    generation++;
    if (blame != null) {
      blame = blame.shifted(start, oldEnd, newEnd);
      redraw();
    }
    display.timerExec(BLAME_DELAY_MS, blameLater);
  }

  private int lineOf(int offset) {
    try {
      return document.getLineOfOffset(Math.min(offset, document.getLength()));
    }
    catch (org.eclipse.jface.text.BadLocationException e) {
      return 0;
    }
  }

  private void onUiThread(Runnable runnable) {
    if (!display.isDisposed()) {
      display.asyncExec(() -> {
        if (!disposed) {
          runnable.run();
        }
      });
    }
  }

  /** Blames the editor text on a background thread; a result for an older text is dropped. */
  private void reblame() {
    if (disposed || document == null) {
      return;
    }
    String text = document.get();
    Path blamed = file;
    Charset encoding = charset;
    int expected = ++generation;
    GitRunner runner = Activator.probeRunner();
    boolean dark = Theme.isDark();
    EXECUTOR.execute(() -> {
      try {
        if (!keySearched) {
          key = LineStatusBase.find(new RepositoryLocator(runner), blamed).orElse(null);
          keySearched = true;
        }
        LineStatusBase.Key found = key;
        if (found == null) {
          return;
        }
        Blame result = new BlameService(runner).blame(found.repository(), found.path(), text, encoding);
        Map<String, Integer> resultColors = AnnotationColors.byCommit(result, dark);
        onUiThread(() -> {
          if (expected == generation) {
            blame = result;
            colors = resultColors;
            relayout();
          }
        });
      }
      catch (GitException e) {
        onUiThread(() -> showError(e.userMessage()));
      }
      catch (RuntimeException e) {
        // Odd git output must not kill the thread without a word.
        onUiThread(() -> showError(String.valueOf(e)));
      }
    });
  }

  private void showError(String message) {
    ITextEditor editor = getEditor();
    if (!disposed && editor != null) {
      editor.getEditorSite().getActionBars().getStatusLineManager().setErrorMessage("Annotate: " + message);
    }
  }

  private void relayout() {
    int newWidth = painter.width(blame, Clock.systemDefaultZone());
    if (newWidth != width) {
      width = newWidth;
      ruler.relayout();
    }
    redraw();
  }

  private void paint(Event e) {
    // The editor background, not the ruler one: in the dark theme Eclipse may leave ruler parts light.
    e.gc.setBackground(viewer.getTextWidget().getBackground());
    e.gc.fillRectangle(canvas.getClientArea());
    if (blame != null && viewer.getDocument() != null) {
      painter.paint(e.gc, blame, colors, geometry, Theme.isDark(), Clock.systemDefaultZone(), canvas.getClientArea().width);
    }
  }

  /** The shown blame, or null before the first one; for the tests. */
  Blame blame() {
    return blame;
  }

  private Optional<BlameCommit> commitAt(int line) {
    return blame == null ? Optional.empty() : blame.commitAt(line);
  }

  /** The commit of the line, as the IntelliJ annotation tooltip: hash, author, date, and the summary. */
  private String tooltip(int line) {
    return commitAt(line).map(c -> c.hash().substring(0, SHORT_HASH) + " " + c.author() + " " + TOOLTIP_DATE.format(c.authorTime())
                                   + "\n\n" + c.summary()).orElse(null);
  }

  private void mouseDown(Event e) {
    if (e.button == 1) {
      commitAt(geometry.lineAt(e.y)).ifPresent(this::selectInLog);
    }
  }

  private void selectInLog(BlameCommit commit) {
    LogLocator.showCommit(getEditor().getSite().getPage(), commit.hash());
  }

  /** The menu of the column instead of the menu of the ruler, as IntelliJ shows over its annotations. */
  private void showMenu(Event e) {
    e.doit = false;
    int line = geometry.lineAt(canvas.toControl(e.x, e.y).y);
    Optional<BlameCommit> commit = commitAt(line);
    Menu menu = new Menu(canvas);
    item(menu, "Show Diff", commit.isPresent(), () -> showDiff(commit.get()));
    item(menu, "Copy Revision Number", commit.isPresent(), () -> copy(commit.get().hash()));
    item(menu, "Select in Git Log", commit.isPresent(), () -> selectInLog(commit.get()));
    new MenuItem(menu, SWT.SEPARATOR);
    item(menu, "Close Annotations", true, () -> setVisible(getEditor(), false));
    menu.addListener(SWT.Hide, h -> menu.getDisplay().asyncExec(menu::dispose));
    menu.setLocation(e.x, e.y);
    menu.setVisible(true);
  }

  private static void item(Menu menu, String text, boolean enabled, Runnable action) {
    MenuItem item = new MenuItem(menu, SWT.PUSH);
    item.setText(text);
    item.setEnabled(enabled);
    item.addListener(SWT.Selection, e -> action.run());
  }

  /** The change of the file in the commit: the previous version on the left, the commit on the right. */
  private void showDiff(BlameCommit commit) {
    LineStatusBase.Key found = key;
    if (found == null) {
      return;
    }
    String path = commit.filename() != null ? commit.filename() : found.path();
    DiffSide left = commit.previousHash() == null ? new DiffSide.Empty("File added")
                                                  : new DiffSide.Revision(commit.previousHash(), commit.previousFilename());
    DiffRequest request = new DiffRequest(found.repository(), path, left, new DiffSide.Revision(commit.hash(), path));
    DiffOpener.open(getEditor().getSite().getPage(), List.of(request), 0);
  }

  private void copy(String hash) {
    Clipboard clipboard = new Clipboard(canvas.getDisplay());
    try {
      clipboard.setContents(new Object[]{hash}, new Transfer[]{TextTransfer.getInstance()});
    }
    finally {
      clipboard.dispose();
    }
  }

  @Override
  public Control getControl() {
    return canvas;
  }

  @Override
  public int getWidth() {
    return width;
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
    if (disposed) {
      return;
    }
    disposed = true;
    RepositoryEvents.unlisten(repositoryListener);
    if (document != null) {
      document.removeDocumentListener(documentListener);
    }
    if (viewer != null) {
      viewer.removeViewportListener(viewportListener);
      viewer.removeTextListener(textListener);
    }
  }
}
