package gitbench.ui.diff;

import gitbench.diff.HighlightPolicy;
import gitbench.diff.IgnorePolicy;
import gitbench.ui.GitbenchIcons;
import gitbench.ui.GitbenchIcons.Icon;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;

/**
 * The diff toolbar, in the order of the IntelliJ diff window: Previous and Next Difference, Previous and Next File,
 * the viewer choice; on the right the number of differences and the settings gear with Ignore and Highlighting.
 */
final class DiffToolbar {
  /** What the toolbar buttons do. */
  interface Actions {
    void previousDifference();

    void nextDifference();

    void previousFile();

    void nextFile();

    void viewerChanged(DiffSettings.Viewer viewer);

    void ignoreChanged(IgnorePolicy policy);

    void highlightChanged(HighlightPolicy policy);
  }

  private final Composite control;
  private final ToolItem previousFile;
  private final ToolItem nextFile;
  private final ToolItem viewerItem;
  private final Label count;

  DiffToolbar(Composite parent, Actions actions) {
    control = new Composite(parent, SWT.NONE);
    GridLayout layout = new GridLayout(3, false);
    layout.marginHeight = 0;
    layout.marginWidth = 0;
    control.setLayout(layout);

    ToolBar left = new ToolBar(control, SWT.FLAT | SWT.RIGHT);
    left.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false));
    button(left, Icon.PREVIOUS_CHANGE, "Previous Difference (Shift+F7)", actions::previousDifference);
    button(left, Icon.NEXT_CHANGE, "Next Difference (F7)", actions::nextDifference);
    new ToolItem(left, SWT.SEPARATOR);
    previousFile = button(left, Icon.PREVIOUS_FILE, "Compare Previous File (Alt+Shift+Left)", actions::previousFile);
    nextFile = button(left, Icon.NEXT_FILE, "Compare Next File (Alt+Shift+Right)", actions::nextFile);
    new ToolItem(left, SWT.SEPARATOR);
    viewerItem = new ToolItem(left, SWT.DROP_DOWN);
    viewerItem.setText(DiffSettings.viewer().label());
    Menu viewerMenu = new Menu(left);
    viewerItem.addListener(SWT.Selection, e -> {
      Menu menu = clear(viewerMenu);
      for (DiffSettings.Viewer viewer : DiffSettings.Viewer.values()) {
        radio(menu, viewer.label(), viewer == DiffSettings.viewer(), () -> {
          viewerItem.setText(viewer.label());
          control.layout(true, true);
          actions.viewerChanged(viewer);
        });
      }
      show(menu, viewerItem);
    });

    count = new Label(control, SWT.NONE);
    count.setLayoutData(new GridData(SWT.END, SWT.CENTER, true, false));

    ToolBar right = new ToolBar(control, SWT.FLAT);
    ToolItem gear = new ToolItem(right, SWT.PUSH);
    gear.setImage(GitbenchIcons.get(Icon.SETTINGS));
    gear.setToolTipText("Settings");
    Menu gearMenu = new Menu(right);
    gear.addListener(SWT.Selection, e -> {
      Menu menu = clear(gearMenu);
      header(menu, "Ignore Differences");
      for (IgnorePolicy policy : IgnorePolicy.values()) {
        radio(menu, policy.label(), policy == DiffSettings.ignore(), () -> actions.ignoreChanged(policy));
      }
      new MenuItem(menu, SWT.SEPARATOR);
      header(menu, "Highlighting Differences");
      for (HighlightPolicy policy : HighlightPolicy.values()) {
        radio(menu, policy.label(), policy == DiffSettings.highlight(), () -> actions.highlightChanged(policy));
      }
      show(menu, gear);
    });
  }

  Composite control() {
    return control;
  }

  void setFileNavigation(boolean visible, boolean hasPrevious, boolean hasNext) {
    previousFile.setEnabled(visible && hasPrevious);
    nextFile.setEnabled(visible && hasNext);
  }

  /** Shows "N differences" as IntelliJ diff.count.differences.status.text, or a status such as "Differences ignored". */
  void setCount(int differences, String status) {
    count.setText(status != null ? status
                  : differences == 0 ? "No differences" : differences == 1 ? "1 difference" : differences + " differences");
    control.layout(true, true);
  }

  private static ToolItem button(ToolBar bar, Icon icon, String tooltip, Runnable action) {
    ToolItem item = new ToolItem(bar, SWT.PUSH);
    item.setImage(GitbenchIcons.get(icon));
    item.setToolTipText(tooltip);
    item.addListener(SWT.Selection, e -> action.run());
    return item;
  }

  private static void radio(Menu menu, String text, boolean selected, Runnable action) {
    MenuItem item = new MenuItem(menu, SWT.RADIO);
    item.setText(text);
    item.setSelection(selected);
    item.addListener(SWT.Selection, e -> {
      if (item.getSelection()) {
        action.run();
      }
    });
  }

  private static void header(Menu menu, String text) {
    MenuItem item = new MenuItem(menu, SWT.PUSH);
    item.setText(text);
    item.setEnabled(false);
  }

  private static void show(Menu menu, ToolItem item) {
    Rectangle bounds = item.getBounds();
    Point location = item.getParent().toDisplay(bounds.x, bounds.y + bounds.height);
    menu.setLocation(location);
    menu.setVisible(true);
  }

  /** Removes the old items: a menu is built again on each click, because the settings change in other tabs too. */
  private static Menu clear(Menu menu) {
    for (MenuItem item : menu.getItems()) {
      item.dispose();
    }
    return menu;
  }
}
