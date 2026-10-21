package gitbench.ui.annotate;

import gitbench.annotate.Blame;
import gitbench.annotate.BlameCommit;
import gitbench.annotate.PrettyDate;
import gitbench.ui.status.LineGeometry;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.widgets.Display;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Paints the annotate column as the IntelliJ annotation gutter: the background of each line by the age of its commit,
 * then the date and the author in two aligned columns. Lines that are not committed stay empty.
 */
public final class AnnotatePainter {
  /** The space left and right of the text. */
  private static final int PADDING = 4;
  /** The space between the date and the author. */
  private static final int GAP = 8;
  /** ANNOTATIONS_COLOR and ANNOTATIONS_LAST_COMMIT_COLOR of the New UI themes. */
  private static final Color LIGHT_TEXT = new Color(0x5A, 0x5D, 0x6B);
  private static final Color DARK_TEXT = new Color(0x8D, 0x91, 0x99);
  private static final Color LIGHT_LAST_COMMIT = new Color(0x00, 0x00, 0x00);
  private static final Color DARK_LAST_COMMIT = new Color(0xCE, 0xD0, 0xD6);

  private final Font font;
  /** The layout of the last blame: the width of the date column and the whole width. */
  private Blame laidOut;
  private int dateWidth;
  private int totalWidth;
  /** The newest commit of the blame, found once per blame. */
  private String lastCommit;
  private final Map<Integer, Color> backgrounds = new java.util.HashMap<>();

  public AnnotatePainter(Font font) {
    this.font = font;
  }

  /** The width that shows the longest date and the longest author of the blame. */
  public int width(Blame blame, Clock clock) {
    layout(blame, clock);
    return totalWidth;
  }

  /** Paints the visible lines; {@code colors} maps a commit hash to 0xRRGGBB, and {@code width} is the column width. */
  public void paint(GC gc, Blame blame, Map<String, Integer> colors, LineGeometry geometry, boolean dark, Clock clock, int width) {
    layout(blame, clock);
    gc.setFont(font);
    int last = Math.min(blame.lineHashes().size() - 1, geometry.lastVisibleLine());
    for (int line = Math.max(0, geometry.firstVisibleLine()); line <= last; line++) {
      if (!geometry.visible(line)) {
        continue;
      }
      Optional<BlameCommit> commit = blame.commitAt(line);
      if (commit.isEmpty()) {
        continue;
      }
      int y = geometry.top(line);
      // Lines differ in height with code minings or line spacing.
      int lineHeight = Math.max(geometry.top(line + 1) - y, geometry.lineHeight());
      Integer rgb = colors.get(commit.get().hash());
      if (rgb != null) {
        gc.setBackground(backgrounds.computeIfAbsent(rgb, value -> new Color(value >> 16, value >> 8 & 0xFF, value & 0xFF)));
        gc.fillRectangle(0, y, width, lineHeight);
      }
      boolean newest = commit.get().hash().equals(lastCommit);
      gc.setForeground(newest ? (dark ? DARK_LAST_COMMIT : LIGHT_LAST_COMMIT) : (dark ? DARK_TEXT : LIGHT_TEXT));
      int textY = y + (lineHeight - gc.getFontMetrics().getHeight()) / 2;
      gc.drawText(PrettyDate.format(commit.get().authorTime(), clock), PADDING, textY, SWT.DRAW_TRANSPARENT);
      gc.drawText(commit.get().author(), PADDING + dateWidth + GAP, textY, SWT.DRAW_TRANSPARENT);
    }
  }

  /** The newest commit of the blame, whose lines IntelliJ shows in ANNOTATIONS_LAST_COMMIT_COLOR. */
  private static String lastCommit(Blame blame) {
    String last = null;
    Instant time = Instant.MIN;
    for (int line = 0; line < blame.lineHashes().size(); line++) {
      Optional<BlameCommit> commit = blame.commitAt(line);
      if (commit.isPresent() && commit.get().authorTime().isAfter(time)) {
        time = commit.get().authorTime();
        last = commit.get().hash();
      }
    }
    return last;
  }

  private void layout(Blame blame, Clock clock) {
    if (blame == laidOut) {
      return;
    }
    GC gc = new GC(Display.getCurrent());
    try {
      gc.setFont(font);
      int dates = 0;
      int authors = 0;
      for (int line = 0; line < blame.lineHashes().size(); line++) {
        Optional<BlameCommit> commit = blame.commitAt(line);
        if (commit.isPresent()) {
          dates = Math.max(dates, gc.textExtent(PrettyDate.format(commit.get().authorTime(), clock)).x);
          authors = Math.max(authors, gc.textExtent(commit.get().author()).x);
        }
      }
      dateWidth = dates;
      totalWidth = PADDING + dates + GAP + authors + PADDING;
      lastCommit = lastCommit(blame);
      laidOut = blame;
    }
    finally {
      gc.dispose();
    }
  }
}
