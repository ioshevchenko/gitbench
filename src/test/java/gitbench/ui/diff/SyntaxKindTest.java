package gitbench.ui.diff;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SyntaxKindTest {
  @Test
  void cAndCppSourcesUseCdtC() {
    assertThat(SyntaxKind.of(Path.of("Core/Src/main.c"))).isEqualTo(SyntaxKind.C);
    assertThat(SyntaxKind.of(Path.of("Core/Inc/main.H"))).isEqualTo(SyntaxKind.C);
    assertThat(SyntaxKind.of(Path.of("lib/driver.cpp"))).isEqualTo(SyntaxKind.C);
  }

  @Test
  void startupFilesUseCdtAssembler() {
    assertThat(SyntaxKind.of(Path.of("Core/Startup/startup_stm32f405rgtx.s"))).isEqualTo(SyntaxKind.ASSEMBLER);
    assertThat(SyntaxKind.of(Path.of("boot.S"))).isEqualTo(SyntaxKind.ASSEMBLER);
  }

  @Test
  void otherFilesGoToTheGenericEditor() {
    assertThat(SyntaxKind.of(Path.of("Makefile"))).isEqualTo(SyntaxKind.OTHER);
    assertThat(SyntaxKind.of(Path.of("board.ioc"))).isEqualTo(SyntaxKind.OTHER);
    assertThat(SyntaxKind.of(Path.of("STM32F405RGTX_FLASH.ld"))).isEqualTo(SyntaxKind.OTHER);
  }
}
