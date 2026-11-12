# Heap Dump Analysis Instructions

> Historical AI-generated notes: the estimates and proposed fixes below were
> not verified. In particular, modern JDKs do not include `jhat`. See
> [the follow-up investigation](GRADLE_MEMORY_ANALYSIS.md#follow-up-measurement).

If tests still fail with the new memory settings, use these instructions to capture and analyze a heap dump.

## Quick Heap Dump Capture

### Method 1: Via Environment Variable (Simplest)

```bash
cd /workspaces/jenkins-dynamatrix
./gradlew --stop
mkdir -p heap-dumps

export _JAVA_OPTIONS="-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=$(pwd)/heap-dumps"
timeout 600 ./gradlew :integrationTestJunit --tests "org.nut.dynamatrix.DynamatrixPipelineTest" --no-daemon 2>&1 | tee test-run.log

# Check if heap dump was created
ls -lh heap-dumps/
```

### Method 2: Via Build Configuration

Edit `build.gradle.kts` and add to `tasks.named<Test>("integrationTestJunit")`:

```kotlin
tasks.named<Test>("integrationTestJunit") {
    jvmArgs = listOf(
        "-XX:+HeapDumpOnOutOfMemoryError",
        "-XX:HeapDumpPath=${layout.buildDirectory.dir("heap-dumps")}"
    )
}
```

Then run:
```bash
./gradlew :integrationTestJunit --tests "org.nut.dynamatrix.DynamatrixPipelineTest" --no-daemon
```

## Analyzing the Heap Dump

### With Eclipse Memory Analyzer (MAT)
1. Download from: https://www.eclipse.org/mat/
2. Open the `.hprof` file
3. Look for:
   - "Leak Suspects" report
   - Large objects list
   - ArrayLists holding many items
   - String buffers

### With jhat (Java Heap Analysis Tool - included with JDK)

```bash
# Start interactive analyzer
jhat heap-dumps/java_pid*.hprof

# Opens on http://localhost:7000
# Browse to "Show heap histogram" to see object counts
```

### With JProfiler
```bash
jprofiler heap-dumps/java_pid*.hprof
```

## What to Look For

### Potential Issues:

1. **Runaway Lists/Collections**
   - ArrayList with millions of items
   - Indicates: Matrix axis combinations not being garbage collected
   - Solution: Check if test properly cleans up JenkinsRule

2. **Cached Build Data**
   - Large HashMap/TreeMap entries
   - Indicates: Build results cached in memory
   - Solution: Clear cache between tests or reduce cache size

3. **String Duplication**
   - Millions of identical strings
   - Indicates: Node labels or architecture strings not interned
   - Solution: Use String.intern() or consider interning pool

4. **Plugin ClassLoaders**
   - Multiple instances of same plugin classes
   - Indicates: Plugin isolation issue
   - Solution: Check UnitTestSupportingPluginManager configuration

### Key Objects to Investigate:

- `org.nut.dynamatrix.NodeData` - test node data
- `org.nut.dynamatrix.NodeCaps` - node capabilities
- `com.cloudbees.groovy.cps.Continuable` - pipeline state
- `java.lang.String` - check for duplication
- `java.util.ArrayList` - check for runaway sizes

## Understanding the Test Memory Profile

### DynamatrixPipelineTest Test Flow

```
1. JUnit launches test method
   ↓
2. JenkinsRule creates temporary Jenkins home directory
   ↓
3. UnitTestSupportingPluginManager loads plugins
   ├── Loads all runtimeOnly() dependencies from integrationTestJunit
   ├── Creates classloaders for each plugin
   └── Initializes extension points
   ↓
4. Test loads TestFarmNodeData.groovy (from test/mocklib)
   ├── Creates 72 NodeData objects with:
   │   ├── Architecture info (x86_64, aarch64, ppc64le, etc.)
   │   ├── OS distribution info (debian, ubuntu, fedora, etc.)
   │   ├── Bit width (32/64)
   │   └── String label expressions
   └── Total node data: ~72 * ~200 bytes = ~14KB per full structure
   ↓
5. Pipeline script executes via CPS
   ├── Calls generateBuildConfigSet()
   ├── Matrix axis processing:
   │   ├── For each axis combination (~24 total)
   │   ├── Generates label expression
   │   ├── Calls nodesByLabel() JUnit mock
   │   └── Adds result to build config array
   └── Creates 24 build scenario objects
   ↓
6. Pipeline completes
   ↓
7. JUnit assertion checks pipeline status
   ├── If failed, saves full pipeline log
   └── Destroys JenkinsRule (cleanup)
   ↓
8. Test method finishes
```

### Memory Spike Points

1. **Node Data Creation** (~5-10 MB)
   - 72 NodeData objects + label strings
   - During TestFarmNodeData class initialization

2. **Matrix Combination Processing** (~20-50 MB)
   - Each axis combination creates intermediate strings
   - nodesByLabel() results cached in pipeline
   - Pipeline execution state grows with each step

3. **Build Configuration Set** (~10-20 MB)
   - 24 build scenario objects
   - Each contains full axis values and environment

4. **Pipeline Execution State** (~100-200 MB)
   - CPS serialization of entire pipeline state
   - All variable bindings captured
   - Full script text and closure state

5. **Jenkins Plugin State** (~500-800 MB)
   - Workflow plugins maintain large state trees
   - Security realm and authorization caches
   - Build history and log storage

## Common Fixes

### If Gradle Daemon Uses Too Much Memory

```properties
# In gradle.properties
# Reduce max heap for daemon
org.gradle.jvmargs=-XX:+UseG1GC -Xmx3072m -Xms256m ...
```

### If Test JVM Uses Too Much Memory

```gradle
tasks.named<Test>("integrationTestJunit") {
    // Limit test JVM heap
    maxHeapSize = "3g"
}
```

### If NodeData Causes Runaway Memory

In `test/mocklib/src/org/nut/farm/TestFarmNodeData.groovy`:
- Consider lazy-loading node data instead of creating all at once
- Use weak references for caches
- Implement explicit cleanup between tests

### If CPS Continuation State Grows Large

Consider:
- Breaking large matrix into smaller batches
- Using Jenkins node batching features
- Implementing periodic state snapshots and cleanup

## Debugging Checklist

- [ ] System has sufficient RAM (8GB minimum, 16GB recommended)
- [ ] Gradle daemon stopped before run
- [ ] Heap dump file generated (check directory)
- [ ] Heap dump analyzed for large objects
- [ ] Memory settings applied to gradle.properties
- [ ] Test output log saved for error analysis
- [ ] Verified other processes not consuming RAM
- [ ] Checked build cache size: `du -sh .gradle/build-cache`
- [ ] Cleared build cache if > 2GB: `rm -rf .gradle/build-cache`

## Additional Resources

- [Java Heap Dump Analysis](https://docs.oracle.com/javase/8/docs/technotes/tools/unix/jhat.html)
- [Gradle Memory Tuning](https://docs.gradle.org/current/userguide/gradle_daemon.html#sec:tuning_daemon)
- [Jenkins Test Harness](https://wiki.jenkins.io/display/JENKINS/Unit+Test)
- [CPS for Jenkins Pipeline](https://github.com/cloudbees/groovy-cps)
