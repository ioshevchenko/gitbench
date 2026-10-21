package gitbench.ui.annotate;

import gitbench.testutil.TempRepo;
import gitbench.ui.EditorStub;
import gitbench.ui.SwtTest;
import org.eclipse.jface.text.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

class AnnotateColumnTest extends SwtTest {
  private final List<Throwable> uncaught = Collections.synchronizedList(new ArrayList<>());
  private Thread.UncaughtExceptionHandler previousHandler;

  private void catchUncaught() {
    previousHandler = Thread.getDefaultUncaughtExceptionHandler();
    Thread.setDefaultUncaughtExceptionHandler((thread, e) -> uncaught.add(e));
  }

  @AfterEach
  void restoreHandler() {
    if (previousHandler != null) {
      Thread.setDefaultUncaughtExceptionHandler(previousHandler);
    }
  }

  private void waitFor(BooleanSupplier condition) throws InterruptedException {
    long end = System.currentTimeMillis() + 10000;
    while (!condition.getAsBoolean() && System.currentTimeMillis() < end) {
      while (display.readAndDispatch()) {
        // Keep going until the queue is empty.
      }
      Thread.sleep(10);
    }
    assertThat(condition.getAsBoolean()).as("condition within 10 s").isTrue();
  }

  private static TempRepo repo() {
    TempRepo repo = TempRepo.init();
    repo.write("a.c", "a\nb\n");
    repo.write("b.c", "x\ny\nz\n");
    repo.commitAll("init");
    return repo;
  }

  private AnnotateColumn open(EditorStub stub) {
    AnnotateColumn column = new AnnotateColumn();
    column.setEditor(stub.editor);
    column.createControl(stub.ruler, shell);
    return column;
  }

  @Test
  void anEditMovesTheBlameAtOnce() throws Exception {
    TempRepo repo = repo();
    Document document = new Document("a\nb\n");
    AnnotateColumn column = open(new EditorStub(shell, repo.root().resolve("a.c"), document));
    waitFor(() -> column.blame() != null);

    document.replace(0, 0, "new\n");

    // No events ran yet: the new line is empty and the old lines moved down.
    assertThat(column.blame().commitAt(0)).isEmpty();
    assertThat(column.blame().commitAt(2)).isPresent();
  }

  @Test
  void aNewEditorInputBlamesTheNewFile() throws Exception {
    TempRepo repo = repo();
    EditorStub stub = new EditorStub(shell, repo.root().resolve("a.c"), new Document("a\nb\n"));
    AnnotateColumn column = open(stub);
    waitFor(() -> column.blame() != null && column.blame().lineHashes().size() == 2);

    stub.show(repo.root().resolve("b.c"), new Document("x\ny\nz\n"));
    column.setModel(null);

    waitFor(() -> column.blame() != null && column.blame().lineHashes().size() == 3);
  }

  @Test
  void closingWhileTheBlameRunsThrowsNothing() throws Exception {
    catchUncaught();
    TempRepo repo = repo();
    AnnotateColumn column = open(new EditorStub(shell, repo.root().resolve("a.c"), new Document("a\nb\n")));

    column.getControl().dispose();
    long end = System.currentTimeMillis() + 3000;
    while (System.currentTimeMillis() < end) {
      while (display.readAndDispatch()) {
        // Keep going until the queue is empty.
      }
      Thread.sleep(10);
    }

    assertThat(uncaught).isEmpty();
  }

  @Test
  void documentChangeFromABackgroundThreadIsSafe() throws Exception {
    TempRepo repo = repo();
    Document document = new Document("a\nb\n");
    AnnotateColumn column = open(new EditorStub(shell, repo.root().resolve("a.c"), document));
    waitFor(() -> column.blame() != null);
    Throwable[] failure = {null};

    Thread thread = new Thread(() -> {
      try {
        document.replace(0, 0, "new\n");
      }
      catch (Throwable e) {
        failure[0] = e;
      }
    });
    thread.start();
    thread.join();

    assertThat(failure[0]).isNull();
    waitFor(() -> column.blame() != null && column.blame().lineHashes().size() == 3 && column.blame().commitAt(0).isEmpty());
  }
}
