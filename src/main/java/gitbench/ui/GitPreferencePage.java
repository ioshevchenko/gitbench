package gitbench.ui;

import gitbench.git.GitCommandListener;
import gitbench.git.GitException;
import gitbench.git.GitPathValidator;
import gitbench.git.GitResult;
import gitbench.git.GitRunner;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.StringButtonFieldEditor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

import java.nio.file.Path;
import java.util.List;

/** Preferences → Gitbench. Sets the path to the git executable. */
public final class GitPreferencePage extends FieldEditorPreferencePage implements IWorkbenchPreferencePage {
  private GitPathEditor gitPath;

  public GitPreferencePage() {
    super(GRID);
    setDescription("Gitbench runs the git command line tool. Use \"git\" to take it from PATH.");
  }

  @Override
  public void init(IWorkbench workbench) {
    setPreferenceStore(Activator.getDefault().getPreferenceStore());
  }

  @Override
  protected void createFieldEditors() {
    gitPath = new GitPathEditor(getFieldEditorParent());
    addField(gitPath);
    Button test = new Button(getFieldEditorParent(), SWT.PUSH);
    test.setText("Test");
    test.setLayoutData(new GridData(SWT.END, SWT.CENTER, false, false, 3, 1));
    test.addListener(SWT.Selection, e -> testGit());
  }

  private void testGit() {
    String path = gitPath.getStringValue().isBlank() ? "git" : gitPath.getStringValue().trim();
    try {
      GitResult result = new GitRunner(() -> path, GitCommandListener.NONE)
        .run(Path.of(System.getProperty("user.home")), List.of("--version"), null);
      MessageDialog.openInformation(getShell(), "Git", result.stdoutText().trim());
    }
    catch (GitException e) {
      MessageDialog.openError(getShell(), "Git", e.userMessage());
    }
  }

  /** A text field with a Browse button. Accepts a bare command name, unlike FileFieldEditor. */
  private static final class GitPathEditor extends StringButtonFieldEditor {
    GitPathEditor(Composite parent) {
      super(Activator.PREF_GIT_PATH, "Path to Git executable:", parent);
      setChangeButtonText("Browse...");
      setValidateStrategy(VALIDATE_ON_KEY_STROKE);
    }

    @Override
    protected String changePressed() {
      FileDialog dialog = new FileDialog(getShell(), SWT.OPEN);
      dialog.setFilterExtensions(new String[]{"*.exe", "*"});
      String file = dialog.open();
      return file == null ? null : file;
    }

    @Override
    protected boolean doCheckState() {
      String error = GitPathValidator.error(getStringValue());
      setErrorMessage(error);
      return error == null;
    }
  }
}
