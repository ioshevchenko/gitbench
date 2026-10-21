package gitbench.ui.log;

import gitbench.log.GitRef;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;

import java.util.List;

/** "Go to Hash/Branch/Tag": a field with completion over the ref names; any hash prefix is accepted too. */
final class GoToRefDialog extends Dialog {
  private final List<GitRef> refs;
  private Combo field;
  private String value;

  GoToRefDialog(Shell parent, List<GitRef> refs) {
    super(parent);
    this.refs = refs;
  }

  /** The ref name or hash prefix the user typed. */
  String value() {
    return value;
  }

  @Override
  protected void configureShell(Shell shell) {
    super.configureShell(shell);
    shell.setText("Go to Hash/Branch/Tag");
  }

  @Override
  protected Control createDialogArea(Composite parent) {
    Composite area = (Composite)super.createDialogArea(parent);
    area.setLayout(new GridLayout(1, false));
    field = new Combo(area, SWT.DROP_DOWN);
    field.setItems(refs.stream().map(GitRef::name).distinct().toArray(String[]::new));
    field.setText("");
    GridData data = new GridData(SWT.FILL, SWT.CENTER, true, false);
    data.widthHint = 360;
    field.setLayoutData(data);
    field.setToolTipText("Enter hash or branch/tag name");
    return area;
  }

  @Override
  protected void createButtonsForButtonBar(Composite parent) {
    createButton(parent, IDialogConstants.OK_ID, "Go", true);
    createButton(parent, IDialogConstants.CANCEL_ID, IDialogConstants.CANCEL_LABEL, false);
  }

  @Override
  protected void okPressed() {
    value = field.getText().trim();
    super.okPressed();
  }
}
