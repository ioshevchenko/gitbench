package gitbench.ui.log;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.swt.widgets.Table;

import java.util.List;

/** The context menu of the commit table, in the order of the IntelliJ "Git.Log.ContextMenu". */
final class LogContextMenu {
  private final GitLogView view;

  LogContextMenu(GitLogView view) {
    this.view = view;
  }

  void install(Table table) {
    MenuManager manager = new MenuManager();
    manager.setRemoveAllWhenShown(true);
    manager.addMenuListener(menu -> fill(menu, view.selectedHashes()));
    table.setMenu(manager.createContextMenu(table));
  }

  private void fill(IMenuManager menu, List<String> hashes) {
    menu.add(action("Copy Revision Number\tCtrl+C", !hashes.isEmpty(), view::copyHashes));
  }

  static Action action(String text, boolean enabled, Runnable run) {
    Action action = new Action(text) {
      @Override
      public void run() {
        run.run();
      }
    };
    action.setEnabled(enabled);
    return action;
  }
}
