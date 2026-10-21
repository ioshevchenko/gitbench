package gitbench.ui.merge;

import com.intellij.diff.util.ThreeSide;
import gitbench.ui.GitbenchIcons;
import gitbench.ui.GitbenchIcons.Icon;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;

import java.util.function.Consumer;

/**
 * The toolbar of the merge window, in the order of IntelliJ: Previous and Next Difference, Apply non-conflicting
 * changes from the left, from both sides and from the right, Resolve simple conflicts; on the right the status
 * "N changes, M conflicts".
 */
final class MergeToolbar {
  private final Composite control;
  private final Label status;

  MergeToolbar(Composite parent, Runnable previous, Runnable next, Consumer<ThreeSide> applyNonConflicting, Runnable magic) {
    control = new Composite(parent, SWT.NONE);
    GridLayout layout = new GridLayout(2, false);
    layout.marginWidth = 0;
    layout.marginHeight = 0;
    control.setLayout(layout);
    ToolBar bar = new ToolBar(control, SWT.FLAT);
    button(bar, Icon.PREVIOUS_CHANGE, "Previous Difference (Shift+F7)", previous);
    button(bar, Icon.NEXT_CHANGE, "Next Difference (F7)", next);
    new ToolItem(bar, SWT.SEPARATOR);
    button(bar, Icon.APPLY_NON_CONFLICTS_LEFT, "Apply Non-Conflicting Changes from the Left Side", () -> applyNonConflicting.accept(ThreeSide.LEFT));
    button(bar, Icon.APPLY_NON_CONFLICTS, "Apply All Non-Conflicting Changes", () -> applyNonConflicting.accept(ThreeSide.BASE));
    button(bar, Icon.APPLY_NON_CONFLICTS_RIGHT, "Apply Non-Conflicting Changes from the Right Side", () -> applyNonConflicting.accept(ThreeSide.RIGHT));
    button(bar, Icon.MAGIC_RESOLVE_TOOLBAR, "Resolve Simple Conflicts", magic);
    status = new Label(control, SWT.NONE);
    status.setLayoutData(new GridData(SWT.END, SWT.CENTER, true, false));
  }

  Composite control() {
    return control;
  }

  void setStatus(String text) {
    status.setText(text);
    control.layout(true, true);
  }

  private static void button(ToolBar bar, Icon icon, String tooltip, Runnable action) {
    ToolItem item = new ToolItem(bar, SWT.PUSH);
    item.setImage(GitbenchIcons.get(icon));
    item.setToolTipText(tooltip);
    item.addListener(SWT.Selection, e -> action.run());
  }
}
