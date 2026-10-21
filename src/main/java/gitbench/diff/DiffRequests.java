package gitbench.diff;

import gitbench.git.ChangeKind;
import gitbench.git.FileChange;
import gitbench.git.GitRepository;

import java.util.List;

/** Builds the diff requests that the Commit and Git Log views open. */
public final class DiffRequests {
  private DiffRequests() {
  }

  /** A local change: HEAD on the left, the working file on the right, as the IntelliJ Commit tool window. */
  public static DiffRequest forLocalChange(GitRepository repository, FileChange change) {
    String leftPath = change.origPath() != null ? change.origPath() : change.path();
    DiffSide left = switch (change.kind()) {
      case ADDED -> new DiffSide.Empty("File added");
      case UNVERSIONED -> new DiffSide.Empty("Unversioned file");
      default -> new DiffSide.Revision("HEAD", leftPath);
    };
    DiffSide right = change.kind() == ChangeKind.DELETED
                     ? new DiffSide.Empty("File deleted")
                     : new DiffSide.WorkingFile(repository.root().resolve(change.path()));
    return new DiffRequest(repository, change.path(), left, right);
  }

  /** The index of the selected request in the chain, where the diff tab starts; 0 when the chain does not hold it. */
  public static int chain(List<DiffRequest> all, DiffRequest selected) {
    return Math.max(0, all.indexOf(selected));
  }

  /** A file of a commit: the first parent on the left, the commit on the right. A root commit has nothing on the left. */
  public static DiffRequest forCommitFile(GitRepository repository, String hash, List<String> parents, FileChange change) {
    String leftPath = change.origPath() != null ? change.origPath() : change.path();
    DiffSide left = parents.isEmpty() || change.kind() == ChangeKind.ADDED
                    ? new DiffSide.Empty("File added")
                    : new DiffSide.Revision(parents.get(0), leftPath);
    DiffSide right = change.kind() == ChangeKind.DELETED
                     ? new DiffSide.Empty("File deleted")
                     : new DiffSide.Revision(hash, change.path());
    return new DiffRequest(repository, change.path(), left, right);
  }
}
