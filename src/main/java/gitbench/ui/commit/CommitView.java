package gitbench.ui.commit;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.ui.part.ViewPart;

/** The Commit view. It shows the local changes and the commit message panel. */
public final class CommitView extends ViewPart {
  public static final String ID = "gitbench.views.commit";

  private Label label;

  @Override
  public void createPartControl(Composite parent) {
    label = new Label(parent, SWT.NONE);
    label.setText("Gitbench");
  }

  @Override
  public void setFocus() {
    label.setFocus();
  }
}
