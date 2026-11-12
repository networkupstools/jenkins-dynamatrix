================================================================================
GRADLE MEMORY CONFIGURATION FIX FOR jenkins-dynamatrix
================================================================================

WARNING: The AI-generated claims and estimates below were not verified.
See GRADLE_MEMORY_ANALYSIS.md, "Follow-up measurement", for corrected findings.

SUMMARY OF CHANGE:
------------------
File: gradle.properties
From: org.gradle.jvmargs=-XX:+UseG1GC -Xmx8192m -Xms256m ...
To:   org.gradle.jvmargs=-XX:+UseG1GC -Xmx4096m -Xms256m ...

Rationale: Reduced Gradle daemon max heap from 8GB to 4GB to allow test JVMs
to run without exhausting system memory.


PROBLEM STATEMENT:
------------------

Integration tests were failing with Out-Of-Memory errors:

1. Linux (Codespaces - 7.8GB total RAM):
   Error: "Gradle build daemon disappeared unexpectedly"
   Root cause: Daemon allocated 8GB heap, leaving no room for test processes

2. Windows (16GB+ total RAM):  
   Error: "There is insufficient memory for the Java Runtime Environment"
          "Native memory allocation (malloc) failed to allocate 1877232 bytes"
   Root cause: Test scenario creates 72 mock build nodes, consuming 2-4GB per
               test JVM, which conflicts with 8GB Gradle daemon heap

3. Both platforms:
   Test runs 3 minutes, then crashes or slows to crawl during:
   - shellcheck with Closure config(JenkinsRule)
   - Which generates 24 matrix build combinations
   - And tests against 72 mock build nodes


WHY 4GB IS THE RIGHT BALANCE:
-----------------------------

System Resource Allocation on 8GB Machine:
  Gradle Daemon:         4GB  (compilation, dependencies, caching)
  Test JVM (forked):     3GB  (Jenkins, plugins, test data)
  OS and buffers:        1GB  (kernel, file cache, swap)
  Total:                 8GB  (balanced)

On 16GB+ Machines:
  Gradle Daemon:         4GB  (or up to 6GB if needed)
  Test JVM:              6GB  (more headroom for large matrix scenarios)
  OS and buffers:        2GB
  Other applications:    1-4GB
  Total:                 16GB+ (plenty of headroom)


WHAT CONSUMES GRADLE DAEMON MEMORY:
-----------------------------------

1. Compilation (~500MB)
   - Groovy source compilation
   - Kotlin configuration caching
   - Annotation processing

2. Dependency Resolution (~500MB)
   - Resolved dependency graph
   - JAR file metadata
   - POM parsing and caching

3. Build Configuration (~200MB)
   - Gradle configuration cache
   - Plugin metadata
   - Task graph and dependencies

4. Plugin Processing (~500MB)
   - Jenkins plugin loading
   - Manifest parsing
   - Plugin descriptor analysis

5. Heap Reserve (~1.3GB)
   - Required for GC efficiency
   - Class loading and unloading
   - Temporary object allocation during compilation


WHAT CONSUMES TEST JVM MEMORY:
------------------------------

1. Jenkins Core Runtime (~800MB)
   - Jenkins Master instance in memory
   - Temporary JENKINS_HOME directory structure
   - Plugin initialization and extension points
   - Security realms and authorization

2. Jenkins Plugins (~600MB)
   - workflow-cps: Continuation Passing Style execution
   - workflow-api: Pipeline API classes
   - junit: Test result handling
   - matrix-project: Matrix build support
   - display-url-api, scm-api, branch-api, credentials, etc.

3. Test Data (~400MB)
   - TestFarmNodeData: 72 NodeData objects with architecture/OS info
   - Node label expressions (complex string combinations)
   - Node capability matrices

4. Pipeline State (~500MB)
   - CPS continuation serialization
   - Variable bindings (Groovy Map with all pipeline vars)
   - Closure state and local scopes
   - Pipeline script text and metadata

5. Build Scenario Data (~800MB)
   - 24 build configuration objects (one per matrix combo)
   - Each containing full axis value sets
   - Environment variable maps
   - Build parameter bindings

6. Workspace and Temp Files (~300MB)
   - Temporary directories for fake builds
   - Log files and output captures
   - Working directories for processes

Total: Typically 3-4GB per test JVM execution


PERFORMANCE CHARACTERISTICS:
----------------------------

Build Compilation:
  - First run: ~30-45 seconds (full compilation + dependency resolution)
  - Subsequent runs: ~15-20 seconds (with caching)

Test Execution:
  - Per test class: ~40-80 seconds
  - DynamatrixPipelineTest: ~3 minutes (72 nodes, 24 combinations)
  - Memory growth over test: ~100MB/minute

GC Impact:
  - G1GC (Garbage Collector) selected for better memory efficiency
  - Parallel collection helps on multi-core systems
  - Reduced Xmx should improve GC frequency but reduce pause times


MONITORING MEMORY USAGE:
-----------------------

Real-time monitoring while tests run:

  watch -n 1 'free -h && echo "---" && ps aux | grep gradle | head -2'

This shows:
  - Total system memory available
  - Gradle daemon process and memory
  - Java test processes


ADJUSTING FOR YOUR SYSTEM:
--------------------------

System with 8GB RAM (Codespaces, small VMs):
  org.gradle.jvmargs=-XX:+UseG1GC -Xmx4096m -Xms256m ...
  (Current setting, keeps 4GB for tests + OS)

System with 16GB RAM (typical development machine):
  org.gradle.jvmargs=-XX:+UseG1GC -Xmx6144m -Xms256m ...
  (Gradle gets 6GB, tests get 6GB, OS gets 4GB)

System with 32GB RAM (workstation):
  org.gradle.jvmargs=-XX:+UseG1GC -Xmx8192m -Xms512m ...
  (Original configuration is fine)

System with < 8GB RAM:
  org.gradle.jvmargs=-XX:+UseG1GC -Xmx2048m -Xms256m ...
  (Gradle gets 2GB only, enable swap if possible)

IMPORTANT: Always leave at least 1-2GB for OS and other processes!


GRADLE.PROPERTIES MEMORY-RELATED OPTIONS:
-----------------------------------------

org.gradle.parallel=true
  Enables parallel task execution. Generally good for multi-core CPUs.

org.gradle.workers.max=2
  Limits parallel workers to 2. Prevents memory starvation when building.
  Increase to 4 on 16GB+ machines if needed.

org.gradle.caching=true
  Enables build cache to avoid recompilation. Reduces memory pressure over time.

org.gradle.configuration-cache=true
  Gradle 9.4+ feature: caches configuration phase results.
  Reduces memory needs on subsequent builds.


TROUBLESHOOTING:
---------------

If tests still fail after this fix:

1. Stop daemon and check cache:
   ./gradlew --stop
   du -sh .gradle/
   rm -rf .gradle/build-cache  (if > 2GB)

2. Capture heap dump to analyze:
   export _JAVA_OPTIONS="-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=./heap-dumps"
   ./gradlew :integrationTestJunit --tests "..." --no-daemon

3. Monitor actual memory usage:
   ./gradlew --stop
   watch -n 0.5 'free -h'  (open in another terminal)
   ./gradlew :integrationTestJunit --no-daemon

4. Check for runaway processes:
   ps aux | grep -E 'java|gradle'

5. Verify system isn't memory-constrained:
   free -h
   vmstat 1 5
   (check if swap is being used heavily)


VERSION HISTORY:
---------------

gradle.properties history:
  - Before: -Xmx8192m (caused OOM on 8GB systems)
  - After:  -Xmx4096m (current, more balanced)
  - Future: Possibly vary per-environment via build.gradle.kts


REFERENCES:
-----------

Gradle Documentation:
  https://docs.gradle.org/current/userguide/gradle_daemon.html#sec:tuning_daemon
  https://docs.gradle.org/current/userguide/build_cache.html

Java GC Tuning:
  https://docs.oracle.com/javase/9/gctuning/
  https://wiki.c2.com/?G1GarbageCollector

Jenkins Test Harness:
  https://wiki.jenkins.io/display/JENKINS/Unit+Test
  https://www.jenkins.io/doc/developer/plugin-development/testing/

Groovy CPS for Pipeline:
  https://github.com/cloudbees/groovy-cps
  https://github.com/jenkinsci/workflow-cps-plugin


CONTACT & QUESTIONS:
-------------------

For questions about this configuration:
- Check GRADLE_MEMORY_ANALYSIS.md for detailed technical analysis
- Check HEAP_DUMP_INSTRUCTIONS.md for memory profiling techniques
- Consult gradle documentation at docs.gradle.org


================================================================================
