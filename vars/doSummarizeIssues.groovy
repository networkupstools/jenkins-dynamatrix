import org.nut.dynamatrix.*;

void call(String JOB_NAME, String BRANCH_NAME) {
    script {
        def reference = JOB_NAME.replace(BRANCH_NAME, "master")
        publishIssues id: 'analysis', name: 'All Issues',
            referenceJobName: reference,
            issues: dynamatrixGlobalState.issueAnalysis,
            filters: [includePackage('io.jenkins.plugins.analysis.*')]
    }
} // doSummarizeIssues()
