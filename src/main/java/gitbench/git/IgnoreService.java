package gitbench.git;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/** Adds paths to the {@code .gitignore} file in the repository root. */
public final class IgnoreService {
  public void addToGitignore(GitRepository repo, List<String> paths) throws GitException {
    Path gitignore = repo.root().resolve(".gitignore");
    try {
      String text = Files.exists(gitignore) ? Files.readString(gitignore, StandardCharsets.UTF_8) : "";
      List<String> existing = text.lines().map(String::strip).toList();
      List<String> toAdd = new ArrayList<>();
      for (String path : paths) {
        String line = "/" + path;
        if (!existing.contains(line) && !toAdd.contains(line)) {
          toAdd.add(line);
        }
      }
      if (toAdd.isEmpty()) {
        return;
      }
      StringBuilder out = new StringBuilder();
      if (!text.isEmpty() && !text.endsWith("\n")) {
        out.append('\n');
      }
      toAdd.forEach(line -> out.append(line).append('\n'));
      Files.writeString(gitignore, out, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }
    catch (IOException e) {
      throw new GitException("Cannot update .gitignore: " + e.getMessage(), e);
    }
  }
}
