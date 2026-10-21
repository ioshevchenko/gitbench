package gitbench.ui.commit;

import gitbench.git.ChangeKind;
import gitbench.git.FileChange;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.List;
import org.eclipse.swt.widgets.Shell;

/** Confirms a rollback, as the IntelliJ "Rollback Changes" dialog does. */
final class RollbackDialog extends Dialog {
  private final java.util.List<FileChange> changes;
  private boolean deleteAddedFiles;

  RollbackDialog(Shell parent, java.util.List<FileChange> changes) {
    super(parent);
    this.changes = changes;
  }

  boolean deleteAddedFiles() {
    return deleteAddedFiles;
  }

  @Override
  protected void configureShell(Shell shell) {
    super.configureShell(shell);
    shell.setText("Rollback Changes");
  }

  @Override
  protected boolean isResizable() {
    return true;
  }

  @Override
  protected Control createDialogArea(Composite parent) {
    Composite area = (Composite)super.createDialogArea(parent);
    Label label = new Label(area, SWT.NONE);
    label.setText(changes.size() == 1 ? "Rollback 1 file:" : "Rollback " + changes.size() + " files:");
    List list = new List(area, SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL);
    GridData data = new GridData(SWT.FILL, SWT.FILL, true, true);
    data.heightHint = 160;
    data.widthHint = 420;
    list.setLayoutData(data);
    changes.forEach(change -> list.add(change.path()));
    if (changes.stream().anyMatch(c -> c.kind() == ChangeKind.ADDED || c.kind() == ChangeKind.RENAMED)) {
      Button delete = new Button(area, SWT.CHECK);
      delete.setText("Delete local copies of added files");
      delete.addListener(SWT.Selection, e -> deleteAddedFiles = delete.getSelection());
    }
    return area;
  }

  @Override
  protected void createButtonsForButtonBar(Composite parent) {
    createButton(parent, IDialogConstants.OK_ID, "Rollback", true);
    createButton(parent, IDialogConstants.CANCEL_ID, IDialogConstants.CANCEL_LABEL, false);
  }
}
