package gitbench.ui.merge;

import gitbench.git.GitException;
import gitbench.git.GitRepository;
import gitbench.merge.ConflictFile;
import gitbench.merge.ConflictService;
import gitbench.merge.MergeContext;
import gitbench.ui.Activator;
import gitbench.ui.RepositoryEvents;
import gitbench.ui.WorkspaceSync;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.custom.BusyIndicator;
import org.eclipse.swt.widgets.Shell;

import java.util.List;
import java.util.Optional;

/** Opens the conflicts window and the merge window. */
public final class MergeOpener {
  private MergeOpener() {
  }

  /** Opens the conflicts window of the repository. Without conflicts it says so. */
  public static void showConflicts(Shell shell, GitRepository repository) {
    Optional<Loaded> loaded = load(shell, repository);
    if (loaded.isEmpty()) {
      return;
    }
    if (loaded.get().files().isEmpty()) {
      MessageDialog.openInformation(shell, "Conflicts", "There are no conflicts to resolve.");
      return;
    }
    new ConflictsDialog(shell, repository, loaded.get().context(), loaded.get().files()).open();
    offerContinue(shell, repository);
  }

  /** Opens the merge window of one conflicted file, or the conflicts window when the file cannot be merged as text. */
  public static void merge(Shell shell, GitRepository repository, String path) {
    Optional<Loaded> loaded = load(shell, repository);
    if (loaded.isEmpty()) {
      return;
    }
    Optional<ConflictFile> file = loaded.get().files().stream().filter(f -> f.path().equals(path)).findFirst();
    if (file.isPresent() && file.get().canMerge()) {
      MergeDialog.open(shell, repository, file.get(), loaded.get().context());
      offerContinue(shell, repository);
    }
    else if (file.isPresent()) {
      new ConflictsDialog(shell, repository, loaded.get().context(), loaded.get().files()).open();
      offerContinue(shell, repository);
    }
    else {
      MessageDialog.openInformation(shell, "Conflicts", path + " has no conflicts.");
    }
  }

  /**
   * After the last conflict, offers to finish the operation, as the IntelliJ notifications do: continue the rebase,
   * the cherry-pick or the revert, or commit the merge. A rebase that stops on the next commit opens the conflicts
   * window again.
   */
  static void offerContinue(Shell shell, GitRepository repository) {
    Optional<Loaded> loaded = load(shell, repository);
    if (loaded.isEmpty() || !loaded.get().files().isEmpty()) {
      return;
    }
    MergeContext context = loaded.get().context();
    String action = switch (context.operation()) {
      case REBASE -> "Continue Rebase";
      case CHERRY_PICK -> "Continue Cherry-Pick";
      case REVERT -> "Continue Revert";
      case MERGE -> "Commit Merge";
      case NONE -> null;
    };
    if (action == null) {
      return;
    }
    MessageDialog question = new MessageDialog(shell, "All Conflicts Resolved", null,
                                               "All conflicts are resolved. " + action + " now?", MessageDialog.QUESTION,
                                               0, action, "Later");
    if (question.open() != 0) {
      return;
    }
    // A rebase can go on through many commits and hooks: it runs in a job, as the other git operations.
    ConflictService service = new ConflictService(Activator.getDefault().gitRunner());
    org.eclipse.swt.widgets.Display display = shell.getDisplay();
    Job job = Job.create(action, monitor -> {
      GitException error = null;
      try {
        service.continueOperation(repository, context);
      }
      catch (GitException e) {
        error = e;
      }
      WorkspaceSync.refresh(repository);
      RepositoryEvents.fire(repository);
      GitException failure = error;
      display.asyncExec(() -> {
        if (shell.isDisposed()) {
          return;
        }
        Optional<Loaded> after = load(shell, repository);
        if (after.isPresent() && !after.get().files().isEmpty()) {
          // The next commit of the rebase stops on conflicts too.
          showConflicts(shell, repository);
        }
        else if (failure != null) {
          MessageDialog.openError(shell, action, failure.userMessage());
        }
      });
      return Status.OK_STATUS;
    });
    job.setRule(gitbench.ui.OperationRunner.ruleFor(repository));
    job.setUser(true);
    job.schedule();
  }

  private record Loaded(List<ConflictFile> files, MergeContext context) {
  }

  private static Optional<Loaded> load(Shell shell, GitRepository repository) {
    ConflictService service = new ConflictService(Activator.getDefault().gitRunner());
    Loaded[] result = new Loaded[1];
    GitException[] error = new GitException[1];
    BusyIndicator.showWhile(shell.getDisplay(), () -> {
      try {
        result[0] = new Loaded(service.list(repository), service.context(repository));
      }
      catch (GitException e) {
        error[0] = e;
      }
    });
    if (error[0] != null) {
      MessageDialog.openError(shell, "Conflicts", error[0].userMessage());
      return Optional.empty();
    }
    return Optional.of(result[0]);
  }
}
