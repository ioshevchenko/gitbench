package gitbench.branch;

import gitbench.git.GitCommandListener;
import gitbench.git.GitRepository;
import gitbench.git.GitRunner;
import gitbench.testutil.TempRepo;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AheadBehindTest {
  @Test
  void aheadBehindCounts() throws Exception {
    TempRepo remote = TempRepo.initBare();
    TempRepo repo = TempRepo.init();
    repo.git("remote", "add", "origin", remote.root().toString());
    repo.write("a.c", "a\n");
    repo.commitAll("first");
    repo.git("push", "-q", "-u", "origin", "main");
    TempRepo other = TempRepo.cloneOf(remote);
    other.write("x.c", "x\n");
    other.commitAll("x1");
    other.write("y.c", "y\n");
    other.commitAll("x2");
    other.git("push", "-q");
    repo.git("fetch", "-q");
    repo.write("b.c", "b\n");
    repo.commitAll("local");

    Map<String, AheadBehind.Counts> counts = AheadBehind.load(new GitRunner(() -> "git", GitCommandListener.NONE),
                                                              new GitRepository(repo.root()));

    assertThat(counts.get("main")).isEqualTo(new AheadBehind.Counts(2, 1));
  }

  @Test
  void branchWithoutUpstreamHasNoCounts() throws Exception {
    TempRepo repo = TempRepo.init();
    repo.write("a.c", "a\n");
    repo.commitAll("first");
    assertThat(AheadBehind.load(new GitRunner(() -> "git", GitCommandListener.NONE), new GitRepository(repo.root()))).isEmpty();
  }
}
