// Steps should not be in a package, to avoid CleanGroovyClassLoader exceptions...
// package org.nut.dynamatrix;

import org.nut.dynamatrix.dynamatrixGlobalState;
import org.nut.dynamatrix.*;

void call(def issueAnalysisArr, String id, String name, String sJOB_NAME, String sBRANCH_NAME, String sCHANGE_TARGET) {
    if (issueAnalysisArr.size() > 0) {
        // Compare issues that are new/fixed compared to specified branch
        // ReferenceJob(Name) is handled according to current version (at this time) of
        //   https://github.com/jenkinsci/warnings-ng-plugin/blob/master/doc/Documentation.md#configure-the-selection-of-the-reference-build-baseline
        // Note: this solution below assumes Git SCM for the project
        String reference = sJOB_NAME.replace(sBRANCH_NAME, sCHANGE_TARGET)
        discoverGitReferenceBuild referenceJob: reference

        Map piMap = [
            id: id,
            name: name,
            //referenceJobName: reference,
            //recordIssues-only//filters: [includePackage('io.jenkins.plugins.analysis.*')],

            // Below we require that any warnings exposed by the analysis
            // would mark the build as unstable. This is anticipated in
            // "fightwarn" style branches dedicated to higher verbosity
            // and sensitivity of tools that deal with code inspection,
            // but allows for zero tolerance on typical branches and PRs.
            //   https://github.com/jenkinsci/warnings-ng-plugin/blob/master/doc/Documentation.md#quality-gate-configuration
            // The "TOTAL" count of any-severity warnings (1 or more) is
            // what matters for us here; other options are available - for
            // more details see `enum QualityGateType` in plugin sources:
            //   https://github.com/jenkinsci/warnings-ng-plugin/blob/master/plugin/src/main/java/io/jenkins/plugins/analysis/core/util/QualityGate.java
            qualityGates: [[threshold: 1, type: 'TOTAL', unstable: true]],

            issues: issueAnalysisArr
            ]

        if (id ==~ /aggregate/) {
            piMap.aggregatingResults = true
        }

        publishIssues (piMap)
    }
} // doSummarizeIssues(args)

void call(def issueAnalysisArr, String id, String name) {
    String sCHANGE_TARGET = infra.branchDefaultStable()
    try {
        // Can fail if not set by pipeline (not a PR build)
        if (CHANGE_TARGET != null && CHANGE_TARGET != "")
            sCHANGE_TARGET = "${CHANGE_TARGET}"
    } catch (Throwable ignored) {
        try {
            if (env.CHANGE_TARGET != null && env.CHANGE_TARGET != "")
                sCHANGE_TARGET = "${env.CHANGE_TARGET}"
        } catch (Throwable ignore) {}
    }

    doSummarizeIssues(issueAnalysisArr, id, name, "${JOB_NAME}", "${BRANCH_NAME}", sCHANGE_TARGET)
} // doSummarizeIssues(arr)

void call() {
    // Summary at end of pipeline
    doSummarizeIssues(dynamatrixGlobalState.issueAnalysis, 'analysis', 'All Issues')
    doSummarizeIssues(dynamatrixGlobalState.issueAnalysisAggregated, 'aggregated-analysis', 'All Issues - Aggregated')
    // This is not always collected and so is accounted separately
    if (Utils.isListNotEmpty(dynamatrixGlobalState?.issueAnalysisCppcheck))
        doSummarizeIssues(dynamatrixGlobalState.issueAnalysisCppcheck, 'CppCheck-analysis', 'CppCheck Issues')
    if (Utils.isListNotEmpty(dynamatrixGlobalState?.issueAnalysisAggregatedCppcheck))
        doSummarizeIssues(dynamatrixGlobalState.issueAnalysisAggregatedCppcheck, 'CppCheck-aggregated-analysis', 'CppCheck Issues - Aggregated')
} // doSummarizeIssues()
