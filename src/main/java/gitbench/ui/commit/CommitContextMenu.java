package gitbench.ui.commit;

import gitbench.git.ChangeKind;
import gitbench.git.CommitService;
import gitbench.git.FileChange;
import gitbench.git.GitRepository;
import gitbench.git.IgnoreService;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.widgets.Control;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/** The context menu of the Commit view tree, in the order of the IntelliJ "ChangesViewPopupMenu". */
final class CommitContextMenu {
  private final CommitView view;
  private final CommitService commitService;
  private final IgnoreService ignoreService = new IgnoreService();

  CommitContextMenu(CommitView view, CommitService commitService) {
    this.view = view;
    this.commitService = commitService;
  }

  void install() {
    Control tree = view.tree().viewer().getControl();
    MenuManager manager = new MenuManager();
    manager.setRemoveAllWhenShown(true);
    manager.addMenuListener(menu -> fill(menu, view.tree().selectedChanges()));
    tree.setMenu(manager.createContextMenu(tree));
    tree.addListener(SWT.KeyDown, e -> {
      List<FileChange> selected = view.tree().selectedChanges();
      if (e.keyCode == SWT.F4 && e.stateMask == 0) {
        jumpToSource(selected);
      }
      else if (e.keyCode == 'd' && (e.stateMask & SWT.MODIFIER_MASK) == SWT.MOD1) {
        view.showDiff(selected);
      }
      else if (e.keyCode == 'a' && (e.stateMask & SWT.MOD1) != 0 && (e.stateMask & SWT.ALT) != 0) {
        addToVcs(unversioned(selected));
      }
      else if (e.keyCode == SWT.DEL && e.stateMask == 0) {
        delete(selected);
      }
    });
  }

  private void fill(org.eclipse.jface.action.IMenuManager menu, List<FileChange> selected) {
    List<FileChange> versioned = selected.stream().filter(c -> c.kind() != ChangeKind.UNVERSIONED).toList();
    List<FileChange> unversioned = unversioned(selected);
    menu.add(action("Commit File...", !selected.isEmpty(), () -> commitFiles(selected)));
    menu.add(action("Rollback...", !versioned.isEmpty(), () -> view.rollback(versioned)));
    menu.add(action("Show Diff\tCtrl+D", !selected.isEmpty(), () -> view.showDiff(selected)));
    menu.add(action("Jump to Source\tF4", !selected.isEmpty(), () -> jumpToSource(selected)));
    menu.add(action("Copy Path", !selected.isEmpty(), () -> copyPaths(selected)));
    menu.add(new Separator());
    menu.add(action("Delete", !selected.isEmpty(), () -> delete(selected)));
    menu.add(action("Add to VCS\tCtrl+Alt+A", !unversioned.isEmpty(), () -> addToVcs(unversioned)));
    menu.add(action("Add to .gitignore", !unversioned.isEmpty(), () -> addToGitignore(unversioned)));
  }

  /** Checks only the selected files and moves the focus to the commit message. */
  private void commitFiles(List<FileChange> selected) {
    view.inclusion().setIncluded(view.inclusion().included(), false);
    view.inclusion().setIncluded(selected, true);
    view.tree().viewer().refresh();
    view.panel().updateButtons(() -> !view.inclusion().included().isEmpty());
    view.panel().focusMessage();
  }

  private void jumpToSource(List<FileChange> selected) {
    OpenFiles.open(view.getSite().getPage(), view.currentRepository(), selected);
  }

  private void copyPaths(List<FileChange> selected) {
    Optional<GitRepository> repository = view.currentRepository();
    if (repository.isEmpty()) {
      return;
    }
    String text = selected.stream()
      .map(c -> repository.get().root().resolve(c.path()).toString())
      .collect(Collectors.joining(System.lineSeparator()));
    Clipboard clipboard = new Clipboard(view.getSite().getShell().getDisplay());
    try {
      clipboard.setContents(new Object[]{text}, new Transfer[]{TextTransfer.getInstance()});
    }
    finally {
      clipboard.dispose();
    }
  }

  private void delete(List<FileChange> selected) {
    List<FileChange> existing = selected.stream().filter(c -> c.kind() != ChangeKind.DELETED).toList();
    Optional<GitRepository> repository = view.currentRepository();
    if (existing.isEmpty() || repository.isEmpty()) {
      return;
    }
    String question = existing.size() == 1
                      ? "Delete \"" + existing.get(0).fileName() + "\"?"
                      : "Delete " + existing.size() + " files?";
    if (!MessageDialog.openConfirm(view.getSite().getShell(), "Delete", question)) {
      return;
    }
    for (FileChange change : existing) {
      Path path = repository.get().root().resolve(change.path());
      try {
        IFile[] files = ResourcesPlugin.getWorkspace().getRoot().findFilesForLocationURI(path.toUri());
        if (files.length > 0) {
          files[0].delete(true, null);
        }
        else {
          Files.deleteIfExists(path);
        }
      }
      catch (CoreException | IOException e) {
        MessageDialog.openError(view.getSite().getShell(), "Delete", "Cannot delete " + change.path() + ": " + e.getMessage());
        break;
      }
    }
    view.refresh();
  }

  private void addToVcs(List<FileChange> unversioned) {
    if (!unversioned.isEmpty()) {
      List<String> paths = unversioned.stream().map(FileChange::path).toList();
      view.runOperation("Add to VCS", repo -> commitService.addToVcs(repo, paths));
    }
  }

  private void addToGitignore(List<FileChange> unversioned) {
    List<String> paths = unversioned.stream().map(FileChange::path).toList();
    view.runOperation("Add to .gitignore", repo -> ignoreService.addToGitignore(repo, paths));
  }

  private static List<FileChange> unversioned(List<FileChange> changes) {
    return changes.stream().filter(c -> c.kind() == ChangeKind.UNVERSIONED).toList();
  }

  private static Action action(String text, boolean enabled, Runnable run) {
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
