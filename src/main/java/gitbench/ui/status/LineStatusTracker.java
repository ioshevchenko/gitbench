package gitbench.ui.status;

import gitbench.diff.DiffChange;
import gitbench.status.LineStatus;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentListener;
import org.eclipse.swt.widgets.Display;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * Keeps the changed ranges of a document against its HEAD text up to date, as the IntelliJ LineStatusTracker. The
 * HEAD text loads and the comparison runs on a background thread; the state changes on the UI thread only. A result
 * for an older text or an older base is dropped.
 */
public final class LineStatusTracker {
  /** IntelliJ also waits for a pause in typing before it compares again. */
  private static final int COMPARE_DELAY_MS = 200;
  private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool(runnable -> {
    Thread thread = new Thread(runnable, "Gitbench line status");
    thread.setDaemon(true);
    return thread;
  });

  private final IDocument document;
  private final Supplier<Optional<String>> baseLoader;
  private final Display display;
  private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
  private final Runnable compareLater = this::compare;
  private final IDocumentListener documentListener = new IDocumentListener() {
    @Override
    public void documentAboutToBeChanged(DocumentEvent event) {
      editStart = lineOf(event.getOffset());
      editOldEnd = lineOf(event.getOffset() + event.getLength());
    }

    @Override
    public void documentChanged(DocumentEvent event) {
      int newEnd = lineOf(event.getOffset() + (event.getText() == null ? 0 : event.getText().length()));
      if (Display.getCurrent() == display) {
        edited(editStart, editOldEnd, newEnd);
      }
      else {
        // A change on another thread: the state belongs to the UI thread, so the markers wait for the comparison.
        onUiThread(() -> {
          ranges = List.of();
          edited(0, 0, 0);
        });
      }
    }
  };
  /** The first and the last line of the text that the current change replaces. */
  private int editStart;
  private int editOldEnd;

  /** Null until the first load ends. The ranges always belong to this base. */
  private Optional<String> base;
  /** A loaded base that waits for its comparison; then it becomes {@link #base} together with the ranges. */
  private Optional<String> pendingBase;
  private List<DiffChange> ranges = List.of();
  private int generation;
  private int baseGeneration;
  private boolean disposed;

  /** Starts to load the base at once. {@code baseLoader} runs on a background thread. */
  public LineStatusTracker(IDocument document, Supplier<Optional<String>> baseLoader, Display display) {
    this.document = document;
    this.baseLoader = baseLoader;
    this.display = display;
    document.addDocumentListener(documentListener);
    reloadBase();
  }

  public List<DiffChange> ranges() {
    return ranges;
  }

  /** The HEAD text; empty when the file has none, as an untracked file, or before the first load. */
  public Optional<String> base() {
    return base == null ? Optional.empty() : base;
  }

  public IDocument document() {
    return document;
  }

  /** Loads the HEAD text again, for example after a commit, and compares again. */
  public void reloadBase() {
    int expected = ++baseGeneration;
    EXECUTOR.execute(() -> {
      Optional<String> loaded;
      try {
        loaded = baseLoader.get();
      }
      catch (RuntimeException e) {
        // A file without a base shows no markers; that is better than markers that never come.
        loaded = Optional.empty();
      }
      Optional<String> result = loaded;
      onUiThread(() -> {
        if (expected == baseGeneration) {
          pendingBase = result;
          compare();
        }
      });
    });
  }

  /** Runs after each change of the ranges or the base, on the UI thread. */
  public void addListener(Runnable listener) {
    listeners.add(listener);
  }

  public void dispose() {
    disposed = true;
    document.removeDocumentListener(documentListener);
    listeners.clear();
  }

  /**
   * Moves the ranges with the edit at once, as the IntelliJ DocumentTracker does, so the markers and Rollback Lines
   * never use the lines of the old text; then compares again after a pause in typing.
   */
  private void edited(int start, int oldEnd, int newEnd) {
    generation++;
    ranges = LineStatus.shift(ranges, start, oldEnd, newEnd);
    fire();
    display.timerExec(COMPARE_DELAY_MS, compareLater);
  }

  private int lineOf(int offset) {
    try {
      return document.getLineOfOffset(Math.min(offset, document.getLength()));
    }
    catch (BadLocationException e) {
      return 0;
    }
  }

  private void compare() {
    Optional<String> used = pendingBase != null ? pendingBase : base;
    if (disposed || used == null) {
      return;
    }
    if (used.isEmpty()) {
      base = used;
      pendingBase = null;
      ranges = List.of();
      fire();
      return;
    }
    String baseText = used.get();
    String text = document.get();
    int expected = ++generation;
    EXECUTOR.execute(() -> {
      List<DiffChange> computed = LineStatus.compute(baseText, text);
      onUiThread(() -> {
        if (expected == generation) {
          base = used;
          if (pendingBase == used) {
            pendingBase = null;
          }
          ranges = computed;
          fire();
        }
      });
    });
  }

  private void fire() {
    listeners.forEach(Runnable::run);
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
}
