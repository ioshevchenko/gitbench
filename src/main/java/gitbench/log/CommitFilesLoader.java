package gitbench.log;

import gitbench.git.ChangeKind;
import gitbench.git.FileChange;
import gitbench.git.GitException;
import gitbench.git.GitRepository;
import gitbench.git.GitRunner;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Lists the files that a commit changes, against its first parent, as the IntelliJ log shows them by default.
 * A root commit lists all its files as added.
 */
public final class CommitFilesLoader {
  private final GitRunner runner;

  public CommitFilesLoader(GitRunner runner) {
    this.runner = runner;
  }

  public List<FileChange> load(GitRepository repo, String hash) throws GitException {
    // diff-tree has no --first-parent; compare with the first parent by name, or use --root for a root commit.
    String[] commitAndParents = runner.runOk(repo.root(), List.of("rev-list", "--parents", "-n", "1", hash), null).trim().split(" ");
    List<String> args = new ArrayList<>(List.of("diff-tree", "-r", "-z", "--name-status", "-M", "--no-commit-id"));
    if (commitAndParents.length > 1) {
      args.add(commitAndParents[1]);
    }
    else {
      args.add("--root");
    }
    args.add(hash);
    byte[] out = runner.run(repo.root(), args, null).stdout();
    String[] f = new String(out, StandardCharsets.UTF_8).split("\0");
    List<FileChange> files = new ArrayList<>();
    for (int i = 0; i < f.length; i++) {
      String status = f[i].trim();
      if (status.isEmpty() || i + 1 >= f.length) {
        continue;
      }
      switch (status.charAt(0)) {
        case 'R', 'C' -> {
          String from = f[++i];
          files.add(new FileChange(f[++i], from, ChangeKind.RENAMED));
        }
        case 'A' -> files.add(new FileChange(f[++i], null, ChangeKind.ADDED));
        case 'D' -> files.add(new FileChange(f[++i], null, ChangeKind.DELETED));
        default -> files.add(new FileChange(f[++i], null, ChangeKind.MODIFIED));
      }
    }
    return files;
  }
}
