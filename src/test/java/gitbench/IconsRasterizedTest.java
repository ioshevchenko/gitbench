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
      for (String svgName : names) {
        // An IntelliJ size suffix such as @14x14 sets the size and is dropped from the PNG name.
        int at = svgName.indexOf('@');
        String name = at < 0 ? svgName : svgName.substring(0, at);
        int size = at < 0 ? 16 : Integer.parseInt(svgName.substring(at + 1, svgName.indexOf('x', at)));
        assertSize(pngDir.resolve(theme).resolve(name + ".png"), size);
        assertSize(pngDir.resolve(theme).resolve(name + "@2x.png"), size * 2);
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
