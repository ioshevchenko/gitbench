package gitbench;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class IconsRasterizedTest {
  @Test
  void iconsRasterizedForBothThemes() throws Exception {
    Path svgDir = BuildSmokeTest.projectDir().resolve("icons/svg");
    Path pngDir = BuildSmokeTest.projectDir().resolve("build/generated/icons/png");
    List<String> names;
    try (Stream<Path> files = Files.list(svgDir)) {
      names = files.map(p -> p.getFileName().toString())
        .filter(n -> n.endsWith(".svg") && !n.endsWith("_dark.svg"))
        .map(n -> n.substring(0, n.length() - 4))
        .toList();
    }
    assertThat(names).isNotEmpty();
    for (String theme : List.of("light", "dark")) {
      for (String name : names) {
        assertSize(pngDir.resolve(theme).resolve(name + ".png"), 16);
        assertSize(pngDir.resolve(theme).resolve(name + "@2x.png"), 32);
      }
    }
  }

  private static void assertSize(Path png, int size) throws Exception {
    assertThat(png).exists();
    BufferedImage image = ImageIO.read(png.toFile());
    assertThat(image.getWidth()).as(png.toString()).isEqualTo(size);
    assertThat(image.getHeight()).as(png.toString()).isEqualTo(size);
  }
}
