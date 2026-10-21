package gitbench.ui.commit;

import gitbench.git.CommitOptions;
import gitbench.ui.GitbenchIcons;
import gitbench.ui.GitbenchIcons.Icon;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;

import java.util.function.BooleanSupplier;

/** The lower part of the Commit view: Amend, the commit message, Commit, Commit and Push, and the options gear. */
final class CommitPanel {
  /** Receives the user actions of the panel. */
  interface Listener {
    void commit(boolean andPush);

    void amendChanged(boolean amend);

    void showMessageHistory(Point screenLocation);
  }

  private final Composite control;
  private final Button amend;
  private final ToolItem history;
  private final StyledText message;
  private Button commit;
  private final Button commitAndPush;
  private boolean signOff;
  private String author;
  private boolean busy;

  CommitPanel(Composite parent, Listener listener, BooleanSupplier hasIncludedChanges) {
    control = new Composite(parent, SWT.NONE);
    GridLayout layout = new GridLayout(1, false);
    layout.marginWidth = 6;
    layout.marginHeight = 4;
    control.setLayout(layout);

    Composite amendRow = new Composite(control, SWT.NONE);
    GridLayout amendLayout = new GridLayout(2, false);
    amendLayout.marginWidth = 0;
    amendLayout.marginHeight = 0;
    amendRow.setLayout(amendLayout);
    amend = new Button(amendRow, SWT.CHECK);
    amend.setText("Amend");
    amend.addListener(SWT.Selection, e -> listener.amendChanged(amend.getSelection()));
    ToolBar historyBar = new ToolBar(amendRow, SWT.FLAT);
    history = new ToolItem(historyBar, SWT.PUSH);
    history.setImage(GitbenchIcons.get(Icon.HISTORY));
    history.setToolTipText("Commit Message History");
    history.addListener(SWT.Selection, e -> {
      Rectangle bounds = history.getBounds();
      listener.showMessageHistory(historyBar.toDisplay(bounds.x, bounds.y + bounds.height));
    });

    message = new StyledText(control, SWT.BORDER | SWT.MULTI | SWT.WRAP | SWT.V_SCROLL);
    message.setFont(JFaceResources.getTextFont());
    message.setMargins(4, 4, 4, 4);
    message.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
    message.addListener(SWT.Modify, e -> updateButtons(hasIncludedChanges));
    message.addListener(SWT.KeyDown, e -> {
      if ((e.keyCode == SWT.CR || e.keyCode == SWT.KEYPAD_CR) && (e.stateMask & SWT.MOD1) != 0) {
        e.doit = false;
        if (commit.isEnabled()) {
          listener.commit(false);
        }
      }
    });

    Composite buttons = new Composite(control, SWT.NONE);
    buttons.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    GridLayout buttonsLayout = new GridLayout(4, false);
    buttonsLayout.marginWidth = 0;
    buttonsLayout.marginHeight = 0;
    buttons.setLayout(buttonsLayout);
    commit = new Button(buttons, SWT.PUSH);
    commit.setText("Commit");
    commit.addListener(SWT.Selection, e -> listener.commit(false));
    setButtonWidth(commit);
    commitAndPush = new Button(buttons, SWT.PUSH);
    commitAndPush.setText("Commit and Push...");
    commitAndPush.addListener(SWT.Selection, e -> listener.commit(true));
    setButtonWidth(commitAndPush);
    new org.eclipse.swt.widgets.Label(buttons, SWT.NONE).setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    ToolBar optionsBar = new ToolBar(buttons, SWT.FLAT);
    ToolItem options = new ToolItem(optionsBar, SWT.PUSH);
    options.setImage(GitbenchIcons.get(Icon.SETTINGS));
    options.setToolTipText("Commit Options");
    options.addListener(SWT.Selection, e -> showOptions(optionsBar, options));

    updateButtons(hasIncludedChanges);
  }

  Composite control() {
    return control;
  }

  String message() {
    return message.getText();
  }

  void setMessage(String text) {
    message.setText(text == null ? "" : text);
  }

  boolean isAmend() {
    return amend.getSelection();
  }

  void setAmend(boolean on) {
    amend.setSelection(on);
  }

  void setAmendEnabled(boolean enabled) {
    amend.setEnabled(enabled);
    if (!enabled) {
      amend.setSelection(false);
    }
  }

  CommitOptions options() {
    return new CommitOptions(isAmend(), signOff, author);
  }

  void focusMessage() {
    message.setFocus();
  }

  boolean isBusy() {
    return busy;
  }

  /** Locks the commit buttons while a commit runs. */
  void setBusy(boolean on) {
    busy = on;
    if (on) {
      commit.setEnabled(false);
      commitAndPush.setEnabled(false);
    }
  }

  /** Commit is possible with a message and at least one checked file. Amend needs only a message. */
  void updateButtons(BooleanSupplier hasIncludedChanges) {
    boolean hasMessage = !message.getText().isBlank();
    boolean enabled = !busy && hasMessage && (hasIncludedChanges.getAsBoolean() || isAmend());
    commit.setEnabled(enabled);
    commitAndPush.setEnabled(enabled);
    String tip = hasMessage ? null : "Specify commit message";
    commit.setToolTipText(tip);
    commitAndPush.setToolTipText(tip);
  }

  private void showOptions(ToolBar bar, ToolItem item) {
    Menu menu = new Menu(bar.getShell(), SWT.POP_UP);
    MenuItem signOffItem = new MenuItem(menu, SWT.CHECK);
    signOffItem.setText("Sign-off commit");
    signOffItem.setSelection(signOff);
    signOffItem.addListener(SWT.Selection, e -> signOff = signOffItem.getSelection());
    MenuItem authorItem = new MenuItem(menu, SWT.PUSH);
    authorItem.setText(author == null ? "Author..." : "Author: " + author);
    authorItem.addListener(SWT.Selection, e -> {
      InputDialog dialog = new InputDialog(bar.getShell(), "Commit Author", "Author (Name <email>), empty for the default:",
                                           author == null ? "" : author,
                                           text -> text.isBlank() || text.matches(".+<.+@.+>") ? null : "Use the form: Name <email>");
      if (dialog.open() == IDialogConstants.OK_ID) {
        author = dialog.getValue().isBlank() ? null : dialog.getValue().trim();
      }
    });
    Rectangle bounds = item.getBounds();
    menu.addListener(SWT.Hide, e -> menu.getDisplay().asyncExec(menu::dispose));
    menu.setLocation(bar.toDisplay(bounds.x, bounds.y + bounds.height));
    menu.setVisible(true);
  }

  /** Resets the panel after a successful commit. Sign-off stays, the author resets, as in IntelliJ. */
  void afterCommit() {
    setMessage("");
    setAmend(false);
    author = null;
  }

  private static void setButtonWidth(Button button) {
    GridData data = new GridData();
    data.widthHint = Math.max(button.computeSize(SWT.DEFAULT, SWT.DEFAULT).x + 16, 80);
    button.setLayoutData(data);
  }
}
