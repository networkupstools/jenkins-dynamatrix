package org.nut.dynamatrix

import hudson.model.Label
import hudson.slaves.DumbSlave
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition
import org.jenkinsci.plugins.workflow.job.WorkflowJob
import org.jenkinsci.plugins.workflow.job.WorkflowRun
import org.junit.jupiter.api.Test
import org.jvnet.hudson.test.JenkinsRule
import org.jvnet.hudson.test.junit.jupiter.WithJenkins
import org.jvnet.hudson.test.recipes.WithTimeout

/**
 * Integration test that mimics a simplified variant of
 * the NUT project's Jenkinsfile-dynamatrix pipeline script.
 *
 * Uses sandbox=false so TestFarmNodeData (in test/mocklib/src/) is
 * accessible from CPS pipelines.  This is safe because the test runs
 * in a controlled JenkinsRule harness, not in production.
 *
 * Registers mock DumbSlave agents with labels from TestFarmNodeData
 * so that nodesByLabel() calls in generateBuildConfigSet() can find
 * matching nodes.
 *
 * Covers:
 * - C/C++ autotools build with "extreme" specifications for included/excluded
 *   matrix cells, including MAX resolution, allowed failures, and bit-width
 *   exclusion combos.
 * - C/C++ ci_build approach with extreme specs.
 * - Spellcheck and shellcheck dynamatrices (one with "legacy string"
 *   config, another with a Closure).
 * - Verifies build logs for reported success, lack of exceptions,
 *   and absence of "expected to call" lines.
 * - Generates alphanumerically sorted summaries for the dynamatrix.
 */
@WithJenkins
class DynamatrixPipelineTest {

    /**
     * Register mock DumbSlave agents for the given node names.
     * Each agent gets the labels from TestFarmNodeData.FARM_NODES_LABELS.
     */
    private void registerMockNodes(JenkinsRule rule, List<String> nodeNames) {
        nodeNames.each { String nodeName ->
            List<String> labels = TestFarmNodeData.FARM_NODES_LABELS[nodeName] ?: []
            // Filter out the node name itself from labels to avoid duplication
            String labelStr = labels.findAll { it != nodeName }.join(' ')
            DumbSlave slave = rule.createOnlineSlave(
                labelStr.isEmpty() ? null : Label.parse(labelStr)
            )
            slave.setName(nodeName)
        }
    }

    // ========================================================================
    // Test 1: C/C++ autotools build with extreme include/exclude specs
    // ========================================================================

    @Test
    @WithTimeout(600)
    void "autotools slow build with extreme specs"(JenkinsRule rule) {
        // Register mock nodes before creating the pipeline
        def nodeNames = TestFarmNodeData.getNodeNames().findAll { name ->
            def labels = TestFarmNodeData.FARM_NODES_LABELS[name] ?: []
            boolean hasGcc = labels.any { it.startsWith("GCCVER=") }
            boolean hasClang = labels.any { it.startsWith("CLANGVER=") }
            (hasGcc || hasClang) && name.contains("debian")
        }
        registerMockNodes(rule, nodeNames)

        final CpsFlowDefinition flow = new CpsFlowDefinition(
            '''
            @Library("jenkins-dynamatrix") _
            import org.nut.dynamatrix.*

            def nodeNames = TestFarmNodeData.getNodeNames().findAll { name ->
                def labels = TestFarmNodeData.FARM_NODES_LABELS[name] ?: []
                boolean hasGcc = labels.any { it.startsWith("GCCVER=") }
                boolean hasClang = labels.any { it.startsWith("CLANGVER=") }
                (hasGcc || hasClang) && name.contains("debian")
            }
            echo "Found " + nodeNames.size() + " Debian nodes with compilers"

            NodeCaps nc = TestFarmNodeData.getNodeCaps(nodeNames)
            Dynamatrix dynamatrix = new Dynamatrix(this, nc, "autotools-slowbuild-test")

            Map cfgMap = [
                "defaultDynamatrixConfig": "C+CXX",
                "commonLabelExpr":         "nut-builder",
                "dynamatrixAxesLabels": ["OS_DISTRO", '${COMPILER}VER', 'ARCH${ARCH_BITS}'],
                "dynamatrixAxesVirtualLabelsMap": [
                    'CSTDVERSION_${KEY}': [
                        ["c": "89", "cxx": "98"],
                        ["c": "99", "cxx": "11"],
                        ["c": "11", "cxx": "14"],
                        ["c": "17", "cxx": "17"],
                        ["c": "MAX", "cxx": "MAX"]
                    ],
                    "CSTDVARIANT": ["gnu"],
                    "BITS": [32, 64]
                ],
                "excludeCombos": [
                    [~/BITS=32/, ~/ARCH_BITS=64/],
                    [~/BITS=64/, ~/ARCH_BITS=32/]
                ],
                "allowedFailure": [
                    [~/CSTDVERSION_c=89/]
                ],
                "runAllowedFailure": true
            ]

            dynamatrix.prepareDynamatrix(cfgMap)
            Set<DynamatrixSingleBuildConfig> dsbcs = dynamatrix.generateBuildConfigSet()

            if (dsbcs == null || dsbcs.isEmpty()) {
                throw new RuntimeException("generateBuildConfigSet returned null or empty")
            }

            dsbcs.each { DynamatrixSingleBuildConfig dsbc ->
                Map<String, String> kv = dsbc.getKVMap(false)
                if (kv.get("CSTDVERSION_c") == "MAX") {
                    throw new RuntimeException("CSTDVERSION_c must not be literal MAX: " + dsbc)
                }
                if (kv.get("CSTDVERSION_cxx") == "MAX") {
                    throw new RuntimeException("CSTDVERSION_cxx must not be literal MAX: " + dsbc)
                }
            }

            String summary = "Discovered " + dsbcs.size() + " slow build (autotools) configurations"
            List<DynamatrixSingleBuildConfig> sorted = dsbcs.toList().sort { it.defaultStageName() }
            sorted.eachWithIndex { dsbc, idx ->
                summary += " CONFIG#" + (idx + 1) + " " + dsbc.defaultStageName()
            }

            if (summary.contains("expected to call")) {
                throw new RuntimeException("Summary contains expected to call CPS errors")
            }

            boolean hasOldStandard = dsbcs.any { dsbc ->
                dsbc.getKVMap(false).get("CSTDVERSION_c") == "89"
            }
            if (!hasOldStandard) {
                throw new RuntimeException("Should have C89 builds (allowed failure)")
            }

            boolean hasMaxResolved = dsbcs.any { dsbc ->
                Map<String, String> kv = dsbc.getKVMap(false)
                kv.get("CSTDVERSION_c") in ["11", "17", "2x"]
            }
            if (!hasMaxResolved) {
                throw new RuntimeException("Should have MAX-resolved builds")
            }

            echo summary
            echo "SUCCESS: autotools slow build test passed with " + dsbcs.size() + " configurations"
            '''.stripIndent(), false)

        final WorkflowJob workflowJob = rule.createProject(WorkflowJob, "pAutotools")
        workflowJob.definition = flow
        final WorkflowRun run = rule.buildAndAssertSuccess(workflowJob)

        rule.assertLogContains("SUCCESS: autotools slow build test passed", run)
        rule.assertLogContains("Discovered", run)
        rule.assertLogNotContains("expected to call", run)
        rule.assertLogNotContains("java.lang.Error", run)
        rule.assertLogNotContains("java.lang.Exception", run)
        rule.assertLogNotContains("Closure", run)
    }

    // ========================================================================
    // Test 2: C/C++ ci_build approach with extreme specs
    // ========================================================================

    @Test
    @WithTimeout(600)
    void "ci_build slow build with extreme specs"(JenkinsRule rule) {
        // Register mock nodes before creating the pipeline
        def nodeNames = TestFarmNodeData.getNodeNames().findAll { name ->
            def labels = TestFarmNodeData.FARM_NODES_LABELS[name] ?: []
            boolean hasClang = labels.any { it.startsWith("CLANGVER=") }
            hasClang && (name.contains("freebsd") || name.contains("nutci"))
        }
        registerMockNodes(rule, nodeNames)

        final CpsFlowDefinition flow = new CpsFlowDefinition(
            '''
            @Library("jenkins-dynamatrix") _
            import org.nut.dynamatrix.*

            def nodeNames = TestFarmNodeData.getNodeNames().findAll { name ->
                def labels = TestFarmNodeData.FARM_NODES_LABELS[name] ?: []
                boolean hasClang = labels.any { it.startsWith("CLANGVER=") }
                hasClang && (name.contains("freebsd") || name.contains("nutci"))
            }
            echo "Found " + nodeNames.size() + " nodes with CLANG"

            NodeCaps nc = TestFarmNodeData.getNodeCaps(nodeNames)
            Dynamatrix dynamatrix = new Dynamatrix(this, nc, "ci-build-slowbuild-test")

            Map cfgMap = [
                "defaultDynamatrixConfig": "C+CXX",
                "commonLabelExpr":         "nut-builder",
                "dynamatrixAxesLabels":    ["OS_DISTRO", '${COMPILER}VER', 'ARCH${ARCH_BITS}'],
                "dynamatrixAxesVirtualLabelsMap": [
                    'CSTDVERSION_${KEY}': [
                        ["c": "11", "cxx": "14"],
                        ["c": "17", "cxx": "17"],
                        ["c": "MAX", "cxx": "MAX"]
                    ],
                    "CSTDVARIANT": ["gnu", "iso"],
                    "BUILD_TYPE": ["default-all-errors", "default-all-warnings"]
                ],
                "excludeCombos": [
                    [~/CSTDVARIANT=iso/, ~/CSTDVERSION_c=MAX/]
                ]
            ]

            dynamatrix.prepareDynamatrix(cfgMap)
            Set<DynamatrixSingleBuildConfig> dsbcs = dynamatrix.generateBuildConfigSet()

            if (dsbcs == null || dsbcs.isEmpty()) {
                throw new RuntimeException("ci_build: generateBuildConfigSet returned null or empty")
            }

            dsbcs.each { DynamatrixSingleBuildConfig dsbc ->
                Map<String, String> kv = dsbc.getKVMap(false)
                if (kv.get("CSTDVERSION_c") == "MAX") {
                    throw new RuntimeException("CSTDVERSION_c must not be literal MAX: " + dsbc)
                }
            }

            boolean hasGnu = dsbcs.any { dsbc ->
                dsbc.getKVMap(false).get("CSTDVARIANT") == "gnu"
            }
            boolean hasIso = dsbcs.any { dsbc ->
                dsbc.getKVMap(false).get("CSTDVARIANT") == "iso"
            }
            if (!hasGnu) throw new RuntimeException("Should have gnu variant builds")
            if (!hasIso) throw new RuntimeException("Should have iso variant builds")

            String summary = "Discovered " + dsbcs.size() + " slow build (ci_build) configurations"
            List<DynamatrixSingleBuildConfig> sorted = dsbcs.toList().sort { it.defaultStageName() }
            sorted.eachWithIndex { dsbc, idx ->
                summary += " CONFIG#" + (idx + 1) + " " + dsbc.defaultStageName()
            }

            if (summary.contains("expected to call")) {
                throw new RuntimeException("Summary contains expected to call CPS errors")
            }

            echo summary
            echo "SUCCESS: ci_build slow build test passed with " + dsbcs.size() + " configurations"
            '''.stripIndent(), false)

        final WorkflowJob workflowJob = rule.createProject(WorkflowJob, "pCiBuild")
        workflowJob.definition = flow
        final WorkflowRun run = rule.buildAndAssertSuccess(workflowJob)

        rule.assertLogContains("SUCCESS: ci_build slow build test passed", run)
        rule.assertLogContains("Discovered", run)
        rule.assertLogNotContains("expected to call", run)
        rule.assertLogNotContains("Closure", run)
    }

    // ========================================================================
    // Test 3: Spellcheck dynamatrix with legacy string config
    // ========================================================================

    @Test
    @WithTimeout(600)
    void "spellcheck with legacy string config"(JenkinsRule rule) {
        // Register mock nodes before creating the pipeline
        def nodeNames = TestFarmNodeData.getNodeNames().findAll { name ->
            def labels = TestFarmNodeData.FARM_NODES_LABELS[name] ?: []
            labels.any { it.contains("OS_DISTRO") }
        }
        registerMockNodes(rule, nodeNames)

        final CpsFlowDefinition flow = new CpsFlowDefinition(
            '''
            @Library("jenkins-dynamatrix") _
            import org.nut.dynamatrix.*

            def nodeNames = TestFarmNodeData.getNodeNames().findAll { name ->
                def labels = TestFarmNodeData.FARM_NODES_LABELS[name] ?: []
                labels.any { it.contains("OS_DISTRO") }
            }
            echo "Found " + nodeNames.size() + " nodes for spellcheck"

            NodeCaps nc = TestFarmNodeData.getNodeCaps(nodeNames)
            Dynamatrix dynamatrix = new Dynamatrix(this, nc, "spellcheck-legacy-test")

            Map cfgMap = [
                "commonLabelExpr":      "nut-builder",
                "dynamatrixAxesLabels": ["OS_DISTRO", 'ARCH${ARCH_BITS}']
            ]

            dynamatrix.prepareDynamatrix(cfgMap)
            Set<DynamatrixSingleBuildConfig> dsbcs = dynamatrix.generateBuildConfigSet()

            String summary = "Discovered " + (dsbcs ? dsbcs.size() : 0) + " spellcheck (legacy string) configurations"
            if (dsbcs) {
                List<DynamatrixSingleBuildConfig> sorted = dsbcs.toList().sort { it.defaultStageName() }
                sorted.eachWithIndex { dsbc, idx ->
                    summary += " CONFIG#" + (idx + 1) + " " + dsbc.defaultStageName()
                }
            }

            if (summary.contains("expected to call")) {
                throw new RuntimeException("Summary contains expected to call CPS errors")
            }

            echo summary
            echo "SUCCESS: spellcheck legacy string test passed"
            '''.stripIndent(), false)

        final WorkflowJob workflowJob = rule.createProject(WorkflowJob, "pSpellLegacy")
        workflowJob.definition = flow
        final WorkflowRun run = rule.buildAndAssertSuccess(workflowJob)

        rule.assertLogContains("SUCCESS: spellcheck legacy string test passed", run)
        rule.assertLogNotContains("expected to call", run)
        rule.assertLogNotContains("Closure", run)
    }

    // ========================================================================
    // Test 4: Spellcheck dynamatrix with Closure-based config
    // ========================================================================

    @Test
    @WithTimeout(600)
    void "spellcheck with Closure config"(JenkinsRule rule) {
        // Register mock nodes before creating the pipeline
        def nodeNames = TestFarmNodeData.getNodeNames().findAll { name ->
            def labels = TestFarmNodeData.FARM_NODES_LABELS[name] ?: []
            labels.any { it.contains("OS_DISTRO") }
        }
        registerMockNodes(rule, nodeNames)

        final CpsFlowDefinition flow = new CpsFlowDefinition(
            '''
            @Library("jenkins-dynamatrix") _
            import org.nut.dynamatrix.*

            def nodeNames = TestFarmNodeData.getNodeNames().findAll { name ->
                def labels = TestFarmNodeData.FARM_NODES_LABELS[name] ?: []
                labels.any { it.contains("OS_DISTRO") }
            }
            echo "Found " + nodeNames.size() + " nodes for spellcheck"

            NodeCaps nc = TestFarmNodeData.getNodeCaps(nodeNames)
            Dynamatrix dynamatrix = new Dynamatrix(this, nc, "spellcheck-closure-test")

            Closure stageNameFunc = { DynamatrixSingleBuildConfig dsbc ->
                def labelMap = dsbc.getKVMap(false)
                String sn = ""
                if (labelMap.containsKey("OS_DISTRO"))
                    sn += labelMap.OS_DISTRO + "-"
                if (labelMap.containsKey("ARCH64"))
                    sn += labelMap.ARCH64 + "-"
                else if (labelMap.containsKey("ARCH32"))
                    sn += labelMap.ARCH32 + "-"
                return "MATRIX_TAG=" + sn + "spellcheck"
            }

            Map cfgMap = [
                "commonLabelExpr":      "nut-builder",
                "dynamatrixAxesLabels": ["OS_DISTRO", 'ARCH${ARCH_BITS}'],
                "stageNameFunc":        stageNameFunc
            ]

            dynamatrix.prepareDynamatrix(cfgMap)
            Set<DynamatrixSingleBuildConfig> dsbcs = dynamatrix.generateBuildConfigSet()

            String summary = "Discovered " + (dsbcs ? dsbcs.size() : 0) + " spellcheck (closure) configurations"
            if (dsbcs) {
                List<DynamatrixSingleBuildConfig> sorted = dsbcs.toList().sort { it.defaultStageName() }
                sorted.eachWithIndex { dsbc, idx ->
                    summary += " CONFIG#" + (idx + 1) + " " + dsbc.defaultStageName()
                }
            }

            if (summary.contains("expected to call")) {
                throw new RuntimeException("Summary contains expected to call CPS errors")
            }

            echo summary
            echo "SUCCESS: spellcheck closure test passed"
            '''.stripIndent(), false)

        final WorkflowJob workflowJob = rule.createProject(WorkflowJob, "pSpellClosure")
        workflowJob.definition = flow
        final WorkflowRun run = rule.buildAndAssertSuccess(workflowJob)

        rule.assertLogContains("SUCCESS: spellcheck closure test passed", run)
        rule.assertLogNotContains("expected to call", run)
        rule.assertLogNotContains("Closure", run)
    }

    // ========================================================================
    // Test 5: Shellcheck dynamatrix with legacy string config
    // ========================================================================

    @Test
    @WithTimeout(600)
    void "shellcheck with legacy string config"(JenkinsRule rule) {
        // Register mock nodes before creating the pipeline
        def nodeNames = TestFarmNodeData.getNodeNames().findAll { name ->
            def labels = TestFarmNodeData.FARM_NODES_LABELS[name] ?: []
            labels.any { it.contains("OS_DISTRO") }
        }
        registerMockNodes(rule, nodeNames)

        final CpsFlowDefinition flow = new CpsFlowDefinition(
            '''
            @Library("jenkins-dynamatrix") _
            import org.nut.dynamatrix.*

            def nodeNames = TestFarmNodeData.getNodeNames().findAll { name ->
                def labels = TestFarmNodeData.FARM_NODES_LABELS[name] ?: []
                labels.any { it.contains("OS_DISTRO") }
            }
            echo "Found " + nodeNames.size() + " nodes for shellcheck"

            NodeCaps nc = TestFarmNodeData.getNodeCaps(nodeNames)
            Dynamatrix dynamatrix = new Dynamatrix(this, nc, "shellcheck-legacy-test")

            Map cfgMap = [
                "commonLabelExpr":      "nut-builder",
                "dynamatrixAxesLabels": ["OS_DISTRO", 'ARCH${ARCH_BITS}']
            ]

            dynamatrix.prepareDynamatrix(cfgMap)
            Set<DynamatrixSingleBuildConfig> dsbcs = dynamatrix.generateBuildConfigSet()

            String summary = "Discovered " + (dsbcs ? dsbcs.size() : 0) + " shellcheck (legacy string) configurations"
            if (dsbcs) {
                List<DynamatrixSingleBuildConfig> sorted = dsbcs.toList().sort { it.defaultStageName() }
                sorted.eachWithIndex { dsbc, idx ->
                    summary += " CONFIG#" + (idx + 1) + " " + dsbc.defaultStageName()
                }
            }

            if (summary.contains("expected to call")) {
                throw new RuntimeException("Summary contains expected to call CPS errors")
            }

            echo summary
            echo "SUCCESS: shellcheck legacy string test passed"
            '''.stripIndent(), false)

        final WorkflowJob workflowJob = rule.createProject(WorkflowJob, "pShellLegacy")
        workflowJob.definition = flow
        final WorkflowRun run = rule.buildAndAssertSuccess(workflowJob)

        rule.assertLogContains("SUCCESS: shellcheck legacy string test passed", run)
        rule.assertLogNotContains("expected to call", run)
        rule.assertLogNotContains("Closure", run)
    }

    // ========================================================================
    // Test 6: Shellcheck dynamatrix with Closure-based config
    // ========================================================================

    @Test
    @WithTimeout(600)
    void "shellcheck with Closure config"(JenkinsRule rule) {
        // Register mock nodes before creating the pipeline
        def nodeNames = TestFarmNodeData.getNodeNames().findAll { name ->
            def labels = TestFarmNodeData.FARM_NODES_LABELS[name] ?: []
            labels.any { it.contains("OS_DISTRO") }
        }
        registerMockNodes(rule, nodeNames)

        final CpsFlowDefinition flow = new CpsFlowDefinition(
            '''
            @Library("jenkins-dynamatrix") _
            import org.nut.dynamatrix.*

            def nodeNames = TestFarmNodeData.getNodeNames().findAll { name ->
                def labels = TestFarmNodeData.FARM_NODES_LABELS[name] ?: []
                labels.any { it.contains("OS_DISTRO") }
            }
            echo "Found " + nodeNames.size() + " nodes for shellcheck"

            NodeCaps nc = TestFarmNodeData.getNodeCaps(nodeNames)
            Dynamatrix dynamatrix = new Dynamatrix(this, nc, "shellcheck-closure-test")

            Closure stageNameFunc = { DynamatrixSingleBuildConfig dsbc ->
                def labelMap = dsbc.getKVMap(false)
                String sn = ""
                if (labelMap.containsKey("OS_FAMILY"))
                    sn += labelMap.OS_FAMILY + "-"
                if (labelMap.containsKey("OS_DISTRO"))
                    sn += labelMap.OS_DISTRO + "-"
                if (labelMap.containsKey("ARCH64"))
                    sn += labelMap.ARCH64 + "-"
                else if (labelMap.containsKey("ARCH32"))
                    sn += labelMap.ARCH32 + "-"
                return "MATRIX_TAG=" + sn + "shellcheck"
            }

            Map cfgMap = [
                "commonLabelExpr":      "nut-builder",
                "dynamatrixAxesLabels": ["OS_DISTRO", 'ARCH${ARCH_BITS}'],
                "stageNameFunc":        stageNameFunc
            ]

            dynamatrix.prepareDynamatrix(cfgMap)
            Set<DynamatrixSingleBuildConfig> dsbcs = dynamatrix.generateBuildConfigSet()

            String summary = "Discovered " + (dsbcs ? dsbcs.size() : 0) + " shellcheck (closure) configurations"
            if (dsbcs) {
                List<DynamatrixSingleBuildConfig> sorted = dsbcs.toList().sort { it.defaultStageName() }
                sorted.eachWithIndex { dsbc, idx ->
                    summary += " CONFIG#" + (idx + 1) + " " + dsbc.defaultStageName()
                }
            }

            if (summary.contains("expected to call")) {
                throw new RuntimeException("Summary contains expected to call CPS errors")
            }

            echo summary
            echo "SUCCESS: shellcheck closure test passed"
            '''.stripIndent(), false)

        final WorkflowJob workflowJob = rule.createProject(WorkflowJob, "pShellClosure")
        workflowJob.definition = flow
        final WorkflowRun run = rule.buildAndAssertSuccess(workflowJob)

        rule.assertLogContains("SUCCESS: shellcheck closure test passed", run)
        rule.assertLogNotContains("expected to call", run)
        rule.assertLogNotContains("Closure", run)
    }

    // ========================================================================
    // Test 7: Quickcheck dynamatrix summary
    // ========================================================================

    @Test
    @WithTimeout(600)
    void "quickcheck summary generation"(JenkinsRule rule) {
        // Register mock nodes before creating the pipeline
        def nodeNames = TestFarmNodeData.getNodeNames().findAll { name ->
            def labels = TestFarmNodeData.FARM_NODES_LABELS[name] ?: []
            labels.any { it.contains("OS_DISTRO") }
        }
        registerMockNodes(rule, nodeNames)

        final CpsFlowDefinition flow = new CpsFlowDefinition(
            '''
            @Library("jenkins-dynamatrix") _
            import org.nut.dynamatrix.*

            def nodeNames = TestFarmNodeData.getNodeNames().findAll { name ->
                def labels = TestFarmNodeData.FARM_NODES_LABELS[name] ?: []
                labels.any { it.contains("OS_DISTRO") }
            }
            echo "Found " + nodeNames.size() + " nodes for quickcheck"

            NodeCaps nc = TestFarmNodeData.getNodeCaps(nodeNames)
            Dynamatrix dynamatrix = new Dynamatrix(this, nc, "quickcheck-test")

            Map cfgMap = [
                "commonLabelExpr":      "nut-builder",
                "dynamatrixAxesLabels": ["OS_DISTRO", 'ARCH${ARCH_BITS}']
            ]

            dynamatrix.prepareDynamatrix(cfgMap)
            Set<DynamatrixSingleBuildConfig> dsbcs = dynamatrix.generateBuildConfigSet()

            String summary = "Discovered " + (dsbcs ? dsbcs.size() : 0) + " quickcheck configurations"
            if (dsbcs) {
                List<DynamatrixSingleBuildConfig> sorted = dsbcs.toList().sort { it.defaultStageName() }
                sorted.eachWithIndex { dsbc, idx ->
                    summary += " CONFIG#" + (idx + 1) + " " + dsbc.defaultStageName()
                }
            }

            if (summary.contains("expected to call")) {
                throw new RuntimeException("Summary contains expected to call CPS errors")
            }

            echo summary
            echo "SUCCESS: quickcheck summary test passed"
            '''.stripIndent(), false)

        final WorkflowJob workflowJob = rule.createProject(WorkflowJob, "pQuickcheck")
        workflowJob.definition = flow
        final WorkflowRun run = rule.buildAndAssertSuccess(workflowJob)

        rule.assertLogContains("SUCCESS: quickcheck summary test passed", run)
        rule.assertLogContains("Discovered", run)
        rule.assertLogNotContains("expected to call", run)
        rule.assertLogNotContains("Closure", run)
    }

    // ========================================================================
    // Test 8: Verify alphanumerical sorting of summaries
    // ========================================================================

    @Test
    @WithTimeout(600)
    void "summary sorting verification"(JenkinsRule rule) {
        // Register mock nodes before creating the pipeline
        def nodeNames = TestFarmNodeData.getNodeNames().findAll { name ->
            def labels = TestFarmNodeData.FARM_NODES_LABELS[name] ?: []
            boolean hasGcc = labels.any { it.startsWith("GCCVER=") }
            hasGcc && name.contains("debian")
        }
        registerMockNodes(rule, nodeNames)

        final CpsFlowDefinition flow = new CpsFlowDefinition(
            '''
            @Library("jenkins-dynamatrix") _
            import org.nut.dynamatrix.*

            def nodeNames = TestFarmNodeData.getNodeNames().findAll { name ->
                def labels = TestFarmNodeData.FARM_NODES_LABELS[name] ?: []
                boolean hasGcc = labels.any { it.startsWith("GCCVER=") }
                hasGcc && name.contains("debian")
            }
            echo "Found " + nodeNames.size() + " Debian nodes with GCC"

            NodeCaps nc = TestFarmNodeData.getNodeCaps(nodeNames)
            Dynamatrix dynamatrix = new Dynamatrix(this, nc, "sorting-test")

            Map cfgMap = [
                "defaultDynamatrixConfig": "C+CXX",
                "commonLabelExpr":         "nut-builder",
                "dynamatrixAxesLabels":    ["OS_DISTRO", '${COMPILER}VER'],
                "dynamatrixAxesVirtualLabelsMap": [
                    'CSTDVERSION_${KEY}': [
                        ["c": "99", "cxx": "11"],
                        ["c": "MAX", "cxx": "MAX"]
                    ],
                    "CSTDVARIANT": ["gnu"]
                ]
            ]

            dynamatrix.prepareDynamatrix(cfgMap)
            Set<DynamatrixSingleBuildConfig> dsbcs = dynamatrix.generateBuildConfigSet()

            if (dsbcs == null || dsbcs.isEmpty()) {
                throw new RuntimeException("Should generate build configs")
            }

            String summary = "Discovered " + dsbcs.size() + " reference comparison configurations"
            List<DynamatrixSingleBuildConfig> sorted = dsbcs.toList().sort { it.defaultStageName() }
            List<String> stageNames = []
            sorted.eachWithIndex { dsbc, idx ->
                String sn = dsbc.defaultStageName()
                stageNames.add(sn)
                summary += " CONFIG#" + (idx + 1) + " " + sn
            }

            List<String> sortedNames = stageNames.sort()
            if (!stageNames.equals(sortedNames)) {
                throw new RuntimeException("Stage names should be alphanumerically sorted")
            }

            if (summary.contains("expected to call")) {
                throw new RuntimeException("Summary contains expected to call CPS errors")
            }

            echo summary
            echo "SUCCESS: sorting verification test passed with " + dsbcs.size() + " configurations"
            '''.stripIndent(), false)

        final WorkflowJob workflowJob = rule.createProject(WorkflowJob, "pSorting")
        workflowJob.definition = flow
        final WorkflowRun run = rule.buildAndAssertSuccess(workflowJob)

        rule.assertLogContains("SUCCESS: sorting verification test passed", run)
        rule.assertLogContains("Discovered", run)
        rule.assertLogContains("CONFIG #", run)
        rule.assertLogNotContains("expected to call", run)
        rule.assertLogNotContains("Closure", run)
    }
}
