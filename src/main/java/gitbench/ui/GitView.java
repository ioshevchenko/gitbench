package gitbench.ui;

import gitbench.ui.GitbenchIcons.Icon;
import gitbench.ui.branch.BranchesPanel;
import gitbench.ui.log.LogPanel;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.ToolItem;
import org.eclipse.ui.part.ViewPart;

/**
 * The Git view: the branches panel on the left and the log on the right in one view, as the Log tab of the IntelliJ
 * Git tool window. Hide Branches collapses the panel to a strip; the view remembers that and the width of the panel.
 */
public final class GitView extends ViewPart {
  public static final String ID = "gitbench.views.git";
  private static final String PREF_SHOW_BRANCHES = "git.showBranches";
  private static final String PREF_BRANCHES_PERCENT = "git.branchesPercent";

  private ExpandableSplit split;
  private BranchesPanel branches;
  private LogPanel log;

  @Override
  public void createPartControl(Composite parent) {
    IPreferenceStore store = Activator.getDefault().getPreferenceStore();
    store.setDefault(PREF_SHOW_BRANCHES, true);
    store.setDefault(PREF_BRANCHES_PERCENT, 30);

    split = new ExpandableSplit(parent, Icon.CHEVRON_RIGHT, "Show Branches", expanded -> store.setValue(PREF_SHOW_BRANCHES, expanded));
    branches = new BranchesPanel(split.controls(), split.sash(), this, this::setContentDescription, new BranchesPanel.LogTarget() {
      @Override
      public LogPanel find() {
        return log;
      }

      @Override
      public LogPanel show() {
        return log;
      }
    });
    log = new LogPanel(split.sash(), this, this::setContentDescription);
    branches.insertItem(0, SWT.PUSH, Icon.CHEVRON_LEFT, "Hide Branches", () -> split.setExpanded(false));
    new ToolItem(branches.toolbar(), SWT.SEPARATOR, 1);
    split.setContents(branches.toolbar(), branches.content(), log.control(), store.getInt(PREF_BRANCHES_PERCENT));
    split.setExpanded(store.getBoolean(PREF_SHOW_BRANCHES));
    branches.content().addListener(SWT.Resize, e -> savePanelWidth());
    log.contributeToolbar(getViewSite().getActionBars().getToolBarManager());
  }

  @Override
  public void setFocus() {
    log.setFocus();
  }

  /** Keeps the width of the expanded panel; the workbench may dispose the widgets before {@link #dispose()}. */
  private void savePanelWidth() {
    int[] weights = split.sash().getWeights();
    int total = weights[0] + weights[1];
    if (split.expanded() && total > 0) {
      Activator.getDefault().getPreferenceStore().setValue(PREF_BRANCHES_PERCENT, Math.max(5, Math.min(95, weights[0] * 100 / total)));
    }
  }

  @Override
  public void dispose() {
    if (branches != null) {
      branches.dispose();
    }
    if (log != null) {
      log.dispose();
    }
    super.dispose();
  }
}
