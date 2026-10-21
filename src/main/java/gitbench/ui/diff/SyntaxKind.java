package gitbench.ui.diff;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

/** Which highlighter colors a file in the diff and merge windows. */
public enum SyntaxKind {
  /** C and C++ sources and headers: the CDT C configuration. */
  C,
  /** Assembler sources such as the STM32 startup files: the CDT assembler configuration. */
  ASSEMBLER,
  /** Everything else: a Generic Editor highlighter for the content type, if a plugin gives one. */
  OTHER;

  private static final Set<String> C_EXTENSIONS = Set.of("c", "h", "cpp", "hpp", "cc", "cxx", "hh", "hxx", "c++", "h++", "inl");
  private static final Set<String> ASSEMBLER_EXTENSIONS = Set.of("s", "asm");

  public static SyntaxKind of(Path file) {
    String name = file.getFileName().toString();
    int dot = name.lastIndexOf('.');
    String extension = dot < 0 ? "" : name.substring(dot + 1);
    String lower = extension.toLowerCase(Locale.ROOT);
    if (C_EXTENSIONS.contains(lower)) {
      return C;
    }
    // ".S" is assembler run through the C preprocessor; CDT colors it as assembler too.
    if (ASSEMBLER_EXTENSIONS.contains(lower)) {
      return ASSEMBLER;
    }
    return OTHER;
  }
}
