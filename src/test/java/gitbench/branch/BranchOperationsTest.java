package gitbench.branch;

import gitbench.git.ChangeKind;
import gitbench.git.FileChange;
import gitbench.git.GitCommandListener;
import gitbench.git.GitRepository;
import gitbench.git.GitRunner;
import gitbench.git.StatusService;
import gitbench.log.GitRef;
import gitbench.log.OperationResult;
import gitbench.log.RefType;
import gitbench.log.RefsLoader;
import gitbench.testutil.TempRepo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BranchOperationsTest {
  private final GitRunner runner = new GitRunner(() -> "git", GitCommandListener.NONE);
  private final BranchOperations ops = new BranchOperations(runner);

  private static TempRepo repo() {
    TempRepo repo = TempRepo.init();
    repo.write("a.c", "base\n");
    repo.commitAll("base");
    return repo;
  }

  private GitRef ref(TempRepo repo, String name) throws Exception {
    List<GitRef> refs = new RefsLoader(runner).load(new GitRepository(repo.root()));
    return refs.stream().filter(r -> r.name().equals(name)).findFirst().orElseThrow();
  }

  private static String current(TempRepo repo) {
    return repo.git("branch", "--show-current").trim();
  }

  /** A clone with origin/main and origin/remote-only. */
  private static TempRepo cloneWithRemoteBranch() {
    TempRepo remote = TempRepo.initBare();
    TempRepo source = repo();
    source.git("remote", "add", "origin", remote.root().toString());
    source.git("push", "-q", "origin", "main");
    source.git("switch", "-q", "-c", "remote-only");
    source.write("r.c", "r\n");
    source.commitAll("remote work");
    source.git("push", "-q", "origin", "remote-only");
    return TempRepo.cloneOf(remote);
  }

  @Test
  void checkoutLocal() throws Exception {
    TempRepo repo = repo();
    repo.git("branch", "dev");
    assertThat(ops.checkout(new GitRepository(repo.root()), ref(repo, "dev")).ok()).isTrue();
    assertThat(current(repo)).isEqualTo("dev");
  }

  @Test
  void checkoutRemoteCreatesTrackingBranch() throws Exception {
    TempRepo repo = cloneWithRemoteBranch();
    assertThat(ops.checkout(new GitRepository(repo.root()), ref(repo, "origin/remote-only")).ok()).isTrue();
    assertThat(current(repo)).isEqualTo("remote-only");
    assertThat(repo.git("rev-parse", "--abbrev-ref", "@{u}").trim()).isEqualTo("origin/remote-only");
  }

  @Test
  void checkoutRemoteWhenLocalExistsUsesLocal() throws Exception {
    TempRepo repo = cloneWithRemoteBranch();
    repo.git("branch", "remote-only", "origin/remote-only");
    repo.git("switch", "-q", "main");
    OperationResult result = ops.checkout(new GitRepository(repo.root()), ref(repo, "origin/remote-only"));
    assertThat(result.ok()).isTrue();
    assertThat(current(repo)).isEqualTo("remote-only");
  }

  @Test
  void checksOutCyrillicBranch() throws Exception {
    TempRepo repo = repo();
    repo.git("branch", "feature/uart/фикс");
    assertThat(ops.checkout(new GitRepository(repo.root()), ref(repo, "feature/uart/фикс")).ok()).isTrue();
    assertThat(current(repo)).isEqualTo("feature/uart/фикс");
  }

  @Test
  void newBranchFromRef() throws Exception {
    TempRepo repo = repo();
    String base = repo.git("rev-parse", "HEAD").trim();
    repo.write("a.c", "next\n");
    repo.commitAll("next");
    assertThat(ops.newBranch(new GitRepository(repo.root()), "hotfix", base, true, false).ok()).isTrue();
    assertThat(current(repo)).isEqualTo("hotfix");
    assertThat(repo.git("rev-parse", "HEAD").trim()).isEqualTo(base);
  }

  @Test
  void renameBranch() throws Exception {
    TempRepo repo = repo();
    repo.git("branch", "old-name");
    assertThat(ops.rename(new GitRepository(repo.root()), "old-name", "new-name").ok()).isTrue();
    assertThat(repo.git("branch", "--list", "new-name").trim()).isNotEmpty();
    assertThat(repo.git("branch", "--list", "old-name").trim()).isEmpty();
  }

  @Test
  void deleteMergedBranch() throws Exception {
    TempRepo repo = repo();
    repo.git("branch", "merged");
    assertThat(ops.delete(new GitRepository(repo.root()), ref(repo, "merged"), false).ok()).isTrue();
    assertThat(repo.git("branch", "--list", "merged").trim()).isEmpty();
  }

  @Test
  void deleteUnmergedNeedsForce() throws Exception {
    TempRepo repo = repo();
    repo.git("switch", "-q", "-c", "unmerged");
    repo.write("u.c", "u\n");
    repo.commitAll("unmerged work");
    repo.git("switch", "-q", "main");
    GitRepository r = new GitRepository(repo.root());
    OperationResult refused = ops.delete(r, ref(repo, "unmerged"), false);
    assertThat(refused.ok()).isFalse();
    assertThat(refused.needsForce()).isTrue();
    assertThat(ops.delete(r, ref(repo, "unmerged"), true).ok()).isTrue();
    assertThat(repo.git("branch", "--list", "unmerged").trim()).isEmpty();
  }

  @Test
  void deleteRemoteBranch() throws Exception {
    TempRepo repo = cloneWithRemoteBranch();
    assertThat(ops.delete(new GitRepository(repo.root()), ref(repo, "origin/remote-only"), false).ok()).isTrue();
    assertThat(repo.git("ls-remote", "--heads", "origin", "remote-only").trim()).isEmpty();
  }

  @Test
  void mergeIntoCurrent() throws Exception {
    TempRepo repo = repo();
    repo.git("switch", "-q", "-c", "feature");
    repo.write("f.c", "f\n");
    repo.commitAll("feature");
    repo.git("switch", "-q", "main");
    assertThat(ops.mergeIntoCurrent(new GitRepository(repo.root()), "feature").ok()).isTrue();
    assertThat(repo.root().resolve("f.c")).exists();
  }

  @Test
  void mergeWithConflictReportsConflict() throws Exception {
    TempRepo repo = repo();
    repo.git("switch", "-q", "-c", "feature");
    repo.write("a.c", "feature\n");
    repo.commitAll("feature edits a");
    repo.git("switch", "-q", "main");
    repo.write("a.c", "main\n");
    repo.commitAll("main edits a");
    GitRepository r = new GitRepository(repo.root());
    OperationResult result = ops.mergeIntoCurrent(r, "feature");
    assertThat(result.ok()).isFalse();
    assertThat(result.conflicts()).isTrue();
    assertThat(result.message()).contains("Conflicts during merge");
    assertThat(new StatusService(runner).status(r)).contains(new FileChange("a.c", null, ChangeKind.CONFLICT));
  }

  @Test
  void rebaseCurrentOnto() throws Exception {
    TempRepo repo = repo();
    repo.git("switch", "-q", "-c", "feature");
    repo.write("f.c", "f\n");
    repo.commitAll("feature");
    repo.git("switch", "-q", "main");
    repo.write("m.c", "m\n");
    repo.commitAll("main");
    repo.git("switch", "-q", "feature");
    assertThat(ops.rebaseCurrentOnto(new GitRepository(repo.root()), "main").ok()).isTrue();
    assertThat(repo.git("rev-parse", "HEAD~1").trim()).isEqualTo(repo.git("rev-parse", "main").trim());
  }

  @Test
  void updateSelectedFastForwardsNonCurrent() throws Exception {
    TempRepo remote = TempRepo.initBare();
    TempRepo repo = repo();
    repo.git("remote", "add", "origin", remote.root().toString());
    repo.git("push", "-q", "-u", "origin", "main");
    TempRepo other = TempRepo.cloneOf(remote);
    other.write("x.c", "x\n");
    other.commitAll("x");
    other.git("push", "-q");
    repo.git("switch", "-q", "-c", "work");
    GitRepository r = new GitRepository(repo.root());
    assertThat(ops.updateSelected(r, ref(repo, "main")).ok()).isTrue();
    assertThat(repo.git("rev-parse", "main").trim()).isEqualTo(other.git("rev-parse", "HEAD").trim());
  }

  @Test
  void updateSelectedPullsCurrent() throws Exception {
    TempRepo remote = TempRepo.initBare();
    TempRepo repo = repo();
    repo.git("remote", "add", "origin", remote.root().toString());
    repo.git("push", "-q", "-u", "origin", "main");
    TempRepo other = TempRepo.cloneOf(remote);
    other.write("x.c", "x\n");
    other.commitAll("x");
    other.git("push", "-q");
    assertThat(ops.updateSelected(new GitRepository(repo.root()), ref(repo, "main")).ok()).isTrue();
    assertThat(repo.root().resolve("x.c")).exists();
  }

  @Test
  void refTypesAreLocal() throws Exception {
    assertThat(ref(repo(), "main").type()).isEqualTo(RefType.LOCAL_BRANCH);
  }
}
