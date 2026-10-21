package gitbench.ui.log;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

/** "Create New Tag On Commit": a name, and an optional message that makes the tag annotated. */
final class NewTagDialog extends Dialog {
  private final String commit;
  private Text nameField;
  private Text messageField;
  private String name;
  private String message;

  NewTagDialog(Shell parent, String commit) {
    super(parent);
    this.commit = commit;
  }

  String name() {
    return name;
  }

  String message() {
    return message;
  }

  @Override
  protected void configureShell(Shell shell) {
    super.configureShell(shell);
    shell.setText("Create New Tag On " + commit);
  }

  @Override
  protected Control createDialogArea(Composite parent) {
    Composite area = (Composite)super.createDialogArea(parent);
    area.setLayout(new GridLayout(2, false));
    new Label(area, SWT.NONE).setText("New tag name:");
    nameField = new Text(area, SWT.BORDER);
    GridData data = new GridData(SWT.FILL, SWT.CENTER, true, false);
    data.widthHint = 300;
    nameField.setLayoutData(data);
    nameField.addListener(SWT.Modify, e -> getButton(IDialogConstants.OK_ID).setEnabled(!nameField.getText().isBlank()));
    new Label(area, SWT.NONE).setText("Message:");
    messageField = new Text(area, SWT.BORDER);
    messageField.setMessage("Optional; makes an annotated tag");
    messageField.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    return area;
  }

  @Override
  protected void createButtonsForButtonBar(Composite parent) {
    createButton(parent, IDialogConstants.OK_ID, "Create Tag", true).setEnabled(false);
    createButton(parent, IDialogConstants.CANCEL_ID, IDialogConstants.CANCEL_LABEL, false);
  }

  @Override
  protected void okPressed() {
    name = nameField.getText().trim();
    message = messageField.getText().trim();
    super.okPressed();
  }
}
