package gitbench.git;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Parses the output of {@code git status --porcelain=v2 -z}. */
public final class StatusParser {
  private StatusParser() {
  }

  public static List<FileChange> parse(byte[] porcelainV2z) {
    String[] records = new String(porcelainV2z, StandardCharsets.UTF_8).split("\0");
    List<FileChange> changes = new ArrayList<>();
    for (int i = 0; i < records.length; i++) {
      String record = records[i];
      if (record.isEmpty()) {
        continue;
      }
      switch (record.charAt(0)) {
        case '1' -> {
          String[] f = record.split(" ", 9);
          changes.add(new FileChange(f[8], null, kindOf(f[1])));
        }
        case '2' -> {
          String[] f = record.split(" ", 10);
          changes.add(new FileChange(f[9], records[++i], ChangeKind.RENAMED));
        }
        case 'u' -> {
          String[] f = record.split(" ", 11);
          changes.add(new FileChange(f[10], null, ChangeKind.CONFLICT));
        }
        case '?' -> changes.add(new FileChange(record.substring(2), null, ChangeKind.UNVERSIONED));
        default -> {
          // '#' headers and '!' ignored entries are not changes.
        }
      }
    }
    return changes;
  }

  private static ChangeKind kindOf(String xy) {
    if (xy.indexOf('D') >= 0) {
      return ChangeKind.DELETED;
    }
    if (xy.charAt(0) == 'A') {
      return ChangeKind.ADDED;
    }
    return ChangeKind.MODIFIED;
  }
}
