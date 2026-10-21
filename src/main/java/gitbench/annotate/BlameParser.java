package gitbench.annotate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads the output of {@code git blame --porcelain}: a group header "hash original-line final-line [count]", the
 * commit headers on the first group of each commit, and one line of the file after a tab.
 */
public final class BlameParser {
  private static final int HASH_LENGTH = 40;

  private BlameParser() {
  }

  public static Blame parse(String output) {
    List<String> lineHashes = new ArrayList<>();
    Map<String, Builder> builders = new HashMap<>();
    Builder current = null;
    for (String line : output.split("\n", -1)) {
      if (line.startsWith("\t")) {
        if (current != null) {
          lineHashes.add(current.hash);
        }
        continue;
      }
      int space = line.indexOf(' ');
      String key = space < 0 ? line : line.substring(0, space);
      String value = space < 0 ? "" : line.substring(space + 1);
      if (key.length() == HASH_LENGTH && isHex(key)) {
        current = builders.computeIfAbsent(key, Builder::new);
        continue;
      }
      if (current != null) {
        current.read(key, value);
      }
    }
    Map<String, BlameCommit> commits = new HashMap<>();
    builders.forEach((hash, builder) -> commits.put(hash, builder.build()));
    return new Blame(lineHashes, commits);
  }

  private static boolean isHex(String text) {
    for (int i = 0; i < text.length(); i++) {
      if (Character.digit(text.charAt(i), 16) < 0) {
        return false;
      }
    }
    return true;
  }

  private static final class Builder {
    final String hash;
    String author = "";
    String authorMail = "";
    Instant authorTime = Instant.EPOCH;
    String summary = "";
    String filename;
    String previousHash;
    String previousFilename;

    Builder(String hash) {
      this.hash = hash;
    }

    void read(String key, String value) {
      switch (key) {
        case "author" -> author = value;
        case "author-mail" -> authorMail = value.startsWith("<") && value.endsWith(">") ? value.substring(1, value.length() - 1) : value;
        case "author-time" -> authorTime = Instant.ofEpochSecond(Long.parseLong(value.trim()));
        case "summary" -> summary = value;
        case "filename" -> {
          if (filename == null) {
            filename = value;
          }
        }
        case "previous" -> {
          int split = value.indexOf(' ');
          if (split > 0) {
            previousHash = value.substring(0, split);
            previousFilename = value.substring(split + 1);
          }
        }
        default -> {
          // The committer and the boundary mark are not shown.
        }
      }
    }

    BlameCommit build() {
      return new BlameCommit(hash, author, authorMail, authorTime, summary, filename, previousHash, previousFilename);
    }
  }
}
