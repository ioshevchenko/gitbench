package gitbench.ui.annotate;

import gitbench.annotate.Blame;
import gitbench.annotate.BlameCommit;
import gitbench.ui.SwtTest;
import gitbench.ui.status.LineGeometry;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.RGB;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AnnotatePainterTest extends SwtTest {
  private static final String OLD = "1111111111111111111111111111111111111111";
  private static final String NEW = "2222222222222222222222222222222222222222";
  private static final String ZERO = "0000000000000000000000000000000000000000";
  private static final Clock CLOCK = Clock.fixed(LocalDateTime.of(2026, 10, 22, 12, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
  private static final int WIDTH = 300;

  private static final LineGeometry GEOMETRY = new LineGeometry() {
    @Override
    public int lineHeight() {
      return 16;
    }

    @Override
    public int top(int modelLine) {
      return modelLine * 16;
    }

    @Override
    public boolean visible(int modelLine) {
      return true;
    }
  };

  private static Blame blame() {
    BlameCommit old = new BlameCommit(OLD, "Bobby Long Name", "b@x", Instant.parse("2026-10-09T02:41:00Z"), "old", "f.c", null, null);
    BlameCommit young = new BlameCommit(NEW, "Ann", "a@x", Instant.parse("2026-10-22T08:00:00Z"), "new", "f.c", OLD, "f.c");
    return new Blame(List.of(OLD, NEW, ZERO), Map.of(OLD, old, NEW, young));
  }

  private ImageData paint(AnnotatePainter painter, Map<String, Integer> colors) {
    Image image = new Image(display, WIDTH, 64);
    GC gc = new GC(image);
    try {
      gc.setBackground(display.getSystemColor(SWT.COLOR_WHITE));
      gc.fillRectangle(0, 0, WIDTH, 64);
      gc.setFont(display.getSystemFont());
      painter.paint(gc, blame(), colors, GEOMETRY, false, CLOCK, WIDTH);
      return image.getImageData();
    }
    finally {
      gc.dispose();
      image.dispose();
    }
  }

  private static RGB pixel(ImageData data, int x, int y) {
    return data.palette.getRGB(data.getPixel(x, y));
  }

  /** The sum of the color parts of the darkest pixel of a line: the color of its text. */
  private static int darkest(ImageData data, int line) {
    int darkest = Integer.MAX_VALUE;
    for (int y = line * 16; y < line * 16 + 16; y++) {
      for (int x = 0; x < WIDTH; x++) {
        RGB rgb = pixel(data, x, y);
        darkest = Math.min(darkest, rgb.red + rgb.green + rgb.blue);
      }
    }
    return darkest;
  }

  @Test
  void backgroundFollowsTheCommitColor() {
    ImageData data = paint(new AnnotatePainter(display.getSystemFont()), Map.of(OLD, 0xC9DAFB, NEW, 0xA1BEF7));

    assertThat(pixel(data, WIDTH - 2, 8)).isEqualTo(new RGB(0xC9, 0xDA, 0xFB));
    assertThat(pixel(data, WIDTH - 2, 16 + 8)).isEqualTo(new RGB(0xA1, 0xBE, 0xF7));
  }

  @Test
  void uncommittedLineHasNoBackground() {
    ImageData data = paint(new AnnotatePainter(display.getSystemFont()), Map.of(OLD, 0xC9DAFB, NEW, 0xA1BEF7));

    assertThat(pixel(data, WIDTH - 2, 32 + 8)).isEqualTo(new RGB(255, 255, 255));
    assertThat(darkest(data, 2)).isEqualTo(255 * 3);
  }

  @Test
  void widthFitsTheLongestDateAndAuthor() {
    AnnotatePainter painter = new AnnotatePainter(display.getSystemFont());
    GC gc = new GC(display);
    int expected;
    try {
      gc.setFont(display.getSystemFont());
      expected = gc.textExtent("09.10.2026").x + 8 + gc.textExtent("Bobby Long Name").x + 8;
    }
    finally {
      gc.dispose();
    }

    assertThat(painter.width(blame(), CLOCK)).isGreaterThanOrEqualTo(expected);
  }

  @Test
  void lastCommitLinesUseTheLastCommitColor() {
    ImageData data = paint(new AnnotatePainter(display.getSystemFont()), Map.of());

    assertThat(darkest(data, 1)).isLessThan(darkest(data, 0));
  }
}
