package gitbench.ui.log;

import gitbench.git.GitException;
import gitbench.git.GitRepository;
import gitbench.git.GitResult;
import gitbench.git.GitRunner;
import gitbench.log.CommitOperations;
import gitbench.log.GitRef;
import gitbench.log.LogModel;
import gitbench.log.OperationResult;
import gitbench.log.RefType;
import gitbench.ui.Activator;
import gitbench.ui.OperationRunner;
import gitbench.ui.branch.NewBranchDialog;
import gitbench.ui.commit.CommitView;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Table;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** The context menu of the commit table, in the order of the IntelliJ "Git.Log.ContextMenu". */
final class LogContextMenu {
  private final LogPanel view;

  LogContextMenu(LogPanel view) {
    this.view = view;
  }

  void install(Table table) {
    MenuManager manager = new MenuManager();
    manager.setRemoveAllWhenShown(true);
    manager.addMenuListener(menu -> fill(menu, view.selectedHashes()));
    table.setMenu(manager.createContextMenu(table));
    table.addListener(SWT.KeyDown, e -> {
      List<String> selected = view.selectedHashes();
      if (selected.size() != 1 || e.stateMask != 0) {
        return;
      }
      if (e.keyCode == SWT.ARROW_LEFT) {
        goToParent(selected.get(0));
        e.doit = false;
      }
      else if (e.keyCode == SWT.ARROW_RIGHT) {
        goToChild(selected.get(0));
        e.doit = false;
      }
    });
  }

  private void fill(IMenuManager menu, List<String> hashes) {
    boolean one = hashes.size() == 1;
    String hash = one ? hashes.get(0) : null;
    menu.add(action("Copy Revision Number\tCtrl+C", !hashes.isEmpty(), view::copyHashes));
    menu.add(action("Cherry-Pick", !hashes.isEmpty(), () -> cherryPick(hashes)));
    menu.add(new Separator());
    menu.add(action("Checkout Revision '" + shortHash(hash) + "'", one, () -> checkoutRevision(hash)));
    menu.add(new Separator());
    menu.add(action("Reset Current Branch to Here...", one, () -> reset(hash)));
    menu.add(action("Revert Commit", one, () -> revert(hash)));
    menu.add(action("Undo Commit...", one && isHead(hash), this::undoCommit));
    menu.add(new Separator());
    menu.add(action("New Branch...", one, () -> newBranch(hash)));
    menu.add(action("New Tag...", one, () -> newTag(hash)));
    menu.add(new Separator());
    menu.add(action("Go to Parent Commit\t←", one, () -> goToParent(hash)));
    menu.add(action("Go to Child Commit\t→", one, () -> goToChild(hash)));
    menu.add(action("Go to Hash/Branch/Tag...", true, this::goToRef));
  }

  private CommitOperations operations() {
    return new CommitOperations(Activator.getDefault().gitRunner());
  }

  private void withRepository(java.util.function.Consumer<GitRepository> action) {
    view.currentRepository().ifPresent(action);
  }

  /** Cherry-picks the selection oldest first, as IntelliJ does, and stops at the first failure. */
  private void cherryPick(List<String> hashes) {
    LogModel model = view.shownModel();
    List<String> ordered = new ArrayList<>(hashes);
    ordered.sort((a, b) -> Integer.compare(model.rowOf(b), model.rowOf(a)));
    withRepository(repo -> OperationRunner.run(view.getSite(), repo, "Cherry-Pick", () -> {
      OperationResult last = OperationResult.success("");
      for (String hash : ordered) {
        last = operations().cherryPick(repo, hash);
        if (!last.ok()) {
          return last;
        }
      }
      return last;
    }));
  }

  private void checkoutRevision(String hash) {
    withRepository(repo -> OperationRunner.run(view.getSite(), repo, "Checkout", () -> operations().checkoutRevision(repo, hash)));
  }

  private void reset(String hash) {
    ResetDialog dialog = new ResetDialog(view.getSite().getShell(), shortHash(hash));
    if (dialog.open() != IDialogConstants.OK_ID) {
      return;
    }
    withRepository(repo -> OperationRunner.run(view.getSite(), repo, "Git Reset", () -> operations().reset(repo, hash, dialog.mode())));
  }

  private void revert(String hash) {
    withRepository(repo -> OperationRunner.run(view.getSite(), repo, "Revert", () -> operations().revert(repo, hash)));
  }

  /** Undo Commit works only for an unpushed HEAD, as in IntelliJ. The message goes back to the Commit view. */
  private void undoCommit() {
    withRepository(repo -> {
      if (isPushed(repo)) {
        MessageDialog.openError(view.getSite().getShell(), "Undo Commit",
                                "The commit is already pushed. Use Revert Commit instead.");
        return;
      }
      OperationRunner.run(view.getSite(), repo, "Undo Commit", () -> operations().undoLastCommit(repo), result -> {
        CommitView commitView = OperationRunner.showCommitView(view.getSite());
        if (commitView != null) {
          commitView.offerMessage(result.message());
        }
      });
    });
  }

  private void newBranch(String hash) {
    withRepository(repo -> {
      Set<String> existing = view.refs().stream().filter(r -> r.type() == RefType.LOCAL_BRANCH).map(GitRef::name)
        .collect(Collectors.toSet());
      GitRunner runner = Activator.getDefault().gitRunner();
      NewBranchDialog dialog = new NewBranchDialog(view.getSite().getShell(), runner, repo, shortHash(hash), existing, null);
      if (dialog.open() == IDialogConstants.OK_ID) {
        OperationRunner.run(view.getSite(), repo, "New Branch",
                            () -> operations().createBranch(repo, dialog.name(), hash, dialog.checkout(), dialog.overwrite()));
      }
    });
  }

  private void newTag(String hash) {
    NewTagDialog dialog = new NewTagDialog(view.getSite().getShell(), shortHash(hash));
    if (dialog.open() == IDialogConstants.OK_ID) {
      withRepository(repo -> OperationRunner.run(view.getSite(), repo, "New Tag",
                                                 () -> operations().createTag(repo, dialog.name(), hash, dialog.message())));
    }
  }

  private void goToParent(String hash) {
    LogModel model = view.shownModel();
    if (model != null) {
      model.parentsOf(hash).stream().findFirst().ifPresent(view::selectCommit);
    }
  }

  private void goToChild(String hash) {
    LogModel model = view.shownModel();
    if (model != null) {
      model.childrenOf(hash).stream().findFirst().ifPresent(view::selectCommit);
    }
  }

  private void goToRef() {
    GoToRefDialog dialog = new GoToRefDialog(view.getSite().getShell(), view.refs());
    if (dialog.open() != IDialogConstants.OK_ID || dialog.value().isEmpty()) {
      return;
    }
    String value = dialog.value();
    Optional<String> byRef = view.refs().stream().filter(r -> r.name().equals(value)).map(GitRef::hash).findFirst();
    Optional<String> hash = byRef.or(() -> resolve(value));
    if (hash.isPresent()) {
      view.selectCommit(hash.get());
    }
    else {
      MessageDialog.openInformation(view.getSite().getShell(), "Go to Hash/Branch/Tag", "Nothing found for '" + value + "'");
    }
  }

  private Optional<String> resolve(String revision) {
    Optional<GitRepository> repo = view.currentRepository();
    if (repo.isEmpty()) {
      return Optional.empty();
    }
    try {
      GitResult result = Activator.getDefault().gitRunner()
        .run(repo.get().root(), List.of("rev-parse", "--verify", "-q", revision + "^{commit}"), null);
      return result.ok() ? Optional.of(result.stdoutText().trim()) : Optional.empty();
    }
    catch (GitException e) {
      return Optional.empty();
    }
  }

  private boolean isHead(String hash) {
    return view.refs().stream().anyMatch(r -> r.type() == RefType.HEAD && r.hash().equals(hash));
  }

  private static boolean isPushed(GitRepository repo) {
    try {
      GitResult result = Activator.getDefault().gitRunner().run(repo.root(), List.of("branch", "-r", "--contains", "HEAD"), null);
      return result.ok() && !result.stdoutText().isBlank();
    }
    catch (GitException e) {
      return false;
    }
  }

  private static String shortHash(String hash) {
    return hash == null ? "" : hash.substring(0, Math.min(8, hash.length()));
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
