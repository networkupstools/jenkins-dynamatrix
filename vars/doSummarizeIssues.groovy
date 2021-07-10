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
    doSummarizeIssues("${JOB_NAME}", "${BRANCH_NAME}", TARGET_BRANCH ? "${TARGET_BRANCH}" : "master")
}
