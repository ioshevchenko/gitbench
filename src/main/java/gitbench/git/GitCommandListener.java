package gitbench.git;

import java.nio.file.Path;
import java.util.List;

/** Receives every git command after it ends. The Git console uses it. */
@FunctionalInterface
public interface GitCommandListener {
  GitCommandListener NONE = (workDir, args, result) -> { };

  void onCommand(Path workDir, List<String> args, GitResult result);
}
