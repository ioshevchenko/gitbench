package gitbench;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class BuildSmokeTest {
  static Path projectDir() {
    return Path.of(System.getProperty("gitbench.projectDir"));
  }

  @Test
  void manifestHasBundleName() throws Exception {
    try (InputStream in = Files.newInputStream(projectDir().resolve("META-INF/MANIFEST.MF"))) {
      Manifest manifest = new Manifest(in);
      assertThat(manifest.getMainAttributes().getValue("Bundle-SymbolicName")).startsWith("gitbench");
    }
  }

  @Test
  void shadowJarEmbedsKotlinAndFastutil() throws Exception {
    Path jar;
    try (Stream<Path> files = Files.list(projectDir().resolve("build/libs"))) {
      jar = files.filter(p -> p.getFileName().toString().matches("gitbench_.*\\.jar"))
        .max(Comparator.comparing(p -> p.toFile().lastModified()))
        .orElseThrow();
    }
    try (JarFile file = new JarFile(jar.toFile())) {
      assertThat(file.getEntry("kotlin/collections/CollectionsKt.class")).isNotNull();
      assertThat(file.getEntry("it/unimi/dsi/fastutil/ints/IntArrayList.class")).isNotNull();
      assertThat(file.getEntry("plugin.xml")).isNotNull();
    }
    assertThat(Files.size(jar)).isLessThan(4L * 1024 * 1024);
  }
}
