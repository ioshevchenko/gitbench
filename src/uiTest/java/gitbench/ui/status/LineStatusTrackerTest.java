package gitbench.ui.status;

import gitbench.diff.BlockKind;
import gitbench.status.LineStatus;
import gitbench.ui.SwtTest;
import org.eclipse.jface.text.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

class LineStatusTrackerTest extends SwtTest {
  private LineStatusTracker tracker;

  @AfterEach
  void disposeTracker() {
    if (tracker != null) {
      tracker.dispose();
    }
  }

  /** Runs the event loop until the condition holds, for up to 5 seconds. */
  private void waitFor(BooleanSupplier condition) throws InterruptedException {
    long end = System.currentTimeMillis() + 5000;
    while (!condition.getAsBoolean() && System.currentTimeMillis() < end) {
      while (display.readAndDispatch()) {
        // Keep going until the queue is empty.
      }
      Thread.sleep(10);
    }
    assertThat(condition.getAsBoolean()).as("condition within 5 s").isTrue();
  }

  @Test
  void computesRangesAfterLoadingTheBase() throws Exception {
    Document document = new Document("a\nx\nb\n");

    tracker = new LineStatusTracker(document, () -> Optional.of("a\nb\n"), display);

    waitFor(() -> tracker.ranges().size() == 1);
    assertThat(tracker.ranges().get(0).kind()).isEqualTo(BlockKind.INSERTED);
    assertThat(tracker.base()).contains("a\nb\n");
  }

  @Test
  void rangesFollowTheLatestText() throws Exception {
    String base = "0\n1\n2\n3\n4\n";
    Document document = new Document(base);
    tracker = new LineStatusTracker(document, () -> Optional.of(base), display);
    waitFor(() -> tracker.base().isPresent());

    document.replace(0, 1, "A");
    document.replace(document.getLineOffset(4), 1, "B");

    waitFor(() -> tracker.ranges().equals(LineStatus.compute(base, document.get())));
    assertThat(tracker.ranges()).hasSize(2);
  }

  @Test
  void reloadBaseRecomputes() throws Exception {
    AtomicReference<String> base = new AtomicReference<>("a\n");
    Document document = new Document("a\nb\n");
    tracker = new LineStatusTracker(document, () -> Optional.of(base.get()), display);
    waitFor(() -> tracker.ranges().size() == 1);

    base.set("a\nb\n");
    tracker.reloadBase();

    waitFor(() -> tracker.ranges().isEmpty());
  }

  @Test
  void noBaseGivesNoRanges() throws Exception {
    Document document = new Document("a\n");
    int[] updates = {0};
    tracker = new LineStatusTracker(document, Optional::empty, display);
    tracker.addListener(() -> updates[0]++);

    document.replace(0, 0, "x");
    waitFor(() -> updates[0] > 0);

    assertThat(tracker.ranges()).isEmpty();
    assertThat(tracker.base()).isEmpty();
  }

  @Test
  void rangesShiftAtOnceOnAnEdit() throws Exception {
    String base = "0\n1\n2\n3\n4\n5\n";
    Document document = new Document("0\n1\n2\n3\nX\n5\n");
    tracker = new LineStatusTracker(document, () -> Optional.of(base), display);
    waitFor(() -> tracker.ranges().size() == 1);

    document.replace(0, 0, "new\n");

    // No events ran yet: the range moved with the text before the next comparison.
    assertThat(tracker.ranges()).singleElement().satisfies(r -> assertThat(r.startLine2()).isEqualTo(5));
  }

  @Test
  void documentChangeFromABackgroundThreadIsSafe() throws Exception {
    String base = "a\nb\n";
    Document document = new Document(base);
    tracker = new LineStatusTracker(document, () -> Optional.of(base), display);
    waitFor(() -> tracker.base().isPresent());
    Throwable[] failure = {null};

    Thread thread = new Thread(() -> {
      try {
        document.replace(0, 1, "A");
      }
      catch (Throwable e) {
        failure[0] = e;
      }
    });
    thread.start();
    thread.join();

    assertThat(failure[0]).isNull();
    waitFor(() -> tracker.ranges().size() == 1);
  }
}
