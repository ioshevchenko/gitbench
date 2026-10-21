package gitbench.ui;

import gitbench.git.GitRepository;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Tells all Gitbench views that a repository changed: a commit, a checkout, a merge. Any thread can fire an event;
 * listeners are called on that thread and move to the UI thread themselves.
 */
public final class RepositoryEvents {
  private static final List<Consumer<GitRepository>> LISTENERS = new CopyOnWriteArrayList<>();

  private RepositoryEvents() {
  }

  public static void listen(Consumer<GitRepository> listener) {
    LISTENERS.add(listener);
  }

  public static void unlisten(Consumer<GitRepository> listener) {
    LISTENERS.remove(listener);
  }

  public static void fire(GitRepository repository) {
    for (Consumer<GitRepository> listener : LISTENERS) {
      listener.accept(repository);
    }
  }
}
