package gitbench.log;

import com.intellij.vcs.log.graph.GraphColorManager;

import java.util.Comparator;
import java.util.List;
import java.util.function.IntFunction;

/**
 * Colors of graph lines, with the logic of {@code GraphColorManagerImpl} from IntelliJ: the fragment that starts at a
 * branch head takes the hash of the branch name, every other fragment takes its own index.
 */
public final class HeadRefColors implements GraphColorManager<Integer> {
  static final int DEFAULT_COLOR = 0;

  /** The order of refs for labels and colors: current branch, local, remote, tag, HEAD, then by name. */
  static final Comparator<GitRef> PRIORITY = Comparator
    .comparingInt((GitRef ref) -> ref.current() ? 0 : switch (ref.type()) {
      case LOCAL_BRANCH -> 1;
      case REMOTE_BRANCH -> 2;
      case TAG -> 3;
      case HEAD -> 4;
    })
    .thenComparing(GitRef::name);

  private final IntFunction<List<GitRef>> refsOfCommit;

  public HeadRefColors(IntFunction<List<GitRef>> refsOfCommit) {
    this.refsOfCommit = refsOfCommit;
  }

  @Override
  public int getColor(Integer headCommit, int headFragmentIndex, int fragmentIndex) {
    if (headFragmentIndex != fragmentIndex) {
      return fragmentIndex;
    }
    return refsOfCommit.apply(headCommit).stream()
      .filter(ref -> ref.type() != RefType.HEAD)
      .min(PRIORITY)
      .map(ref -> ref.name().hashCode())
      .orElse(DEFAULT_COLOR);
  }
}
