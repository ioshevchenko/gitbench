package gitbench.git;

import java.nio.file.Path;

/** A git working tree, identified by its root folder. */
public record GitRepository(Path root) {
  public String name() {
    return root.getFileName().toString();
  }
}
