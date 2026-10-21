package gitbench.ui.branch;

import gitbench.git.GitException;
import gitbench.git.GitRepository;
import gitbench.git.GitResult;
import gitbench.git.GitRunner;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import java.util.List;
import java.util.Set;

/** "Create New Branch from &lt;ref&gt;", as in IntelliJ: a name checked by git, Checkout branch, Overwrite existing branch. */
public final class NewBranchDialog extends TitleAreaDialog {
  private final GitRunner runner;
  private final GitRepository repository;
  private final String startPoint;
  private final Set<String> existingBranches;
  private final String initialName;
  private Text nameField;
  private Button checkoutBox;
  private Button overwriteBox;
  private String name;
  private boolean checkout = true;
  private boolean overwrite;

  public NewBranchDialog(Shell parent, GitRunner runner, GitRepository repository, String startPoint,
                         Set<String> existingBranches, String initialName) {
    super(parent);
    this.runner = runner;
    this.repository = repository;
    this.startPoint = startPoint;
    this.existingBranches = existingBranches;
    this.initialName = initialName == null ? "" : initialName;
  }

  public String name() {
    return name;
  }

  public boolean checkout() {
    return checkout;
  }

  public boolean overwrite() {
    return overwrite;
  }

  @Override
  protected void configureShell(Shell shell) {
    super.configureShell(shell);
    shell.setText("Create New Branch");
  }

  @Override
  protected Control createDialogArea(Composite parent) {
    Composite area = (Composite)super.createDialogArea(parent);
    setTitle("Create New Branch from " + startPoint);
    Composite body = new Composite(area, SWT.NONE);
    body.setLayout(new GridLayout(2, false));
    body.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
    new Label(body, SWT.NONE).setText("New branch name:");
    nameField = new Text(body, SWT.BORDER);
    GridData nameData = new GridData(SWT.FILL, SWT.CENTER, true, false);
    nameData.widthHint = 320;
    nameField.setLayoutData(nameData);
    nameField.setText(initialName);
    checkoutBox = new Button(body, SWT.CHECK);
    checkoutBox.setText("Checkout branch");
    checkoutBox.setSelection(true);
    checkoutBox.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 2, 1));
    overwriteBox = new Button(body, SWT.CHECK);
    overwriteBox.setText("Overwrite existing branch");
    overwriteBox.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 2, 1));
    overwriteBox.addListener(SWT.Selection, e -> validate());
    nameField.addListener(SWT.Modify, e -> validate());
    return area;
  }

  @Override
  protected void createButtonsForButtonBar(Composite parent) {
    createButton(parent, IDialogConstants.OK_ID, "Create", true);
    createButton(parent, IDialogConstants.CANCEL_ID, IDialogConstants.CANCEL_LABEL, false);
    validate();
  }

  private void validate() {
    String value = nameField.getText().trim();
    boolean exists = existingBranches.contains(value);
    overwriteBox.setVisible(exists);
    String error = null;
    if (value.isEmpty()) {
      error = "";
    }
    else if (!validName(value)) {
      error = "Invalid branch name: " + value;
    }
    else if (exists && !overwriteBox.getSelection()) {
      error = "Branch " + value + " already exists";
    }
    setErrorMessage(error == null || error.isEmpty() ? null : error);
    if (getButton(IDialogConstants.OK_ID) != null) {
      getButton(IDialogConstants.OK_ID).setEnabled(error == null);
    }
  }

  private boolean validName(String value) {
    try {
      GitResult result = runner.run(repository.root(), List.of("check-ref-format", "--branch", value), null);
      return result.ok();
    }
    catch (GitException e) {
      return false;
    }
  }

  @Override
  protected void okPressed() {
    name = nameField.getText().trim();
    checkout = checkoutBox.getSelection();
    overwrite = overwriteBox.getSelection() && existingBranches.contains(name);
    super.okPressed();
  }
}
