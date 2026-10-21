package gitbench.ui.diff;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CdtHighlightingTest {
  @Test
  void cAndCppSourcesGetHighlighting() {
    assertThat(CdtHighlighting.isCSource(Path.of("Core/Src/main.c"))).isTrue();
    assertThat(CdtHighlighting.isCSource(Path.of("Core/Inc/main.H"))).isTrue();
    assertThat(CdtHighlighting.isCSource(Path.of("lib/driver.cpp"))).isTrue();
  }

  @Test
  void otherFilesStayPlain() {
    assertThat(CdtHighlighting.isCSource(Path.of("startup_stm32f4.s"))).isFalse();
    assertThat(CdtHighlighting.isCSource(Path.of("Makefile"))).isFalse();
    assertThat(CdtHighlighting.isCSource(Path.of("board.ioc"))).isFalse();
  }
}
