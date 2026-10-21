package gitbench.git;

import java.nio.charset.StandardCharsets;

/** The outcome of one git process: exit code, raw stdout, and stderr text. */
public record GitResult(int exitCode, byte[] stdout, String stderr) {
  public boolean ok() {
    return exitCode == 0;
  }

  public String stdoutText() {
    return new String(stdout, StandardCharsets.UTF_8);
  }
}
