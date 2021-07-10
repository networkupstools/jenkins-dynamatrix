import org.nut.dynamatrix.*;

void call(String sJOB_NAME, String sBRANCH_NAME, String sTARGET_BRANCH = "master") {
    if (dynamatrixGlobalState.issueAnalysis.size() > 0) {
        // Compare issues that are new/fixed compared to specified branch
        def reference = sJOB_NAME.replace(sBRANCH_NAME, sTARGET_BRANCH)
        publishIssues id: 'analysis', name: 'All Issues',
            referenceJobName: reference,
            issues: dynamatrixGlobalState.issueAnalysis,
            filters: [includePackage('io.jenkins.plugins.analysis.*')]
    }
} // doSummarizeIssues()

void call() {
    def sTARGET_BRANCH = "master"
    try {
        // Can fail if not set by pipeline (not a PR build)
        sTARGET_BRANCH = "${TARGET_BRANCH}"
    } catch (Throwable t1) {
        try {
            sTARGET_BRANCH = "${env.TARGET_BRANCH}"
        } catch (Throwable t2) {}
    }

    doSummarizeIssues("${JOB_NAME}", "${BRANCH_NAME}", sTARGET_BRANCH)
}
