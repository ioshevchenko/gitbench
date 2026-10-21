package gitbench.ui.merge;

import gitbench.git.GitException;
import gitbench.git.GitRepository;
import gitbench.merge.ConflictFile;
import gitbench.merge.ConflictService;
import gitbench.merge.MergeContext;
import gitbench.ui.Activator;
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
    }
    else if (!loaded.get().files().isEmpty()) {
      new ConflictsDialog(shell, repository, loaded.get().context(), loaded.get().files()).open();
    }
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
