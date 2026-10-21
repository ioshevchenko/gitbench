package gitbench.log;

import gitbench.git.GitCommandListener;
import gitbench.git.GitRepository;
import gitbench.git.GitRunner;
import gitbench.testutil.TempRepo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RefsLoaderTest {
  private final RefsLoader loader = new RefsLoader(new GitRunner(() -> "git", GitCommandListener.NONE));

  private static GitRef ref(List<GitRef> refs, String name) {
    return refs.stream().filter(r -> r.name().equals(name)).findFirst().orElseThrow(() -> new AssertionError("no ref " + name));
  }

  @Test
  void loadsLocalRemoteTagsAndCurrent() throws Exception {
    TempRepo remote = TempRepo.initBare();
    TempRepo repo = TempRepo.init();
    repo.git("remote", "add", "origin", remote.root().toString());
    repo.write("a.c", "a\n");
    repo.commitAll("first");
    repo.git("push", "-q", "-u", "origin", "main");
    repo.git("branch", "feature/x");
    repo.git("tag", "-a", "v1", "-m", "release");
    String head = repo.git("rev-parse", "HEAD").trim();

    List<GitRef> refs = loader.load(new GitRepository(repo.root()));

    GitRef main = ref(refs, "main");
    assertThat(main.type()).isEqualTo(RefType.LOCAL_BRANCH);
    assertThat(main.current()).isTrue();
    assertThat(main.upstream()).isEqualTo("origin/main");
    assertThat(main.hash()).isEqualTo(head);
    assertThat(ref(refs, "feature/x").current()).isFalse();
    assertThat(ref(refs, "origin/main").type()).isEqualTo(RefType.REMOTE_BRANCH);
    GitRef tag = ref(refs, "v1");
    assertThat(tag.type()).isEqualTo(RefType.TAG);
    assertThat(tag.hash()).isEqualTo(head);
    assertThat(ref(refs, "HEAD").type()).isEqualTo(RefType.HEAD);
    assertThat(refs).noneMatch(r -> r.name().endsWith("/HEAD"));
  }

  @Test
  void detachedHeadHasNoCurrentBranch() throws Exception {
    TempRepo repo = TempRepo.init();
    repo.write("a.c", "a\n");
    repo.commitAll("first");
    repo.git("checkout", "-q", "--detach");
    List<GitRef> refs = loader.load(new GitRepository(repo.root()));
    assertThat(refs).noneMatch(GitRef::current);
    assertThat(ref(refs, "HEAD").hash()).isEqualTo(repo.git("rev-parse", "HEAD").trim());
  }

  @Test
  void emptyRepositoryHasNoRefs() throws Exception {
    assertThat(loader.load(new GitRepository(TempRepo.init().root()))).isEmpty();
  }
}
