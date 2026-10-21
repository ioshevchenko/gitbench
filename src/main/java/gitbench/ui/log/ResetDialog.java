package gitbench.ui.log;

import gitbench.log.ResetMode;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;

/** The IntelliJ "Git Reset" dialog: Soft, Mixed, Hard, or Keep, with the IntelliJ explanations. Mixed is the default. */
final class ResetDialog extends Dialog {
  private final String targetDescription;
  private ResetMode mode = ResetMode.MIXED;

  ResetDialog(Shell parent, String targetDescription) {
    super(parent);
    this.targetDescription = targetDescription;
  }

  ResetMode mode() {
    return mode;
  }

  @Override
  protected void configureShell(Shell shell) {
    super.configureShell(shell);
    shell.setText("Git Reset");
  }

  @Override
  protected Control createDialogArea(Composite parent) {
    Composite area = (Composite)super.createDialogArea(parent);
    area.setLayout(new GridLayout(1, false));
    Label intro = new Label(area, SWT.WRAP);
    intro.setText("This will reset the current branch head to the selected commit " + targetDescription + ",\n"
                  + "and update the working tree and the index according to the selected mode:");
    for (ResetMode each : ResetMode.values()) {
      Button radio = new Button(area, SWT.RADIO);
      radio.setText(each.title());
      radio.setSelection(each == mode);
      radio.addListener(SWT.Selection, e -> {
        if (radio.getSelection()) {
          mode = each;
        }
      });
      Label description = new Label(area, SWT.WRAP);
      description.setText(each.description());
      GridData data = new GridData(SWT.FILL, SWT.CENTER, true, false);
      data.horizontalIndent = 20;
      data.widthHint = 420;
      description.setLayoutData(data);
      description.setForeground(description.getDisplay().getSystemColor(SWT.COLOR_WIDGET_DISABLED_FOREGROUND));
    }
    return area;
  }

  @Override
  protected void createButtonsForButtonBar(Composite parent) {
    createButton(parent, IDialogConstants.OK_ID, "Reset", true);
    createButton(parent, IDialogConstants.CANCEL_ID, IDialogConstants.CANCEL_LABEL, false);
  }
}
