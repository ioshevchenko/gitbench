package gitbench.ui.status;

import gitbench.diff.BlockKind;
import gitbench.diff.DiffChange;
import gitbench.ui.SwtTest;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.RGB;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChangeMarkerPainterTest extends SwtTest {
  private static final RGB WHITE = new RGB(255, 255, 255);
  private static final RGB ADDED = new RGB(0x7F, 0xC7, 0x84);
  private static final RGB DELETED = new RGB(0x76, 0x7A, 0x8A);

  /** 16 pixels per line, all lines visible. */
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

  private static DiffChange inserted(int line1, int line2) {
    return new DiffChange(0, 0, line1, line2, BlockKind.INSERTED, List.of(), List.of());
  }

  private static DiffChange deleted(int line) {
    return new DiffChange(3, 5, line, line, BlockKind.DELETED, List.of(), List.of());
  }

  private ImageData paint(List<DiffChange> ranges, int hovered) {
    Image image = new Image(display, ChangeMarkerPainter.WIDTH, 200);
    GC gc = new GC(image);
    try {
      gc.setBackground(display.getSystemColor(org.eclipse.swt.SWT.COLOR_WHITE));
      gc.fillRectangle(0, 0, ChangeMarkerPainter.WIDTH, 200);
      ChangeMarkerPainter.paint(gc, ranges, GEOMETRY, hovered, false);
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

  @Test
  void insertedLinesAreGreenBetweenTheirTops() {
    ImageData data = paint(List.of(inserted(1, 3)), -1);

    assertThat(pixel(data, 4, 16 + 8)).isEqualTo(ADDED);
    assertThat(pixel(data, 4, 16 + 24)).isEqualTo(ADDED);
    assertThat(pixel(data, 4, 16)).isEqualTo(WHITE);
    assertThat(pixel(data, 4, 3 * 16)).isEqualTo(WHITE);
  }

  @Test
  void markerKeepsLeftGap() {
    ImageData data = paint(List.of(inserted(1, 3)), -1);

    assertThat(pixel(data, 1, 16 + 8)).isEqualTo(WHITE);
    assertThat(pixel(data, 3, 16 + 8)).isEqualTo(ADDED);
  }

  @Test
  void deletedRangeIsAShortBarOnTheBoundary() {
    ImageData data = paint(List.of(deleted(4)), -1);

    assertThat(pixel(data, 4, 4 * 16)).isEqualTo(DELETED);
    assertThat(pixel(data, 4, 4 * 16 - 3)).isEqualTo(DELETED);
    assertThat(pixel(data, 4, 4 * 16 + 2)).isEqualTo(DELETED);
    assertThat(pixel(data, 4, 4 * 16 - 6)).isEqualTo(WHITE);
    assertThat(pixel(data, 4, 4 * 16 + 5)).isEqualTo(WHITE);
  }

  @Test
  void hoveredMarkerIsWiderToTheLeft() {
    ImageData data = paint(List.of(inserted(1, 3)), 0);

    assertThat(pixel(data, 1, 16 + 8)).isEqualTo(ADDED);
  }

  @Test
  void rangeAtFindsTheBlockUnderTheMouse() {
    List<DiffChange> ranges = List.of(inserted(1, 3), inserted(6, 7));

    assertThat(ChangeMarkerPainter.rangeAt(ranges, GEOMETRY, 20)).isEqualTo(0);
    assertThat(ChangeMarkerPainter.rangeAt(ranges, GEOMETRY, 47)).isEqualTo(0);
    assertThat(ChangeMarkerPainter.rangeAt(ranges, GEOMETRY, 100)).isEqualTo(1);
  }

  @Test
  void rangeAtCatchesADeletionWithinAThirdOfALine() {
    List<DiffChange> ranges = List.of(deleted(4));

    assertThat(ChangeMarkerPainter.rangeAt(ranges, GEOMETRY, 64 - 5)).isEqualTo(0);
    assertThat(ChangeMarkerPainter.rangeAt(ranges, GEOMETRY, 64 + 4)).isEqualTo(0);
  }

  @Test
  void rangeAtMissesOutside() {
    List<DiffChange> ranges = List.of(inserted(1, 3), deleted(6));

    assertThat(ChangeMarkerPainter.rangeAt(ranges, GEOMETRY, 5)).isEqualTo(-1);
    assertThat(ChangeMarkerPainter.rangeAt(ranges, GEOMETRY, 48)).isEqualTo(-1);
    assertThat(ChangeMarkerPainter.rangeAt(ranges, GEOMETRY, 96 + 6)).isEqualTo(-1);
  }

  @Test
  void aBlockThatStartsInAFoldShowsOnTheFoldLine() {
    // Lines 3 and 4 are folded under line 2; line 5 shows in row 3.
    LineGeometry folded = new LineGeometry() {
      @Override
      public int lineHeight() {
        return 16;
      }

      @Override
      public int top(int modelLine) {
        if (modelLine <= 2) {
          return modelLine * 16;
        }
        if (modelLine <= 4) {
          return 2 * 16;
        }
        return (modelLine - 2) * 16;
      }

      @Override
      public boolean visible(int modelLine) {
        return modelLine < 3 || modelLine > 4;
      }
    };
    Image image = new Image(display, ChangeMarkerPainter.WIDTH, 200);
    GC gc = new GC(image);
    ImageData data;
    try {
      gc.setBackground(display.getSystemColor(org.eclipse.swt.SWT.COLOR_WHITE));
      gc.fillRectangle(0, 0, ChangeMarkerPainter.WIDTH, 200);
      ChangeMarkerPainter.paint(gc, List.of(inserted(4, 5)), folded, -1, false);
      data = image.getImageData();
    }
    finally {
      gc.dispose();
      image.dispose();
    }

    assertThat(pixel(data, 4, 2 * 16 + 8)).isEqualTo(ADDED);
    assertThat(ChangeMarkerPainter.rangeAt(List.of(inserted(4, 5)), folded, 2 * 16 + 8)).isEqualTo(0);
  }
}
