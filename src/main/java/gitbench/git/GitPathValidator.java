package gitbench.git;

import java.nio.file.Files;
import java.nio.file.Path;

/** Checks the "Path to Git executable" preference. A bare command name such as {@code git} is found through PATH. */
public final class GitPathValidator {
  private GitPathValidator() {
  }

  /** Returns an error text, or {@code null} when the value is valid. */
  public static String error(String value) {
    String path = value == null ? "" : value.trim();
    if (path.isEmpty() || (!path.contains("/") && !path.contains("\\"))) {
      return null;
    }
    return Files.isRegularFile(Path.of(path)) ? null : "The file does not exist: " + path;
  }
}
