package gitbench.ui.branch;

import gitbench.ui.log.GitLogView;
import gitbench.ui.log.LogPanel;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.part.ViewPart;

/** The Branches view: the {@link BranchesPanel} alone. Selecting branches filters the Git Log view. */
public final class BranchesView extends ViewPart {
  public static final String ID = "gitbench.views.branches";

  private BranchesPanel panel;

  @Override
  public void createPartControl(Composite parent) {
    Composite root = new Composite(parent, SWT.NONE);
    GridLayout layout = new GridLayout(2, false);
    layout.marginWidth = 0;
    layout.marginHeight = 0;
    layout.horizontalSpacing = 0;
    root.setLayout(layout);
    panel = new BranchesPanel(root, root, this, this::setContentDescription, new BranchesPanel.LogTarget() {
      @Override
      public LogPanel find() {
        IViewPart view = getSite().getPage().findView(GitLogView.ID);
        return view instanceof GitLogView log ? log.panel() : null;
      }

      @Override
      public LogPanel show() throws org.eclipse.ui.PartInitException {
        return ((GitLogView)getSite().getPage().showView(GitLogView.ID)).panel();
      }
    });
    panel.toolbar().setLayoutData(new GridData(SWT.CENTER, SWT.TOP, false, true));
    panel.content().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
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
