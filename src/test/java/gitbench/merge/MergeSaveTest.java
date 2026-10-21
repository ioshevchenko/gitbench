package gitbench.merge;

import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class MergeSaveTest {
  @Test
  void savesResultWithOriginalLineSeparators() throws Exception {
    Path file = Files.createTempFile("gitbench-merge", ".c");

    MergeResultWriter.write(file, "a\nb\n", "\r\n", StandardCharsets.UTF_8);

    assertThat(Files.readString(file)).isEqualTo("a\r\nb\r\n");
  }

  @Test
  void savesWithCharset() throws Exception {
    Path file = Files.createTempFile("gitbench-merge", ".c");
    Charset cp1251 = Charset.forName("windows-1251");

    MergeResultWriter.write(file, "// привет\n", "\n", cp1251);

    assertThat(Files.readAllBytes(file)).isEqualTo("// привет\n".getBytes(cp1251));
  }
}
