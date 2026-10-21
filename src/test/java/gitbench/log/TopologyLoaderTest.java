package gitbench.log;

import gitbench.git.GitCommandListener;
import gitbench.git.GitRepository;
import gitbench.git.GitRunner;
import gitbench.testutil.TempRepo;
import com.intellij.vcs.log.graph.GraphCommit;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TopologyLoaderTest {
  private final GitRunner runner = new GitRunner(() -> "git", GitCommandListener.NONE);
  private final RefsLoader refs = new RefsLoader(runner);
  private final TopologyLoader loader = new TopologyLoader(runner);

  private CommitTopology load(TempRepo repo) throws Exception {
    GitRepository r = new GitRepository(repo.root());
    return loader.load(r, refs.load(r));
  }

  @Test
  void parentsAreIndexes() throws Exception {
    TempRepo repo = TempRepo.init();
    repo.write("a.c", "a\n");
    repo.commitAll("base");
    repo.git("switch", "-q", "-c", "feature");
    repo.write("f.c", "f\n");
    repo.commitAll("feature work");
    repo.git("switch", "-q", "main");
    repo.write("m.c", "m\n");
    repo.commitAll("main work");
    repo.git("merge", "-q", "--no-edit", "feature");

    CommitTopology topology = load(repo);

    assertThat(topology.commits()).hasSize(4);
    GraphCommit<Integer> merge = topology.commits().get(0);
    assertThat(topology.index().hashOf(merge.getId())).isEqualTo(repo.git("rev-parse", "HEAD").trim());
    assertThat(merge.getParents()).hasSize(2);
    assertThat(topology.index().hashOf(merge.getParents().get(0))).isEqualTo(repo.git("rev-parse", "HEAD^1").trim());
    assertThat(topology.index().hashOf(merge.getParents().get(1))).isEqualTo(repo.git("rev-parse", "HEAD^2").trim());
  }

  @Test
  void includesCommitsOfAllBranches() throws Exception {
    TempRepo repo = TempRepo.init();
    repo.write("a.c", "a\n");
    repo.commitAll("base");
    repo.git("switch", "-q", "-c", "side");
    repo.write("s.c", "s\n");
    repo.commitAll("side only");
    repo.git("switch", "-q", "main");
    assertThat(load(repo).commits()).hasSize(2);
  }

  @Test
  void emptyRepositoryHasNoCommits() throws Exception {
    assertThat(load(TempRepo.init()).commits()).isEmpty();
  }

  @Test
  void loadsTwentyThousandCommitsUnderThreeSeconds() throws Exception {
    TempRepo repo = TempRepo.init();
    ByteArrayOutputStream stream = new ByteArrayOutputStream();
    for (int i = 1; i <= 20_000; i++) {
      String message = "commit " + i;
      StringBuilder block = new StringBuilder()
        .append("commit refs/heads/main\n")
        .append("mark :").append(i).append('\n')
        .append("committer Test User <test@example.com> ").append(1_600_000_000L + i).append(" +0000\n")
        .append("data ").append(message.length()).append('\n').append(message).append('\n');
      if (i > 1) {
        block.append("from :").append(i - 1).append('\n');
      }
      stream.writeBytes(block.toString().getBytes(StandardCharsets.UTF_8));
    }
    repo.gitWithInput(stream.toByteArray(), "fast-import", "--quiet");

    long start = System.nanoTime();
    CommitTopology topology = load(repo);
    long millis = (System.nanoTime() - start) / 1_000_000;

    assertThat(topology.commits()).hasSize(20_000);
    assertThat(millis).isLessThan(3000);
  }

  @Test
  void indexIsStable() throws Exception {
    TempRepo repo = TempRepo.init();
    repo.write("a.c", "a\n");
    repo.commitAll("base");
    CommitTopology topology = load(repo);
    String hash = repo.git("rev-parse", "HEAD").trim();
    assertThat(topology.index().hashOf(topology.index().indexOf(hash))).isEqualTo(hash);
    assertThat(topology.index().indexOf("0000000000000000000000000000000000000000")).isEqualTo(-1);
    assertThat(List.of(topology.index().size())).containsExactly(1);
  }
}
