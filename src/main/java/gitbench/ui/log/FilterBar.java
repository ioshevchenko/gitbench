package gitbench.ui.log;

import gitbench.log.GitRef;
import gitbench.log.LogFilter;
import gitbench.log.RefType;
import gitbench.ui.GitbenchIcons;
import gitbench.ui.GitbenchIcons.Icon;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.DateTime;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The filter bar of the Git Log, as in IntelliJ: "Text or hash" with regex and Match Case toggles inside the field,
 * then the Branch, User, Date and Paths filters, each with a × to clear it, and the IntelliSort toggle.
 */
final class FilterBar {
  private static final int TEXT_DELAY_MS = 300;
  private static final int MAX_MENU_ITEMS = 40;

  /** Receives the user changes. */
  interface Listener {
    void filterChanged(LogFilter filter);

    void intelliSortChanged(boolean on);

    void rangeCleared();
  }

  private final Composite control;
  private final Listener listener;
  private final Supplier<List<GitRef>> refs;
  private final Supplier<List<String>> authors;
  private final Supplier<String> me;
  private final Supplier<Path> repositoryRoot;
  private final Text text;
  private final ToolItem regex;
  private final ToolItem matchCase;
  private final FilterButton branch;
  private final FilterButton user;
  private final FilterButton date;
  private final FilterButton paths;
  private final FilterButton range;
  private final ToolItem intelliSort;
  private LogFilter filter = LogFilter.none();
  private int textGeneration;

  FilterBar(Composite parent, Listener listener, Supplier<List<GitRef>> refs, Supplier<List<String>> authors,
            Supplier<String> me, Supplier<Path> repositoryRoot) {
    this.listener = listener;
    this.refs = refs;
    this.authors = authors;
    this.me = me;
    this.repositoryRoot = repositoryRoot;
    control = new Composite(parent, SWT.NONE);
    GridLayout layout = new GridLayout(8, false);
    layout.marginHeight = 3;
    layout.marginWidth = 4;
    layout.horizontalSpacing = 10;
    control.setLayout(layout);

    Composite search = new Composite(control, SWT.BORDER);
    GridLayout searchLayout = new GridLayout(3, false);
    searchLayout.marginHeight = 0;
    searchLayout.marginWidth = 2;
    searchLayout.horizontalSpacing = 2;
    search.setLayout(searchLayout);
    GridData searchData = new GridData(SWT.LEFT, SWT.CENTER, false, false);
    searchData.widthHint = 220;
    search.setLayoutData(searchData);
    Label searchIcon = new Label(search, SWT.NONE);
    searchIcon.setImage(GitbenchIcons.get(Icon.SEARCH));
    text = new Text(search, SWT.SINGLE);
    text.setMessage("Text or hash");
    text.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    text.addListener(SWT.Modify, e -> scheduleTextChange());
    text.addListener(SWT.DefaultSelection, e -> applyText());
    ToolBar toggles = new ToolBar(search, SWT.FLAT);
    regex = toggle(toggles, Icon.REGEX, "Regex");
    matchCase = toggle(toggles, Icon.MATCH_CASE, "Match Case");

    branch = new FilterButton(control, "Branch", this::showBranchMenu, () -> update(filter.withBranches(List.of())));
    user = new FilterButton(control, "User", this::showUserMenu, () -> update(filter.withUsers(List.of())));
    date = new FilterButton(control, "Date", this::showDateMenu, () -> update(filter.withDates(null, null)));
    paths = new FilterButton(control, "Paths", this::showPathsMenu, () -> update(filter.withPaths(List.of())));
    range = new FilterButton(control, "Compare", point -> { }, listener::rangeCleared);
    range.setVisible(false);

    ToolBar sortBar = new ToolBar(control, SWT.FLAT);
    intelliSort = new ToolItem(sortBar, SWT.CHECK);
    intelliSort.setImage(GitbenchIcons.get(Icon.SORT));
    intelliSort.setToolTipText("IntelliSort: show incoming commits below the merge");
    intelliSort.addListener(SWT.Selection, e -> listener.intelliSortChanged(intelliSort.getSelection()));
    new Label(control, SWT.NONE).setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    updateButtons();
  }

  Composite control() {
    return control;
  }

  LogFilter filter() {
    return filter;
  }

  /** Sets the filter from outside, for example from the Branches view, without a callback loop. */
  void setFilter(LogFilter newFilter) {
    filter = newFilter;
    updateButtons();
  }

  void setIntelliSort(boolean on) {
    intelliSort.setSelection(on);
  }

  /** Shows "Compare: label ×" while a range is active, or hides it for {@code null}. */
  void setRange(String label) {
    range.setVisible(label != null);
    if (label != null) {
      range.setText("Compare: " + label, true);
    }
    control.layout(true, true);
  }

  void focusText() {
    text.setFocus();
    text.selectAll();
  }

  private ToolItem toggle(ToolBar bar, Icon icon, String tip) {
    ToolItem item = new ToolItem(bar, SWT.CHECK);
    item.setImage(GitbenchIcons.get(icon));
    item.setToolTipText(tip);
    item.addListener(SWT.Selection, e -> applyText());
    return item;
  }

  private void scheduleTextChange() {
    int generation = ++textGeneration;
    control.getDisplay().timerExec(TEXT_DELAY_MS, () -> {
      if (!control.isDisposed() && generation == textGeneration) {
        applyText();
      }
    });
  }

  private void applyText() {
    String value = text.getText().trim();
    update(filter.withText(value.isEmpty() ? null : value, regex.getSelection(), matchCase.getSelection()));
  }

  private void update(LogFilter newFilter) {
    if (newFilter.equals(filter)) {
      return;
    }
    filter = newFilter;
    updateButtons();
    listener.filterChanged(filter);
  }

  private void updateButtons() {
    branch.setText(filter.branches().isEmpty() ? "Branch" : "Branch: " + String.join(", ", filter.branches()), !filter.branches().isEmpty());
    user.setText(filter.users().isEmpty() ? "User" : "User: " + String.join(", ", filter.users()), !filter.users().isEmpty());
    boolean hasDate = filter.since() != null || filter.until() != null;
    date.setText(hasDate ? "Date: " + (filter.since() == null ? "…" : filter.since()) + " – " + (filter.until() == null ? "…" : filter.until()) : "Date", hasDate);
    paths.setText(filter.paths().isEmpty() ? "Paths" : "Paths: " + String.join(", ", filter.paths()), !filter.paths().isEmpty());
    control.layout(true, true);
  }

  private void showBranchMenu(Point at) {
    Menu menu = new Menu(control.getShell(), SWT.POP_UP);
    List<GitRef> all = refs.get();
    for (RefType type : List.of(RefType.LOCAL_BRANCH, RefType.REMOTE_BRANCH, RefType.TAG)) {
      List<GitRef> ofType = all.stream().filter(ref -> ref.type() == type).limit(MAX_MENU_ITEMS).toList();
      if (ofType.isEmpty()) {
        continue;
      }
      if (menu.getItemCount() > 0) {
        new MenuItem(menu, SWT.SEPARATOR);
      }
      for (GitRef ref : ofType) {
        MenuItem item = new MenuItem(menu, SWT.CHECK);
        item.setText(ref.name());
        item.setSelection(filter.branches().contains(ref.name()));
        item.addListener(SWT.Selection, e -> {
          List<String> selected = new ArrayList<>(filter.branches());
          if (item.getSelection()) {
            selected.add(ref.name());
          }
          else {
            selected.remove(ref.name());
          }
          update(filter.withBranches(selected));
        });
      }
    }
    open(menu, at);
  }

  private void showUserMenu(Point at) {
    Menu menu = new Menu(control.getShell(), SWT.POP_UP);
    List<String> names = new ArrayList<>();
    if (me.get() != null) {
      names.add(me.get());
    }
    authors.get().stream().filter(name -> !names.contains(name)).limit(MAX_MENU_ITEMS).forEach(names::add);
    for (String name : names) {
      MenuItem item = new MenuItem(menu, SWT.CHECK);
      item.setText(name.equals(me.get()) ? "me (" + name + ")" : name);
      item.setSelection(filter.users().contains(name));
      item.addListener(SWT.Selection, e -> {
        List<String> selected = new ArrayList<>(filter.users());
        if (item.getSelection()) {
          selected.add(name);
        }
        else {
          selected.remove(name);
        }
        update(filter.withUsers(selected));
      });
    }
    open(menu, at);
  }

  private void showDateMenu(Point at) {
    Menu menu = new Menu(control.getShell(), SWT.POP_UP);
    LocalDate today = LocalDate.now();
    addItem(menu, "Last 24 hours", () -> update(filter.withDates(today.minusDays(1), null)));
    addItem(menu, "Last 7 days", () -> update(filter.withDates(today.minusDays(7), null)));
    new MenuItem(menu, SWT.SEPARATOR);
    addItem(menu, "Select...", () -> {
      DateRangeDialog dialog = new DateRangeDialog(control.getShell(), filter.since(), filter.until());
      if (dialog.open() == IDialogConstants.OK_ID) {
        update(filter.withDates(dialog.since, dialog.until));
      }
    });
    open(menu, at);
  }

  private void showPathsMenu(Point at) {
    Menu menu = new Menu(control.getShell(), SWT.POP_UP);
    addItem(menu, "Select Folder...", () -> {
      Path root = repositoryRoot.get();
      if (root == null) {
        return;
      }
      DirectoryDialog dialog = new DirectoryDialog(control.getShell());
      dialog.setFilterPath(root.toString());
      String chosen = dialog.open();
      if (chosen != null && Path.of(chosen).startsWith(root)) {
        String relative = root.relativize(Path.of(chosen)).toString().replace(java.io.File.separatorChar, '/');
        List<String> selected = new ArrayList<>(filter.paths());
        if (!relative.isEmpty() && !selected.contains(relative)) {
          selected.add(relative);
        }
        update(filter.withPaths(selected));
      }
    });
    open(menu, at);
  }

  private static void addItem(Menu menu, String text, Runnable action) {
    MenuItem item = new MenuItem(menu, SWT.PUSH);
    item.setText(text);
    item.addListener(SWT.Selection, e -> action.run());
  }

  private static void open(Menu menu, Point at) {
    menu.addListener(SWT.Hide, e -> menu.getDisplay().asyncExec(menu::dispose));
    menu.setLocation(at);
    menu.setVisible(true);
  }

  /** A filter label with a drop-down arrow, and a × to clear it when the filter is set. */
  private static final class FilterButton {
    private final Composite composite;
    private final Label label;
    private final Label arrow;
    private final Label clear;

    FilterButton(Composite parent, String text, Consumer<Point> onOpen, Runnable onClear) {
      composite = new Composite(parent, SWT.NONE);
      GridLayout layout = new GridLayout(3, false);
      layout.marginWidth = 0;
      layout.marginHeight = 0;
      layout.horizontalSpacing = 1;
      composite.setLayout(layout);
      composite.setLayoutData(new GridData());
      label = new Label(composite, SWT.NONE);
      label.setText(text);
      arrow = new Label(composite, SWT.NONE);
      arrow.setImage(GitbenchIcons.get(Icon.CHEVRON_DOWN));
      arrow.setLayoutData(new GridData());
      clear = new Label(composite, SWT.NONE);
      clear.setImage(GitbenchIcons.get(Icon.CLOSE));
      clear.setLayoutData(new GridData());
      clear.setToolTipText("Clear filter");
      for (Control c : new Control[]{label, arrow}) {
        c.setCursor(c.getDisplay().getSystemCursor(SWT.CURSOR_HAND));
        c.addListener(SWT.MouseUp, e -> onOpen.accept(composite.toDisplay(0, composite.getSize().y)));
      }
      clear.setCursor(clear.getDisplay().getSystemCursor(SWT.CURSOR_HAND));
      clear.addListener(SWT.MouseUp, e -> onClear.run());
    }

    void setText(String text, boolean active) {
      label.setText(text);
      label.setForeground(active ? null : label.getDisplay().getSystemColor(SWT.COLOR_WIDGET_DISABLED_FOREGROUND));
      ((GridData)arrow.getLayoutData()).exclude = active;
      arrow.setVisible(!active);
      ((GridData)clear.getLayoutData()).exclude = !active;
      clear.setVisible(active);
    }

    void setVisible(boolean visible) {
      composite.setVisible(visible);
      ((GridData)composite.getLayoutData()).exclude = !visible;
    }
  }

  /** Picks a date range with two calendars. */
  private static final class DateRangeDialog extends Dialog {
    private LocalDate since;
    private LocalDate until;
    private DateTime fromField;
    private DateTime toField;

    DateRangeDialog(Shell parent, LocalDate since, LocalDate until) {
      super(parent);
      this.since = since;
      this.until = until;
    }

    @Override
    protected void configureShell(Shell shell) {
      super.configureShell(shell);
      shell.setText("Select Period");
    }

    @Override
    protected Control createDialogArea(Composite parent) {
      Composite area = (Composite)super.createDialogArea(parent);
      area.setLayout(new GridLayout(2, false));
      new Label(area, SWT.NONE).setText("From:");
      fromField = new DateTime(area, SWT.DATE | SWT.DROP_DOWN);
      new Label(area, SWT.NONE).setText("To:");
      toField = new DateTime(area, SWT.DATE | SWT.DROP_DOWN);
      LocalDate from = since == null ? LocalDate.now().minusDays(7) : since;
      LocalDate to = until == null ? LocalDate.now() : until;
      fromField.setDate(from.getYear(), from.getMonthValue() - 1, from.getDayOfMonth());
      toField.setDate(to.getYear(), to.getMonthValue() - 1, to.getDayOfMonth());
      return area;
    }

    @Override
    protected void okPressed() {
      since = LocalDate.of(fromField.getYear(), fromField.getMonth() + 1, fromField.getDay());
      until = LocalDate.of(toField.getYear(), toField.getMonth() + 1, toField.getDay());
      super.okPressed();
    }
  }
}
