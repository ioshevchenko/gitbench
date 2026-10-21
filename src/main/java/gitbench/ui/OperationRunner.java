package gitbench.ui;

import gitbench.git.GitException;
import gitbench.git.GitRepository;
import gitbench.log.OperationResult;
import gitbench.ui.commit.CommitView;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbenchPartSite;
import org.eclipse.ui.PartInitException;

import java.util.function.Consumer;

/**
 * Runs a git operation of the Log or the Branches view in a job, then syncs the workspace, tells all views, and
 * shows the outcome: a status line text, an error, or the conflicts window.
 */
public final class OperationRunner {
  /** A git operation that reports its outcome. */
  @FunctionalInterface
  public interface Operation {
    OperationResult run() throws GitException;
  }

  private OperationRunner() {
  }

  /**
   * One operation per repository at a time: two git commands that change refs or the index at once fail on
   * index.lock or packed-refs.lock.
   */
  private record RepositoryRule(GitRepository repository) implements org.eclipse.core.runtime.jobs.ISchedulingRule {
    @Override
    public boolean contains(org.eclipse.core.runtime.jobs.ISchedulingRule rule) {
      return equals(rule);
    }

    @Override
    public boolean isConflicting(org.eclipse.core.runtime.jobs.ISchedulingRule rule) {
      return equals(rule);
    }
  }

  /** The rule that keeps git operations of one repository apart, for jobs outside this class. */
  public static org.eclipse.core.runtime.jobs.ISchedulingRule ruleFor(GitRepository repository) {
    return new RepositoryRule(repository);
  }

  public static void run(IWorkbenchPartSite site, GitRepository repository, String title, Operation operation) {
    run(site, repository, title, operation, result -> { });
  }

  /** Like {@link #run(IWorkbenchPartSite, GitRepository, String, Operation)}; {@code onSuccess} runs on the UI thread. */
  public static void run(IWorkbenchPartSite site, GitRepository repository, String title, Operation operation,
                         Consumer<OperationResult> onSuccess) {
    Display display = site.getShell().getDisplay();
    Job job = Job.create(title, monitor -> {
      OperationResult result;
      try {
        result = operation.run();
      }
      catch (GitException e) {
        result = OperationResult.failure(e.userMessage());
      }
      OperationResult finalResult = result;
      // refreshLocal begins a resource rule, and the repository rule does not contain it: refresh in a job without a rule.
      Job refresh = Job.create(title + ": refresh", refreshMonitor -> {
        WorkspaceSync.refresh(repository);
        RepositoryEvents.fire(repository);
        if (!display.isDisposed()) {
          display.asyncExec(() -> show(site, repository, title, finalResult, onSuccess));
        }
        return Status.OK_STATUS;
      });
      refresh.setSystem(true);
      refresh.schedule();
      return Status.OK_STATUS;
    });
    job.setRule(new RepositoryRule(repository));
    job.schedule();
  }

  private static void show(IWorkbenchPartSite site, GitRepository repository, String title, OperationResult result,
                           Consumer<OperationResult> onSuccess) {
    Shell shell = site.getShell();
    if (shell.isDisposed()) {
      return;
    }
    if (result.ok()) {
      if (site instanceof org.eclipse.ui.IViewSite viewSite) {
        viewSite.getActionBars().getStatusLineManager().setMessage(title + ": done");
      }
      onSuccess.accept(result);
      return;
    }
    if (result.conflicts()) {
      // As IntelliJ: an operation that stops on conflicts opens the conflicts window at once.
      gitbench.ui.merge.MergeOpener.showConflicts(shell, repository);
      return;
    }
    MessageDialog.openError(shell, title + " Failed", result.message());
  }

  /** Opens the Commit view and returns it, or null. */
  public static CommitView showCommitView(IWorkbenchPartSite site) {
    try {
      IViewPart view = site.getPage().showView(CommitView.ID);
      return view instanceof CommitView commitView ? commitView : null;
    }
    catch (PartInitException e) {
      MessageDialog.openError(site.getShell(), "Commit", e.getMessage());
      return null;
    }
  }
}
