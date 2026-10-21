package gitbench.git;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class StatusParserTest {
  private static final String H = "1111111111111111111111111111111111111111";

  private static byte[] z(String... records) {
    return (String.join("\0", records) + "\0").getBytes(StandardCharsets.UTF_8);
  }

  private static String ordinary(String xy, String path) {
    return "1 " + xy + " N... 100644 100644 100644 " + H + " " + H + " " + path;
  }

  @Test
  void parsesModifiedInWorktree() {
    assertThat(StatusParser.parse(z(ordinary(".M", "src/a.c"))))
      .containsExactly(new FileChange("src/a.c", null, ChangeKind.MODIFIED));
  }

  @Test
  void parsesStagedAdd() {
    assertThat(StatusParser.parse(z(ordinary("A.", "new.c"))))
      .containsExactly(new FileChange("new.c", null, ChangeKind.ADDED));
  }

  @Test
  void addedThenModifiedIsStillAdded() {
    assertThat(StatusParser.parse(z(ordinary("AM", "new.c"))))
      .containsExactly(new FileChange("new.c", null, ChangeKind.ADDED));
  }

  @Test
  void parsesDeletedInWorktree() {
    assertThat(StatusParser.parse(z(ordinary(".D", "gone.c"))))
      .containsExactly(new FileChange("gone.c", null, ChangeKind.DELETED));
  }

  @Test
  void parsesRenameWithOrigPath() {
    String rename = "2 R. N... 100644 100644 100644 " + H + " " + H + " R100 new.c";
    assertThat(StatusParser.parse(z(rename, "old.c")))
      .containsExactly(new FileChange("new.c", "old.c", ChangeKind.RENAMED));
  }

  @Test
  void parsesUntracked() {
    assertThat(StatusParser.parse(z("? notes.txt")))
      .containsExactly(new FileChange("notes.txt", null, ChangeKind.UNVERSIONED));
  }

  @Test
  void parsesUnmerged() {
    String unmerged = "u UU N... 100644 100644 100644 100644 " + H + " " + H + " " + H + " conflict.c";
    assertThat(StatusParser.parse(z(unmerged)))
      .containsExactly(new FileChange("conflict.c", null, ChangeKind.CONFLICT));
  }

  @Test
  void ignoresIgnoredEntriesAndHeaders() {
    assertThat(StatusParser.parse(z("# branch.oid " + H, "! build/"))).isEmpty();
  }

  @Test
  void parsesUnicodeAndSpacesWithNulSeparators() {
    assertThat(StatusParser.parse(z(ordinary(".M", "Core/Src/мой файл.c"), "? Docs/read me.txt")))
      .containsExactly(
        new FileChange("Core/Src/мой файл.c", null, ChangeKind.MODIFIED),
        new FileChange("Docs/read me.txt", null, ChangeKind.UNVERSIONED));
  }

  @Test
  void emptyOutputGivesEmptyList() {
    assertThat(StatusParser.parse(new byte[0])).isEmpty();
  }
}
