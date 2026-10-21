package gitbench.ui;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Shell;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

/**
 * A base for the UI tests: a real SWT shell on the desktop, and helpers that pump events and click widgets. The
 * tests run on the thread that made the display, as SWT requires.
 */
public abstract class SwtTest {
  protected Display display;
  protected Shell shell;

  @BeforeEach
  void openShell() {
    display = Display.getDefault();
    shell = new Shell(display);
    shell.setLayout(new FillLayout());
    shell.setSize(1000, 600);
  }

  @AfterEach
  void closeShell() {
    for (Shell open : display.getShells()) {
      open.dispose();
    }
  }

  /** Shows the shell and lets the widgets lay out and paint. */
  protected void show() {
    shell.layout(true, true);
    shell.open();
    pump();
  }

  /** Runs the pending events, layouts and paints. */
  protected void pump() {
    for (int i = 0; i < 20; i++) {
      while (display.readAndDispatch()) {
        // Keep going until the queue is empty.
      }
      try {
        Thread.sleep(10);
      }
      catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
      }
    }
  }

  /** Repaints the control at once, so the buttons it paints are in place. */
  protected void paint(Control control) {
    control.redraw();
    control.update();
    pump();
  }

  /** A left mouse click at a point of the control. */
  protected void click(Control control, int x, int y) {
    Event down = new Event();
    down.button = 1;
    down.x = x;
    down.y = y;
    control.notifyListeners(SWT.MouseDown, down);
    pump();
  }

  /** The first control of the type under the composite whose text is {@code text}, for buttons. */
  protected static <T extends Control> T find(Composite root, Class<T> type, String text) {
    for (Control child : root.getChildren()) {
      if (type.isInstance(child) && child instanceof org.eclipse.swt.widgets.Button button && button.getText().equals(text)) {
        return type.cast(child);
      }
      if (child instanceof Composite composite) {
        T found = find(composite, type, text);
        if (found != null) {
          return found;
        }
      }
    }
    return null;
  }
}
