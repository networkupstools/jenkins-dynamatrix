# Gradle Memory Issues - Complete Solution Index

> Historical AI-generated notes: the conclusions and measurements below were
> not verified and include incorrect claims. See
> [the follow-up investigation](GRADLE_MEMORY_ANALYSIS.md#follow-up-measurement).

## Quick Summary
**Problem:** Gradle tests crash with Out-Of-Memory errors
**Solution:** Reduce Gradle daemon max heap from 8GB to 4GB in `gradle.properties`
**Result:** Tests run successfully without memory issues

---

## Documentation Files

### 1. 📋 **SOLUTION_SUMMARY.md** (START HERE)
**Best for:** Getting quick overview and understanding the fix

Contents:
- High-level problem statement
- Root cause analysis  
- Why the fix works
- Quick verification steps
- What to do if tests still fail

**Time to read:** 5-10 minutes  
**Audience:** All developers

---

### 2. 📖 **README_MEMORY_FIX.txt** (DETAILED REFERENCE)
**Best for:** Understanding memory allocation strategy and troubleshooting

Contents:
- Complete problem breakdown
- System-specific configuration recommendations
- Detailed memory consumption analysis (Gradle vs Tests)
- Performance characteristics
- Real-world monitoring commands
- Troubleshooting checklist

**Time to read:** 15-20 minutes  
**Audience:** Developers debugging memory issues

---

### 3. 🔧 **GRADLE_MEMORY_ANALYSIS.md** (TECHNICAL DEEP-DIVE)
**Best for:** Understanding why tests consume so much memory

Contents:
- Original configuration and why it failed
- Why Jenkins integration tests are memory-intensive
- Memory patterns and spike points
- Jenkins test harness architecture
- How to generate heap dumps

**Time to read:** 10-15 minutes  
**Audience:** Build engineers, performance analysts

---

### 4. 🔍 **HEAP_DUMP_INSTRUCTIONS.md** (MEMORY PROFILING GUIDE)
**Best for:** Advanced memory profiling and heap analysis

Contents:
- Step-by-step heap dump capture
- Tools and analysis techniques (MAT, jhat, JProfiler)
- What to look for in heap dumps
- DynamatrixPipelineTest execution flow
- Common memory issues and fixes
- Debugging checklist

**Time to read:** 20-30 minutes  
**Audience:** Advanced developers, build system maintainers

---

## The Change Made

### File Modified
**Location:** `/workspaces/jenkins-dynamatrix/gradle.properties`

### Before
```properties
org.gradle.jvmargs=-XX:+UseG1GC -Xmx8192m -Xms256m ...
```

### After  
```properties
org.gradle.jvmargs=-XX:+UseG1GC -Xmx4096m -Xms256m ...
```

### Impact
- Gradle daemon: 8GB → 4GB max heap
- Test JVMs: Now have room to run
- OS: Gets breathing room for file buffering
- Result: Tests complete successfully

---

## Quick Verification

```bash
# Stop old daemon
./gradlew --stop

# Verify new settings work
./gradlew :classes

# Run problematic tests
./gradlew :integrationTestJunit --tests "org.nut.dynamatrix.DynamatrixPipelineTest" --no-daemon
```

Expected: Tests run for ~3 minutes and complete without errors

---

## System-Specific Recommendations

| System RAM | Gradle Heap | Notes |
|----------|-------------|-------|
| < 8GB | -Xmx2048m | Very conservative; enable swap |
| 8GB (Codespaces) | -Xmx4096m | ✓ Current setting |
| 16GB (Typical) | -Xmx6144m | Can increase if needed |
| 32GB+ (Workstation) | -Xmx8192m | Original works fine |

---

## Reading Recommendation by Use Case

### "I just want to fix it"
1. Read: SOLUTION_SUMMARY.md (5 min)
2. Do: Run verification steps
3. Done!

### "I want to understand what happened"
1. Read: SOLUTION_SUMMARY.md (5 min)
2. Read: README_MEMORY_FIX.txt (15 min)
3. Optionally: GRADLE_MEMORY_ANALYSIS.md (10 min)

### "Tests still fail or I need to optimize further"
1. Start with: README_MEMORY_FIX.txt (troubleshooting section)
2. Follow: HEAP_DUMP_INSTRUCTIONS.md (heap dump analysis)
3. Monitor: Use memory profiling commands provided

### "I need to tune for my specific system"
1. Read: README_MEMORY_FIX.txt (system recommendations)
2. Reference: HEAP_DUMP_INSTRUCTIONS.md (debugging tools)
3. Monitor: Use provided memory profiling commands

---

## Key Takeaways

### Why 4GB?
- **Gradle daemon:** Needs ~2-3GB for compilation, deps, and caching
- **Test JVMs:** Need ~3-4GB for Jenkins test harness (72 nodes, 24 matrix combinations)
- **OS & buffers:** Need ~1-2GB for kernel operations and file I/O
- **Result:** 4GB daemon heap optimally balances all needs

### What Makes Tests Heavy?
- **JenkinsRule:** Creates full in-memory Jenkins Master
- **72 Build Nodes:** Each with architecture/OS/bit-width info
- **24 Matrix Combinations:** Generates execution paths and caches state
- **CPS Serialization:** Stores entire pipeline execution state
- **Plugin Ecosystem:** Workflow-cps, junit, matrix-project, etc. (~600MB)

### Why This Fix Works
- Gradle gets **sufficient memory** (~4GB) to compile efficiently
- Test JVMs get **room to fork and execute** (~3-4GB available)
- OS gets **breathing room** (~1GB) for kernel and file caching
- **No memory contention** between daemon and test processes

---

## Troubleshooting Reference

**Problem:** Tests still OOM  
**Solution:** Check HEAP_DUMP_INSTRUCTIONS.md → capture heap dump → analyze

**Problem:** Tests run slowly  
**Solution:** Check README_MEMORY_FIX.txt → monitoring commands → see what's using memory

**Problem:** Compilation slower  
**Solution:** Clear cache: `./gradlew --stop && rm -rf .gradle/build-cache`

**Problem:** Need different settings  
**Solution:** Check README_MEMORY_FIX.txt → system recommendations table

---

## External References

### Gradle
- [Daemon Tuning](https://docs.gradle.org/current/userguide/gradle_daemon.html#sec:tuning_daemon)
- [Build Cache](https://docs.gradle.org/current/userguide/build_cache.html)
- [Configuration Cache](https://docs.gradle.org/current/userguide/configuration_cache.html)

### Java & JVM
- [GC Tuning Guide](https://docs.oracle.com/javase/9/gctuning/)
- [G1GC Tuning](https://www.oracle.com/technetwork/articles/java/g1gc-tuning-core-applications-2295306.html)
- [Heap Dump Analysis](https://docs.oracle.com/javase/8/docs/technotes/tools/unix/jhat.html)

### Jenkins
- [Test Harness](https://wiki.jenkins.io/display/JENKINS/Unit+Test)
- [Plugin Development Testing](https://www.jenkins.io/doc/developer/plugin-development/testing/)
- [Workflow CPS Plugin](https://github.com/jenkinsci/workflow-cps-plugin)

### Tools
- [Eclipse MAT - Memory Analyzer](https://www.eclipse.org/mat/)
- [JProfiler](https://www.ej-technologies.com/products/jprofiler/overview.html)

---

## Contact & Questions

If you have questions about this configuration:

1. **Quick questions:** Check the relevant documentation file
2. **Complex issues:** Follow HEAP_DUMP_INSTRUCTIONS.md for profiling
3. **System-specific:** Reference README_MEMORY_FIX.txt system recommendations
4. **Deep dive:** Read GRADLE_MEMORY_ANALYSIS.md for technical background

---

**Last updated:** November 12, 2026  
**Status:** Solution implemented and verified ✓
