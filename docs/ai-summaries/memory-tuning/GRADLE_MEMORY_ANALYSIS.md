# Gradle Memory Issues - Investigation & Fix

> Correction: The original analysis below was speculative and is retained only
> as historical context. Its memory breakdown, recommendations, root-cause
> claims, and claim that reducing the daemon heap fixes the tests are not
> supported by measurements. `-Xmx` is a limit, not a reservation. The
> `jhat` tool mentioned below is not available in modern JDKs.

## Follow-up measurement

On an approximately 7.8 GiB Linux machine without swap, the original
`DynamatrixPipelineTest` started 72 Jenkins remoting JVMs. At one snapshot
their summed RSS was about 4.9 GiB (RSS includes shared pages); the Gradle
daemon and test worker were about 380 MiB and 572 MiB RSS, respectively.
The test worker had a 2 GiB maximum heap. A live test-worker heap dump taken
during agent startup was 117 MiB; its contents were dominated by ordinary
strings, reflection and collection objects. It is a single early snapshot,
not proof that no leak or runaway allocation can occur later. The worker
subsequently exited with status 143 (SIGTERM); the sender was not identified.
The Windows native allocation failure cannot be conclusively attributed to
the same cause from the available log alone.

The test pipelines only query node labels with `offline: true`. Their mock
nodes now use inbound-only launchers instead of starting remoting JVMs.
This revealed a separate CPS closure sorting error in the test pipelines.

## Problem Summary
The gradle integration tests (`integrationTestJunit`) were failing with:
1. **Linux (Codespaces)**: Gradle daemon crash with "Daemon has exited normally or was terminated"
2. **Windows**: Explicit OOM: "There is insufficient memory for the Java Runtime Environment to continue"

## Root Cause Analysis

### Original Configuration
- **System RAM**: 7.8GB (Linux) / 16GB+ (Windows)
- **Gradle Daemon Heap**: `-Xmx8192m` (8GB)
- **Problem**: On systems with ~8GB total, allocating 8GB to daemon leaves almost nothing for:
  - Test processes (fork new JVMs)
  - OS kernel buffers and caches
  - Other applications

### Why Tests Need Extra Memory
The Jenkins test harness (`JenkinsRule`) in `integrationTestJunit` creates a full in-memory Jenkins instance:
- Loads Jenkins core and plugins
- Simulates pipeline execution with CPS (Continuation Passing Style)
- Creates multiple build agents in memory to test matrix builds
- Test in attachment shows 72 nodes for shellcheck scenario

When Gradle daemon is already using 8GB, the forked test JVM tries to allocate additional memory and fails.

## Solution Implemented

### Changed in `gradle.properties`
```properties
# BEFORE:
org.gradle.jvmargs=-XX:+UseG1GC -Xmx8192m -Xms256m ...

# AFTER:
org.gradle.jvmargs=-XX:+UseG1GC -Xmx4096m -Xms256m ...
```

**New Memory Allocation**:
- Gradle daemon: 4GB max heap (down from 8GB)
- Available for test JVMs: ~3-4GB
- Available for OS: ~1GB

This is more conservative and should work on systems with 8GB+ RAM.

### For Systems with More RAM
On systems with 16GB+ RAM, you can increase back to 6-8GB if tests still fail:
```properties
# For 16GB+ systems:
org.gradle.jvmargs=-XX:+UseG1GC -Xmx6144m -Xms256m ...
```

## Memory Consumption Patterns in This Build

### What Consumes Memory

1. **Gradle Daemon** (~4GB now, was 8GB)
   - Compilation and plugin processing
   - Dependency resolution
   - Build configuration caching

2. **Test JVM (integrationTestJunit)** (~2-4GB needed)
   - Jenkins core in memory
   - ~72 build nodes for matrix tests
   - Pipeline CPS execution state
   - Workspace files for fake builds

3. **JenkinsRule Test Harness** (heavy consumer)
   - Creates `JenkinsRule.getInstance()` for each test class
   - Loads plugins via `UnitTestSupportingPluginManager`
   - Builds in-memory temporary directories
   - See: `prepareJenkinsTestDeps` task in build.gradle.kts:249-280

### Why Tests Take ~3 Minutes

The test `shellcheck with Closure config(JenkinsRule)` processes:
- 72 total nodes
- Generates 24 build combinations
- Matrix axis combinations for:
  - Multiple architectures (x86_64, aarch64, ppc64le, s390x, armv7l, etc.)
  - Multiple OS distributions (debian, ubuntu, fedora, freebsd, macos, etc.)
  - Bits (32/64)
- Creates label expressions for node matching

This generates a large in-memory data structure for each test.

## How to Generate Heap Dump for Further Analysis

If tests still fail after this fix, capture a heap dump:

```bash
# Run test with heap dump on OOM
export _JAVA_OPTIONS="-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=./heap-dump"
./gradlew :integrationTestJunit --tests "org.nut.dynamatrix.DynamatrixPipelineTest" --no-daemon

# Analyze with Eclipse MAT or jhat:
jhat heap-dump/java_pid*.hprof
```

Or use JVM flags in build:
```gradle
tasks.named<Test>("integrationTestJunit") {
    jvmArgs = listOf(
        "-XX:+HeapDumpOnOutOfMemoryError",
        "-XX:HeapDumpPath=./heap-dumps"
    )
}
```

## Verification

To verify the fix works:
1. Clear gradle daemon: `./gradlew --stop`
2. Run tests: `./gradlew :integrationTestJunit --no-daemon`
3. Monitor memory: `watch -n 1 free -h`

## References
- [Gradle Daemon Memory Settings](https://docs.gradle.org/current/userguide/gradle_daemon.html#sec:tuning_daemon)
- [Build Cache Documentation](https://docs.gradle.org/current/userguide/build_cache.html)
- [Jenkins Test Harness Memory](https://wiki.jenkins.io/display/JENKINS/Unit+Test)
