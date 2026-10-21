package gitbench.ui;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Sash;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SeparatorsTest extends SwtTest {
  /** The color of the pixel at x, y of the control, as it shows on the screen. */
  private RGB pixel(Control control, int x, int y) {
    Image image = new Image(display, control.getSize().x, control.getSize().y);
    GC gc = new GC(control);
    try {
      gc.copyArea(image, 0, 0);
      return image.getImageData().palette.getRGB(image.getImageData().getPixel(x, y));
    }
    finally {
      gc.dispose();
      image.dispose();
    }
  }

  private Sash sashOf(SashForm form) {
    for (Control child : form.getChildren()) {
      if (child instanceof Sash sash) {
        return sash;
      }
    }
    throw new AssertionError("no sash");
  }

  @Test
  void aSashShowsAOnePixelLineInTheMiddle() {
    SashForm form = new SashForm(shell, SWT.HORIZONTAL);
    new Label(form, SWT.NONE);
    new Label(form, SWT.NONE);
    Separators.onePixel(form);
    show();

    Sash sash = sashOf(form);
    int width = sash.getSize().x;
    int middle = width / 2;
    assertThat(pixel(sash, middle, 10)).isEqualTo(Separators.lineColor().getRGB());
    assertThat(pixel(sash, 0, 10)).isNotEqualTo(Separators.lineColor().getRGB());
  }

  @Test
  void aVerticalSashShowsAHorizontalLine() {
    SashForm form = new SashForm(shell, SWT.VERTICAL);
    new Label(form, SWT.NONE);
    new Label(form, SWT.NONE);
    Separators.onePixel(form);
    show();

    Sash sash = sashOf(form);
    assertThat(pixel(sash, 10, sash.getSize().y / 2)).isEqualTo(Separators.lineColor().getRGB());
  }

  @Test
  void aCompositeShowsALineOnItsLeftEdge() {
    Composite composite = new Composite(shell, SWT.NONE);
    composite.setLayout(new GridLayout());
    new Label(composite, SWT.NONE).setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
    Separators.leftLine(composite);
    show();

    assertThat(pixel(composite, 0, 10)).isEqualTo(Separators.lineColor().getRGB());
  }
}
