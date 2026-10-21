package gitbench.log;

import gitbench.git.GitCommandListener;
import gitbench.git.GitRepository;
import gitbench.git.GitRunner;
import gitbench.testutil.TempRepo;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DetailsLoaderTest {
  private final DetailsLoader loader = new DetailsLoader(new GitRunner(() -> "git", GitCommandListener.NONE));

  @Test
  void loadsSubjectAuthorAndMultilineMessage() throws Exception {
    TempRepo repo = TempRepo.init();
    repo.write("a.c", "a\n");
    repo.git("add", "-A");
    repo.git("commit", "-q", "-m", "Добавлен UART", "-m", "Тело сообщения\nвторая строка");
    String hash = repo.git("rev-parse", "HEAD").trim();

    Map<String, CommitDetails> details = loader.load(new GitRepository(repo.root()), List.of(hash));

    CommitDetails d = details.get(hash);
    assertThat(d.subject()).isEqualTo("Добавлен UART");
    assertThat(d.fullMessage()).isEqualTo("Добавлен UART\n\nТело сообщения\nвторая строка");
    assertThat(d.authorName()).isEqualTo("Test User");
    assertThat(d.authorEmail()).isEqualTo("test@example.com");
    assertThat(d.parents()).isEmpty();
    assertThat(d.authorTime()).isNotNull();
  }

  @Test
  void keepsRequestedHashesOnly() throws Exception {
    TempRepo repo = TempRepo.init();
    repo.write("a.c", "a\n");
    repo.commitAll("first");
    repo.write("a.c", "b\n");
    repo.commitAll("second");
    String second = repo.git("rev-parse", "HEAD").trim();
    String first = repo.git("rev-parse", "HEAD~1").trim();

    Map<String, CommitDetails> details = loader.load(new GitRepository(repo.root()), List.of(second));

    assertThat(details).containsOnlyKeys(second);
    assertThat(details.get(second).parents()).containsExactly(first);
  }

  @Test
  void emptyRequestGivesEmptyMap() throws Exception {
    TempRepo repo = TempRepo.init();
    assertThat(loader.load(new GitRepository(repo.root()), List.of())).isEmpty();
  }
}
