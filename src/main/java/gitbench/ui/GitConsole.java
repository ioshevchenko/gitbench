package gitbench.ui;

import gitbench.git.GitCommandListener;
import gitbench.git.GitResult;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.console.ConsolePlugin;
import org.eclipse.ui.console.IConsole;
import org.eclipse.ui.console.MessageConsole;
import org.eclipse.ui.console.MessageConsoleStream;

import java.nio.file.Path;
import java.util.List;

/** The "Gitbench Git" console. It shows every git command and the error output of failed commands. */
public final class GitConsole implements GitCommandListener {
  private static final String NAME = "Gitbench Git";

  private MessageConsole console;

  @Override
  public void onCommand(Path workDir, List<String> args, GitResult result) {
    StringBuilder text = new StringBuilder("> git ").append(String.join(" ", args)).append('\n');
    if (!result.ok() && !result.stderr().isBlank()) {
      text.append(result.stderr().stripTrailing()).append('\n');
    }
    Display display = Display.getDefault();
    display.asyncExec(() -> print(text.toString()));
  }

  private void print(String text) {
    try (MessageConsoleStream stream = console().newMessageStream()) {
      stream.print(text);
    }
    catch (java.io.IOException ignored) {
      // The stream is closed after print. A close error loses nothing.
    }
  }

  private MessageConsole console() {
    if (console == null) {
      console = new MessageConsole(NAME, null);
      ConsolePlugin.getDefault().getConsoleManager().addConsoles(new IConsole[]{console});
    }
    return console;
  }
}
