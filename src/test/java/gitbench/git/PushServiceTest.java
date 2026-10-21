package gitbench.git;

import gitbench.testutil.TempRepo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PushServiceTest {
  private final PushService push = new PushService(new GitRunner(() -> "git", GitCommandListener.NONE));

  private static TempRepo workWithRemote(TempRepo remote) {
    TempRepo work = TempRepo.init();
    work.git("remote", "add", "origin", remote.root().toString());
    return work;
  }

  private static void commit(TempRepo repo, String file, String msg) {
    repo.write(file, msg + "\n");
    repo.commitAll(msg);
  }

  @Test
  void outgoingWithoutUpstreamListsAllCommits() throws Exception {
    TempRepo work = workWithRemote(TempRepo.initBare());
    commit(work, "a.c", "first");
    commit(work, "b.c", "second");
    GitRepository r = new GitRepository(work.root());
    PushTarget target = push.defaultTarget(r);
    assertThat(target).isEqualTo(new PushTarget("origin", "main", false));
    assertThat(push.outgoing(r, target)).extracting(OutgoingCommit::subject).containsExactly("second", "first");
  }

  @Test
  void pushSetsUpstreamOnFirstPush() throws Exception {
    TempRepo work = workWithRemote(TempRepo.initBare());
    commit(work, "a.c", "first");
    GitRepository r = new GitRepository(work.root());
    PushResult result = push.push(r, push.defaultTarget(r));
    assertThat(result.status()).isEqualTo(PushResult.Status.OK);
    assertThat(work.git("rev-parse", "--abbrev-ref", "@{u}").trim()).isEqualTo("origin/main");
    assertThat(push.defaultTarget(r).hasUpstream()).isTrue();
  }

  @Test
  void outgoingListsCommitsAheadOfUpstream() throws Exception {
    TempRepo work = workWithRemote(TempRepo.initBare());
    commit(work, "a.c", "first");
    GitRepository r = new GitRepository(work.root());
    push.push(r, push.defaultTarget(r));
    commit(work, "b.c", "second");
    commit(work, "c.c", "third");
    List<OutgoingCommit> outgoing = push.outgoing(r, push.defaultTarget(r));
    assertThat(outgoing).extracting(OutgoingCommit::subject).containsExactly("third", "second");
    assertThat(outgoing.get(0).author()).isEqualTo("Test User");
    assertThat(outgoing.get(0).shortHash()).isEqualTo(work.git("rev-parse", "--short", "HEAD").trim());
  }

  @Test
  void rejectedPushIsReported() throws Exception {
    TempRepo remote = TempRepo.initBare();
    TempRepo work = workWithRemote(remote);
    commit(work, "a.c", "first");
    GitRepository r = new GitRepository(work.root());
    push.push(r, push.defaultTarget(r));
    TempRepo other = TempRepo.cloneOf(remote);
    commit(other, "x.c", "other");
    other.git("push", "-q");
    commit(work, "b.c", "mine");
    PushResult result = push.push(r, push.defaultTarget(r));
    assertThat(result.status()).isEqualTo(PushResult.Status.REJECTED);
    assertThat(result.message()).contains("rejected");
  }

  @Test
  void upToDate() throws Exception {
    TempRepo work = workWithRemote(TempRepo.initBare());
    commit(work, "a.c", "first");
    GitRepository r = new GitRepository(work.root());
    push.push(r, push.defaultTarget(r));
    assertThat(push.push(r, push.defaultTarget(r)).status()).isEqualTo(PushResult.Status.UP_TO_DATE);
  }

  @Test
  void pushesNonCurrentBranch() throws Exception {
    TempRepo remote = TempRepo.initBare();
    TempRepo work = workWithRemote(remote);
    commit(work, "a.c", "first");
    work.git("branch", "side");
    GitRepository r = new GitRepository(work.root());
    PushResult result = push.push(r, new PushTarget("origin", "side", false), "side");
    assertThat(result.status()).isEqualTo(PushResult.Status.OK);
    assertThat(work.git("ls-remote", "--heads", "origin", "side").trim()).isNotEmpty();
    assertThat(work.git("branch", "--show-current").trim()).isEqualTo("main");
  }

  @Test
  void targetAndOutgoingOfNonCurrentBranch() throws Exception {
    TempRepo work = workWithRemote(TempRepo.initBare());
    commit(work, "a.c", "first");
    GitRepository r = new GitRepository(work.root());
    push.push(r, push.defaultTarget(r));
    work.git("switch", "-q", "-c", "side");
    commit(work, "s.c", "side work");
    work.git("switch", "-q", "main");
    PushTarget target = push.defaultTarget(r, "side");
    assertThat(target).isEqualTo(new PushTarget("origin", "side", false));
    assertThat(push.outgoing(r, target, "side")).extracting(OutgoingCommit::subject).containsExactly("side work");
  }

  @Test
  void noRemoteIsError() throws Exception {
    TempRepo work = TempRepo.init();
    commit(work, "a.c", "first");
    GitRepository r = new GitRepository(work.root());
    PushResult result = push.push(r, push.defaultTarget(r));
    assertThat(result.status()).isEqualTo(PushResult.Status.ERROR);
    assertThat(result.message()).contains("No remote");
  }

  @Test
  void changedFilesOfCommit() throws Exception {
    TempRepo work = workWithRemote(TempRepo.initBare());
    commit(work, "a.c", "first");
    String hash = work.git("rev-parse", "--short", "HEAD").trim();
    assertThat(push.changedFiles(new GitRepository(work.root()), hash))
      .containsExactly(new FileChange("a.c", null, ChangeKind.ADDED));
  }
}
