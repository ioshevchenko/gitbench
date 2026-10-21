package gitbench.ui.log;

import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.part.ViewPart;

/** The Git Log view: the {@link LogPanel} alone, as the Log tab of the IntelliJ Git tool window without branches. */
public final class GitLogView extends ViewPart {
  public static final String ID = "gitbench.views.log";

  private LogPanel panel;

  @Override
  public void createPartControl(Composite parent) {
    panel = new LogPanel(parent, this, this::setContentDescription);
    panel.contributeToolbar(getViewSite().getActionBars().getToolBarManager());
  }

  public LogPanel panel() {
    return panel;
  }

  @Override
  public void setFocus() {
    panel.setFocus();
  }

  @Override
  public void dispose() {
    if (panel != null) {
      panel.dispose();
    }
    super.dispose();
  }
}
