package gitbench.ui.status;

import gitbench.ui.EditorStub;
import gitbench.ui.SwtTest;
import org.eclipse.jface.text.Document;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ChangeMarkerColumnTest extends SwtTest {
  @Test
  void aNewEditorInputGetsANewTracker() throws Exception {
    // Not a JUnit temp dir: the base loads run git in it on a background thread and would block its removal.
    Path dir = Files.createTempDirectory("gitbench-column");
    Path first = Files.writeString(dir.resolve("a.c"), "a\n");
    Path second = Files.writeString(dir.resolve("b.c"), "b\n");
    Document firstDocument = new Document("a\n");
    EditorStub stub = new EditorStub(shell, first, firstDocument);
    ChangeMarkerColumn column = new ChangeMarkerColumn();
    column.setEditor(stub.editor);
    column.createControl(stub.ruler, shell);
    assertThat(column.tracker()).hasValueSatisfying(t -> assertThat(t.document()).isSameAs(firstDocument));

    Document secondDocument = new Document("b\n");
    stub.show(second, secondDocument);
    column.setModel(null);

    assertThat(column.tracker()).hasValueSatisfying(t -> assertThat(t.document()).isSameAs(secondDocument));
  }
}
