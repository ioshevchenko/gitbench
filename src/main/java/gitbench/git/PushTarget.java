package gitbench.git;

/**
 * Where a push goes.
 *
 * @param hasUpstream true when the branch already tracks {@code remote/branch}
 */
public record PushTarget(String remote, String branch, boolean hasUpstream) {
}
