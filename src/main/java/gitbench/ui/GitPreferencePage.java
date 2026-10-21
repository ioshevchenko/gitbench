package gitbench.ui;

import gitbench.git.GitException;
import gitbench.git.GitResult;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.FileFieldEditor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

import java.nio.file.Path;
import java.util.List;

/** Preferences → Gitbench. Sets the path to the git executable. */
public final class GitPreferencePage extends FieldEditorPreferencePage implements IWorkbenchPreferencePage {
  private FileFieldEditor gitPath;

  public GitPreferencePage() {
    super(GRID);
    setDescription("Gitbench runs the git command line tool.");
  }

  @Override
  public void init(IWorkbench workbench) {
    setPreferenceStore(Activator.getDefault().getPreferenceStore());
  }

  @Override
  protected void createFieldEditors() {
    gitPath = new FileFieldEditor(Activator.PREF_GIT_PATH, "Path to Git executable:", getFieldEditorParent());
    addField(gitPath);
    Composite parent = getFieldEditorParent();
    Button test = new Button(parent, SWT.PUSH);
    test.setText("Test");
    test.setLayoutData(new GridData(SWT.END, SWT.CENTER, false, false, 3, 1));
    test.addListener(SWT.Selection, e -> testGit());
  }

  private void testGit() {
    String path = gitPath.getStringValue().isBlank() ? "git" : gitPath.getStringValue();
    try {
      GitResult result = new gitbench.git.GitRunner(() -> path, gitbench.git.GitCommandListener.NONE)
        .run(Path.of(System.getProperty("user.home")), List.of("--version"), null);
      MessageDialog.openInformation(getShell(), "Git", result.stdoutText().trim());
    }
    catch (GitException e) {
      MessageDialog.openError(getShell(), "Git", e.userMessage());
    }
  }
}
