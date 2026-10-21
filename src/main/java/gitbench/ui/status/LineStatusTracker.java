package gitbench.ui.status;

import gitbench.diff.DiffChange;
import gitbench.status.LineStatus;
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
    }

    @Override
    public void documentChanged(DocumentEvent event) {
      generation++;
      display.timerExec(COMPARE_DELAY_MS, compareLater);
    }
  };

  /** Null until the first load ends. */
  private Optional<String> base;
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
          base = result;
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

  private void compare() {
    if (disposed || base == null) {
      return;
    }
    if (base.isEmpty()) {
      ranges = List.of();
      fire();
      return;
    }
    String baseText = base.get();
    String text = document.get();
    int expected = ++generation;
    EXECUTOR.execute(() -> {
      List<DiffChange> computed = LineStatus.compute(baseText, text);
      onUiThread(() -> {
        if (expected == generation) {
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
