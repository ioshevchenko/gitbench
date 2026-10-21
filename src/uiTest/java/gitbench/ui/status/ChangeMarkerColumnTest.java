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

  @Test
  void thePopupOpensWhenTheButtonIsReleased() throws Exception {
    gitbench.testutil.TempRepo repo = gitbench.testutil.TempRepo.init();
    repo.write("a.c", "a\nb\nc\n");
    repo.commitAll("init");
    Document document = new Document("a\nB\nc\n");
    EditorStub stub = new EditorStub(shell, repo.root().resolve("a.c"), document);
    ChangeMarkerColumn column = new ChangeMarkerColumn();
    column.setEditor(stub.editor);
    column.createControl(stub.ruler, shell);
    show();
    long end = System.currentTimeMillis() + 10000;
    while (column.ranges().isEmpty() && System.currentTimeMillis() < end) {
      while (display.readAndDispatch()) {
        // Keep going until the queue is empty.
      }
      Thread.sleep(10);
    }
    assertThat(column.ranges()).hasSize(1);
    int shells = display.getShells().length;
    org.eclipse.swt.widgets.Event click = new org.eclipse.swt.widgets.Event();
    click.button = 1;
    click.x = 4;
    click.y = stub.viewer.getTextWidget().getLinePixel(1) + 2;

    column.getControl().notifyListeners(org.eclipse.swt.SWT.MouseDown, click);
    pump();
    assertThat(display.getShells()).as("no popup while the button is down").hasSize(shells);

    column.getControl().notifyListeners(org.eclipse.swt.SWT.MouseUp, click);
    pump();
    assertThat(display.getShells()).as("the popup after the release").hasSize(shells + 1);
  }

  @Test
  void theColumnTakesTheEditorBackgroundNotTheRulerBackground() throws Exception {
    // In the dark theme Eclipse leaves the ruler, as the folding column, light; the editor text is dark.
    Path dir = Files.createTempDirectory("gitbench-column");
    Path file = Files.writeString(dir.resolve("a.c"), "a\n");
    EditorStub stub = new EditorStub(shell, file, new Document("a\n"));
    org.eclipse.swt.graphics.Color dark = new org.eclipse.swt.graphics.Color(0x1E, 0x1F, 0x22);
    stub.viewer.getTextWidget().setBackground(dark);
    stub.ruler.getControl().setBackground(display.getSystemColor(org.eclipse.swt.SWT.COLOR_WHITE));
    ChangeMarkerColumn column = new ChangeMarkerColumn();
    column.setEditor(stub.editor);
    org.eclipse.swt.widgets.Control control = column.createControl(stub.ruler, (org.eclipse.swt.widgets.Composite)stub.ruler.getControl());
    control.setBounds(0, 0, ChangeMarkerPainter.WIDTH, 50);
    show();

    org.eclipse.swt.graphics.Image image = new org.eclipse.swt.graphics.Image(display, ChangeMarkerPainter.WIDTH, 50);
    org.eclipse.swt.graphics.GC gc = new org.eclipse.swt.graphics.GC(control);
    org.eclipse.swt.graphics.RGB rgb;
    try {
      control.redraw();
      control.update();
      gc.copyArea(image, 0, 0);
      org.eclipse.swt.graphics.ImageData data = image.getImageData();
      rgb = data.palette.getRGB(data.getPixel(1, 25));
    }
    finally {
      gc.dispose();
      image.dispose();
    }

    assertThat(rgb).isEqualTo(dark.getRGB());
  }
}
