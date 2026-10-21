package fleet.util.multiplatform

/** Gitbench: marks a JVM implementation of a multiplatform function. IntelliJ links them at build time; Gitbench calls them directly. */
@Target(AnnotationTarget.FUNCTION)
annotation class Actual
