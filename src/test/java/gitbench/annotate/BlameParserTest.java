package gitbench.annotate;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class BlameParserTest {
  private static final String A = "38d38d8bf65ee3336866358a66ec449c7f7e2dbf";
  private static final String B = "4436822abf9b78ddca22588cd7345fb955c06492";
  private static final String ZERO = "0000000000000000000000000000000000000000";

  private static final String OUTPUT = String.join("\n",
    A + " 1 1 1",
    "author Ann",
    "author-mail <a@x>",
    "author-time 1792619495",
    "author-tz +0300",
    "committer Ann",
    "committer-mail <a@x>",
    "committer-time 1792619495",
    "committer-tz +0300",
    "summary first\tline Привет",
    "boundary",
    "filename old name.c",
    "\ta",
    B + " 2 2 2",
    "author Bob",
    "author-mail <b@x>",
    "author-time 1792619600",
    "author-tz +0300",
    "committer Bob",
    "committer-mail <b@x>",
    "committer-time 1792619600",
    "committer-tz +0300",
    "summary second",
    "previous " + A + " old name.c",
    "filename f.c",
    "\tB",
    B + " 3 3",
    "\tc",
    ZERO + " 4 4 1",
    "author External file (--contents)",
    "author-mail <external.file>",
    "author-time 1792619700",
    "author-tz +0300",
    "committer External file (--contents)",
    "committer-mail <external.file>",
    "committer-time 1792619700",
    "committer-tz +0300",
    "summary Version of f.c from standard input",
    "previous " + B + " f.c",
    "filename f.c",
    "\tnew",
    "");

  @Test
  void parsesHeadersOnceAndLinesForEach() {
    Blame blame = BlameParser.parse(OUTPUT);

    assertThat(blame.lineHashes()).containsExactly(A, B, B, ZERO);
    assertThat(blame.commitAt(1)).hasValueSatisfying(c -> {
      assertThat(c.hash()).isEqualTo(B);
      assertThat(c.author()).isEqualTo("Bob");
      assertThat(c.authorMail()).isEqualTo("b@x");
      assertThat(c.authorTime()).isEqualTo(Instant.ofEpochSecond(1792619600L));
    });
    assertThat(blame.commitAt(2).map(BlameCommit::hash)).contains(B);
  }

  @Test
  void zeroHashLineIsUncommitted() {
    Blame blame = BlameParser.parse(OUTPUT);

    assertThat(blame.commitAt(3)).isEmpty();
    assertThat(blame.commitAt(99)).isEmpty();
  }

  @Test
  void readsPreviousAndFilename() {
    Blame blame = BlameParser.parse(OUTPUT);

    BlameCommit first = blame.commitAt(0).orElseThrow();
    assertThat(first.filename()).isEqualTo("old name.c");
    assertThat(first.previousHash()).isNull();
    assertThat(first.previousFilename()).isNull();
    BlameCommit second = blame.commitAt(1).orElseThrow();
    assertThat(second.previousHash()).isEqualTo(A);
    assertThat(second.previousFilename()).isEqualTo("old name.c");
    assertThat(second.filename()).isEqualTo("f.c");
  }

  @Test
  void summaryWithTabsAndUnicode() {
    assertThat(BlameParser.parse(OUTPUT).commitAt(0).orElseThrow().summary()).isEqualTo("first\tline Привет");
  }
}
