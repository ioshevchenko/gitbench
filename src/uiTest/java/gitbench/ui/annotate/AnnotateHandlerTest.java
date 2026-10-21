package gitbench.ui.annotate;

import gitbench.ui.EditorStub;
import gitbench.ui.SwtTest;
import org.eclipse.jface.text.Document;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AnnotateHandlerTest extends SwtTest {
  @Test
  void theGitCheckRunsOffTheUiThread() throws Exception {
    // Not a JUnit temp dir: the check runs git in it on a background thread.
    Path dir = Files.createTempDirectory("gitbench-annotate");
    Path file = Files.writeString(dir.resolve("plain.c"), "x\n");
    EditorStub stub = new EditorStub(shell, file, new Document("x\n"));
    List<String> problems = new ArrayList<>();

    AnnotateHandler.annotate(stub.editor, problems::add);

    assertThat(problems).as("no git ran on the UI thread yet").isEmpty();
    long end = System.currentTimeMillis() + 10000;
    while (problems.isEmpty() && System.currentTimeMillis() < end) {
      while (display.readAndDispatch()) {
        // Keep going until the queue is empty.
      }
      Thread.sleep(10);
    }
    assertThat(problems).containsExactly("Cannot annotate: the file is not under Git");
  }
}
