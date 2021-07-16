import org.nut.dynamatrix.dynamatrixGlobalState;
import org.nut.dynamatrix.*;

void call(def issueAnalysisArr, String id, String name, String sJOB_NAME, String sBRANCH_NAME, String sTARGET_BRANCH) {
    if (issueAnalysisArr.size() > 0) {
        // Compare issues that are new/fixed compared to specified branch
        // ReferenceJob(Name) is handled according to current version (at this time) of
        //   https://github.com/jenkinsci/warnings-ng-plugin/blob/master/doc/Documentation.md#configure-the-selection-of-the-reference-build-baseline
        // Note: this solution below assumes Git SCM for the project
        def reference = sJOB_NAME.replace(sBRANCH_NAME, sTARGET_BRANCH)
        discoverGitReferenceBuild referenceJob: reference
        publishIssues (
            id: id,
            name: name,
            //referenceJobName: reference,
            //recordIssues-only//filters: [includePackage('io.jenkins.plugins.analysis.*')],
            issues: issueAnalysisArr
            )
    }
} // doSummarizeIssues(args)

void call(def issueAnalysisArr, String id, String name) {
    def sTARGET_BRANCH = infra.branchDefaultStable()
    try {
        // Can fail if not set by pipeline (not a PR build)
        if (TARGET_BRANCH != null && TARGET_BRANCH != "")
            sTARGET_BRANCH = "${TARGET_BRANCH}"
    } catch (Throwable t1) {
        try {
            if (env.TARGET_BRANCH != null && env.TARGET_BRANCH != "")
                sTARGET_BRANCH = "${env.TARGET_BRANCH}"
        } catch (Throwable t2) {}
    }

    doSummarizeIssues(issueAnalysisArr, id, name, "${JOB_NAME}", "${BRANCH_NAME}", sTARGET_BRANCH)
} // doSummarizeIssues(arr)

void call() {
    doSummarizeIssues(dynamatrixGlobalState.issueAnalysis, 'analysis', 'All Issues')
} // doSummarizeIssues()
