package gitbench.ui.log;

import gitbench.log.CommitDetails;
import gitbench.log.LogModel;
import gitbench.log.LogRow;
import gitbench.ui.Theme;
import org.eclipse.jface.resource.FontDescriptor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The commit table of the Git Log: graph, subject and branch labels in the first column, then author and date.
 * The table is virtual and painted by hand; text details load in batches as rows become visible.
 */
final class CommitTable {
  static final int ROW_HEIGHT = 22;
  private static final int AUTHOR_WIDTH = 150;
  private static final int DATE_WIDTH = 90;
  private static final int CELL_PADDING = 4;
  private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy").withZone(ZoneId.systemDefault());

  /** Gives the loaded details and loads missing ones in the background. */
  interface DetailsSource {
    CommitDetails get(String hash);

    void request(int fromRow, int toRow);
  }

  private final Table table;
  private final DetailsSource details;
  private final Supplier<String> myEmail;
  private final GraphPainter graphPainter = new GraphPainter();
  private final Font boldFont;
  private LogModel model;

  CommitTable(Composite parent, DetailsSource details, Supplier<String> myEmail) {
    this.details = details;
    this.myEmail = myEmail;
    table = new Table(parent, SWT.VIRTUAL | SWT.MULTI | SWT.FULL_SELECTION | SWT.V_SCROLL | SWT.H_SCROLL);
    table.setHeaderVisible(false);
    table.setLinesVisible(false);
    TableColumn subject = new TableColumn(table, SWT.LEFT);
    TableColumn author = new TableColumn(table, SWT.LEFT);
    TableColumn date = new TableColumn(table, SWT.LEFT);
    author.setWidth(AUTHOR_WIDTH);
    date.setWidth(DATE_WIDTH);
    subject.setWidth(400);
    boldFont = FontDescriptor.createFrom(table.getFont()).setStyle(SWT.BOLD).createFont(table.getDisplay());
    table.addListener(SWT.Dispose, e -> boldFont.dispose());
    table.addListener(SWT.Resize, e -> {
      int width = table.getClientArea().width - AUTHOR_WIDTH - DATE_WIDTH;
      subject.setWidth(Math.max(width, 100));
    });
    table.addListener(SWT.SetData, e -> ((TableItem)e.item).setData(table.indexOf((TableItem)e.item)));
    table.addListener(SWT.MeasureItem, e -> e.height = ROW_HEIGHT);
    table.addListener(SWT.EraseItem, e -> e.detail &= ~SWT.FOREGROUND);
    table.addListener(SWT.PaintItem, this::paint);
  }

  Table table() {
    return table;
  }

  LogModel model() {
    return model;
  }

  /** Shows a new model and keeps the selection by hash. */
  void setModel(LogModel newModel) {
    List<String> selected = selectedHashes();
    int top = table.getTopIndex();
    String topHash = model != null && top >= 0 && top < model.rowCount() ? model.row(top).hash() : null;
    model = newModel;
    table.setRedraw(false);
    try {
      table.clearAll();
      table.setItemCount(model.rowCount());
      List<Integer> rows = new ArrayList<>();
      for (String hash : selected) {
        int row = model.rowOf(hash);
        if (row >= 0) {
          rows.add(row);
        }
      }
      table.setSelection(rows.stream().mapToInt(Integer::intValue).toArray());
      if (topHash != null && model.rowOf(topHash) >= 0) {
        table.setTopIndex(model.rowOf(topHash));
      }
    }
    finally {
      table.setRedraw(true);
    }
  }

  List<String> selectedHashes() {
    List<String> hashes = new ArrayList<>();
    if (model == null) {
      return hashes;
    }
    for (int row : table.getSelectionIndices()) {
      if (row < model.rowCount()) {
        hashes.add(model.row(row).hash());
      }
    }
    return hashes;
  }

  /** Selects and shows the commit. Returns false when the commit is not in the table. */
  boolean select(String hash) {
    if (model == null) {
      return false;
    }
    int row = model.rowOf(hash);
    if (row < 0) {
      return false;
    }
    table.setSelection(row);
    table.showSelection();
    table.notifyListeners(SWT.Selection, new Event());
    return true;
  }

  private void paint(Event event) {
    if (model == null || !(event.item instanceof TableItem item)) {
      return;
    }
    int rowIndex = table.indexOf(item);
    if (rowIndex < 0 || rowIndex >= model.rowCount()) {
      return;
    }
    LogRow row = model.row(rowIndex);
    CommitDetails d = details.get(row.hash());
    if (d == null) {
      details.request(rowIndex, rowIndex + 1);
    }
    GC gc = event.gc;
    Rectangle cell = item.getBounds(event.index);
    boolean selected = (event.detail & SWT.SELECTED) != 0;
    boolean dark = Theme.isDark();
    Color normal = selected ? table.getDisplay().getSystemColor(SWT.COLOR_LIST_SELECTION_TEXT) : table.getForeground();
    Color pale = table.getDisplay().getSystemColor(SWT.COLOR_WIDGET_DISABLED_FOREGROUND);
    Color text = selected || model.inCurrentBranch(row.commitIndex()) ? normal : pale;
    gc.setForeground(text);
    switch (event.index) {
      case 0 -> paintSubject(gc, cell, row, d, selected, dark, pale);
      case 1 -> {
        boolean mine = d != null && d.authorEmail().equalsIgnoreCase(String.valueOf(myEmail.get()));
        gc.setFont(mine ? boldFont : table.getFont());
        drawText(gc, d == null ? "" : d.authorName(), cell.x + CELL_PADDING, cell, cell.width - CELL_PADDING);
        gc.setFont(table.getFont());
      }
      case 2 -> drawText(gc, d == null ? "" : DATE.format(d.authorTime()), cell.x + CELL_PADDING, cell, cell.width - CELL_PADDING);
      default -> {
      }
    }
  }

  private void paintSubject(GC gc, Rectangle cell, LogRow row, CommitDetails d, boolean selected, boolean dark, Color pale) {
    int graphWidth = GraphPainter.width(model.recommendedWidth(), cell.height);
    Color background = selected ? table.getDisplay().getSystemColor(SWT.COLOR_LIST_SELECTION) : table.getBackground();
    graphPainter.paint(gc, new Rectangle(cell.x, cell.y, graphWidth, cell.height), row.printElements(), dark, background);
    int textX = cell.x + graphWidth + CELL_PADDING;
    int right = cell.x + cell.width - CELL_PADDING;
    int labelsWidth = RefLabelPainter.width(gc, row.refs(), Math.max(0, (right - textX) / 2));
    if (labelsWidth > 0) {
      RefLabelPainter.paint(gc, row.refs(), right, cell.y, cell.height, labelsWidth, selected ? gc.getForeground() : pale);
    }
    if (d == null) {
      Color old = gc.getForeground();
      gc.setForeground(pale);
      drawText(gc, "Loading...", textX, cell, right - labelsWidth - textX);
      gc.setForeground(old);
    }
    else {
      drawText(gc, d.subject(), textX, cell, right - labelsWidth - textX);
    }
  }

  private static void drawText(GC gc, String text, int x, Rectangle cell, int available) {
    if (available <= 0 || text.isEmpty()) {
      return;
    }
    String shown = text;
    if (gc.textExtent(shown).x > available) {
      int length = shown.length();
      while (length > 1 && gc.textExtent(shown.substring(0, length) + "…").x > available) {
        length = length * 9 / 10;
      }
      shown = shown.substring(0, Math.max(1, length)) + "…";
    }
    Point extent = gc.textExtent(shown);
    gc.drawText(shown, x, cell.y + (cell.height - extent.y) / 2, true);
  }
}
