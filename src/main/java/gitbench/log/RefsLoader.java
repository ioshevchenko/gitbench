package gitbench.log;

import gitbench.git.GitException;
import gitbench.git.GitRepository;
import gitbench.git.GitResult;
import gitbench.git.GitRunner;

import java.util.ArrayList;
import java.util.List;

/** Reads the branches, remote branches, tags, and HEAD of a repository. */
public final class RefsLoader {
  private final GitRunner runner;

  public RefsLoader(GitRunner runner) {
    this.runner = runner;
  }

  public List<GitRef> load(GitRepository repo) throws GitException {
    List<GitRef> refs = new ArrayList<>();
    GitResult head = runner.run(repo.root(), List.of("rev-parse", "--verify", "-q", "HEAD"), null);
    if (head.ok()) {
      refs.add(new GitRef("HEAD", "HEAD", RefType.HEAD, head.stdoutText().trim(), null, false));
    }
    String out = runner.runOk(repo.root(), List.of(
      "for-each-ref",
      "--format=%(objectname)%00%(*objectname)%00%(refname)%00%(upstream:short)%00%(HEAD)",
      "refs/heads", "refs/remotes", "refs/tags"), null);
    for (String line : out.split("\n")) {
      String[] f = line.split("\0", -1);
      if (f.length < 5) {
        continue;
      }
      String hash = f[1].isEmpty() ? f[0] : f[1];
      String fullName = f[2];
      String upstream = f[3].isEmpty() ? null : f[3];
      boolean current = "*".equals(f[4].trim());
      if (fullName.startsWith("refs/heads/")) {
        refs.add(new GitRef(fullName.substring("refs/heads/".length()), fullName, RefType.LOCAL_BRANCH, hash, upstream, current));
      }
      else if (fullName.startsWith("refs/remotes/")) {
        String name = fullName.substring("refs/remotes/".length());
        if (!name.endsWith("/HEAD")) {
          refs.add(new GitRef(name, fullName, RefType.REMOTE_BRANCH, hash, null, false));
        }
      }
      else if (fullName.startsWith("refs/tags/")) {
        refs.add(new GitRef(fullName.substring("refs/tags/".length()), fullName, RefType.TAG, hash, null, false));
      }
    }
    return refs;
  }
}
