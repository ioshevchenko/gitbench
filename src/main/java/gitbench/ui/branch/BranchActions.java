package gitbench.ui.branch;

import gitbench.branch.BranchOperations;
import gitbench.git.GitException;
import gitbench.git.GitRepository;
import gitbench.git.OutgoingCommit;
import gitbench.git.PushResult;
import gitbench.git.PushService;
import gitbench.git.PushTarget;
import gitbench.log.GitRef;
import gitbench.log.OperationResult;
import gitbench.log.RefType;
import gitbench.ui.Activator;
import gitbench.ui.GitbenchIcons.Icon;
import gitbench.ui.OperationRunner;
import gitbench.ui.RepositoryEvents;
import gitbench.ui.log.LogPanel;
import gitbench.ui.push.PushDialog;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Control;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.PartInitException;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The branch operations of the Branches view: the context menu in the order of IntelliJ, the toolbar buttons
 * New Branch, Update Selected, Delete, Compare with Current, and checkout on double click or Enter.
 */
final class BranchActions {
  private final BranchesPanel view;

  BranchActions(BranchesPanel view) {
    this.view = view;
  }

  void install() {
    view.insertItem(0, SWT.PUSH, Icon.ADD, "New Branch...", () -> single().ifPresent(this::newBranchFrom));
    view.insertItem(1, SWT.PUSH, Icon.UPDATE, "Update Selected", () -> single().ifPresent(this::update));
    view.insertItem(2, SWT.PUSH, Icon.DELETE, "Delete", () -> delete(view.selectedRefs()));
    view.insertItem(3, SWT.PUSH, Icon.DIFF, "Compare with Current", () -> single().ifPresent(this::compareWithCurrent));

    Control tree = view.tree().viewer().getControl();
    MenuManager manager = new MenuManager();
    manager.setRemoveAllWhenShown(true);
    manager.addMenuListener(this::fill);
    tree.setMenu(manager.createContextMenu(tree));
    view.tree().viewer().addDoubleClickListener(e -> single().ifPresent(this::checkout));
    tree.addListener(SWT.KeyDown, e -> {
      if (e.keyCode == SWT.CR && e.stateMask == 0) {
        single().ifPresent(this::checkout);
      }
      else if (e.keyCode == SWT.DEL && e.stateMask == 0) {
        delete(view.selectedRefs());
      }
    });
  }

  private Optional<GitRef> single() {
    List<GitRef> refs = view.selectedRefs().stream().filter(r -> r.type() != RefType.HEAD).toList();
    return refs.size() == 1 ? Optional.of(refs.get(0)) : Optional.empty();
  }

  private void fill(IMenuManager menu) {
    Optional<GitRef> selected = single();
    if (selected.isEmpty()) {
      return;
    }
    GitRef ref = selected.get();
    String current = view.currentBranch().map(GitRef::name).orElse(null);
    boolean isCurrent = ref.current();
    boolean local = ref.type() == RefType.LOCAL_BRANCH;
    if (!isCurrent) {
      menu.add(action("Checkout", () -> checkout(ref)));
    }
    menu.add(action("New Branch from '" + ref.name() + "'...", () -> newBranchFrom(ref)));
    if (!isCurrent && current != null) {
      menu.add(action("Checkout and Rebase onto '" + current + "'", () -> checkoutAndRebase(ref, current)));
    }
    if (!isCurrent && current != null) {
      menu.add(new Separator());
      menu.add(action("Compare with '" + current + "'", () -> compareWithCurrent(ref)));
      menu.add(new Separator());
      menu.add(action("Rebase '" + current + "' onto '" + ref.name() + "'", () -> rebaseOnto(ref)));
      menu.add(action("Merge '" + ref.name() + "' into '" + current + "'", () -> merge(ref)));
    }
    menu.add(new Separator());
    if (local) {
      menu.add(action("Update", () -> update(ref)));
      menu.add(action("Push...", () -> push(ref)));
    }
    menu.add(new Separator());
    if (local) {
      menu.add(action("Rename...", () -> rename(ref)));
    }
    if (!isCurrent) {
      menu.add(action("Delete", () -> delete(List.of(ref))));
    }
  }

  private BranchOperations operations() {
    return new BranchOperations(Activator.getDefault().gitRunner());
  }

  private void run(String title, OperationRunner.Operation operation) {
    view.currentRepository().ifPresent(repo -> OperationRunner.run(view.getSite(), repo, title, operation));
  }

  void checkout(GitRef ref) {
    if (ref.current() || ref.type() == RefType.HEAD) {
      return;
    }
    view.currentRepository().ifPresent(repo -> run("Checkout", () -> {
      OperationResult result = operations().checkout(repo, ref);
      if (!result.needsForce()) {
        return result;
      }
      // The local branch has its own commits: ask as IntelliJ does.
      int[] choice = {2};
      view.getSite().getShell().getDisplay().syncExec(() -> {
        if (!view.getSite().getShell().isDisposed()) {
          choice[0] = new MessageDialog(view.getSite().getShell(), "Checkout", null,
                                        result.message() + "\nRebase them onto " + ref.name() + ", or drop them?",
                                        MessageDialog.QUESTION, 0, "Checkout and Rebase", "Drop Local Commits", "Cancel").open();
        }
      });
      return switch (choice[0]) {
        case 0 -> operations().checkoutRemote(repo, ref, BranchOperations.LocalCommits.REBASE);
        case 1 -> operations().checkoutRemote(repo, ref, BranchOperations.LocalCommits.DROP);
        default -> OperationResult.success("");
      };
    }));
  }

  private void checkoutAndRebase(GitRef ref, String current) {
    view.currentRepository().ifPresent(repo -> run("Checkout and Rebase", () -> {
      OperationResult checkout = operations().checkout(repo, ref);
      return checkout.ok() ? operations().rebaseCurrentOnto(repo, "refs/heads/" + current) : checkout;
    }));
  }

  private void newBranchFrom(GitRef ref) {
    view.currentRepository().ifPresent(repo -> {
      Set<String> existing = view.refs().stream().filter(r -> r.type() == RefType.LOCAL_BRANCH).map(GitRef::name)
        .collect(Collectors.toSet());
      String suggestion = ref.type() == RefType.REMOTE_BRANCH ? ref.name().substring(ref.name().indexOf('/') + 1) : null;
      NewBranchDialog dialog = new NewBranchDialog(view.getSite().getShell(), Activator.getDefault().gitRunner(), repo,
                                                   ref.name(), existing, suggestion);
      if (dialog.open() == IDialogConstants.OK_ID) {
        run("New Branch", () -> operations().newBranch(repo, dialog.name(), ref.name(), dialog.checkout(), dialog.overwrite()));
      }
    });
  }

  private void compareWithCurrent(GitRef ref) {
    Optional<GitRef> current = view.currentBranch();
    if (current.isEmpty() || ref.current()) {
      return;
    }
    try {
      view.logs().show().showRange(current.get().fullName(), ref.fullName());
    }
    catch (PartInitException e) {
      MessageDialog.openError(view.getSite().getShell(), "Compare", e.getMessage());
    }
  }

  private void rebaseOnto(GitRef ref) {
    view.currentRepository().ifPresent(repo -> run("Rebase", () -> operations().rebaseCurrentOnto(repo, ref.fullName())));
  }

  private void merge(GitRef ref) {
    view.currentRepository().ifPresent(repo -> run("Merge", () -> operations().mergeIntoCurrent(repo, ref.fullName())));
  }

  private void update(GitRef ref) {
    if (ref.type() != RefType.LOCAL_BRANCH) {
      return;
    }
    view.currentRepository().ifPresent(repo -> run("Update", () -> operations().updateSelected(repo, ref)));
  }

  private void rename(GitRef ref) {
    InputDialog dialog = new InputDialog(view.getSite().getShell(), "Rename Branch", "Rename branch '" + ref.name() + "' to:",
                                         ref.name(), value -> value.isBlank() ? "" : null);
    if (dialog.open() == IDialogConstants.OK_ID && !dialog.getValue().trim().equals(ref.name())) {
      String newName = dialog.getValue().trim();
      view.currentRepository().ifPresent(repo -> run("Rename", () -> operations().rename(repo, ref.name(), newName)));
    }
  }

  /** Deletes branches. A local branch that is not fully merged asks once more, then deletes with force. */
  private void delete(List<GitRef> refs) {
    List<GitRef> deletable = refs.stream()
      .filter(r -> (r.type() == RefType.LOCAL_BRANCH && !r.current()) || r.type() == RefType.REMOTE_BRANCH).toList();
    if (deletable.isEmpty()) {
      return;
    }
    boolean remote = deletable.stream().anyMatch(r -> r.type() == RefType.REMOTE_BRANCH);
    String names = deletable.stream().map(GitRef::name).collect(Collectors.joining(", "));
    if (remote && !MessageDialog.openConfirm(view.getSite().getShell(), "Delete Remote Branch",
                                             "Delete " + names + " on the remote?")) {
      return;
    }
    // One job deletes the branches one after another and stops at the first failure.
    view.currentRepository().ifPresent(repo -> OperationRunner.run(view.getSite(), repo, "Delete", () -> {
      OperationResult last = OperationResult.success("");
      for (GitRef ref : deletable) {
        last = operations().delete(repo, ref, false);
        if (last.needsForce()) {
          String question = last.message() + " Delete anyway?";
          boolean[] force = {false};
          view.getSite().getShell().getDisplay().syncExec(() -> {
            if (!view.getSite().getShell().isDisposed()) {
              force[0] = MessageDialog.openQuestion(view.getSite().getShell(), "Delete Branch", question);
            }
          });
          last = force[0] ? operations().delete(repo, ref, true) : OperationResult.success("");
        }
        if (!last.ok()) {
          return last;
        }
      }
      return last;
    }));
  }

  /** Opens the Push dialog for a local branch and pushes it, as Push... in the IntelliJ branch menu. */
  private void push(GitRef ref) {
    view.currentRepository().ifPresent(repo -> {
      PushService pushService = new PushService(Activator.getDefault().gitRunner());
      Job load = Job.create("Preparing push", monitor -> {
        try {
          PushTarget target = pushService.defaultTarget(repo, ref.name());
          List<OutgoingCommit> outgoing = pushService.outgoing(repo, target, ref.name());
          view.asyncExec(() -> showPushDialog(repo, pushService, ref, target, outgoing));
        }
        catch (GitException e) {
          view.asyncExec(() -> MessageDialog.openError(view.getSite().getShell(), "Push", e.userMessage()));
        }
        return Status.OK_STATUS;
      });
      load.schedule();
    });
  }

  private void showPushDialog(GitRepository repo, PushService pushService, GitRef ref, PushTarget target,
                              List<OutgoingCommit> outgoing) {
    PushDialog dialog = new PushDialog(view.getSite().getShell(), repo, pushService, target, outgoing);
    if (dialog.open() != IDialogConstants.OK_ID) {
      return;
    }
    PushTarget chosen = dialog.chosenTarget();
    Job job = Job.create("Pushing to " + chosen.remote(), monitor -> {
      try {
        PushResult result = pushService.push(repo, chosen, ref.name());
        RepositoryEvents.fire(repo);
        String text = PushDialog.describe(result, outgoing.size(), chosen);
        view.asyncExec(() -> {
          switch (result.status()) {
            case OK, UP_TO_DATE -> view.getViewSite().getActionBars().getStatusLineManager().setMessage(text);
            case REJECTED -> MessageDialog.openWarning(view.getSite().getShell(), "Push Rejected", text);
            case ERROR -> MessageDialog.openError(view.getSite().getShell(), "Push Failed", text);
          }
        });
      }
      catch (GitException e) {
        view.asyncExec(() -> MessageDialog.openError(view.getSite().getShell(), "Push Failed", e.userMessage()));
      }
      return Status.OK_STATUS;
    });
    job.schedule();
  }

  private static Action action(String text, Runnable run) {
    return new Action(text) {
      @Override
      public void run() {
        run.run();
      }
    };
  }
}
