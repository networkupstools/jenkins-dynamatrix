package org.nut.dynamatrix

// Inspired by https://github.com/mkobit/jenkins-pipeline-shared-library-example/blob/master/test/integration/groovy/com/mkobit/libraryexample/VarsExampleJunitTest.groovy
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition
import org.jenkinsci.plugins.workflow.job.WorkflowJob
import org.jenkinsci.plugins.workflow.job.WorkflowRun
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.jvnet.hudson.test.JenkinsRule

class genericPipelineTest {

    @Rule
    public JenkinsRule rule = new JenkinsRule()

    @Before
    void configureGlobalGitLibraries() {
        RuleBootstrapper.setup(rule)
    }

    @Test
    void "testing env interaction, inline method definitions and standard pipeline script step calling"() {
        final CpsFlowDefinition flow = new CpsFlowDefinition('''
        def evenOrOdd (int n) {
            if (n % 2 == 0) {
                echo "The build number is even"
            } else {
                echo "The build number is odd"
            }
        }
        
        evenOrOdd(env.BUILD_NUMBER as int)
    '''.stripIndent(), true)
        final WorkflowJob workflowJob = rule.createProject(WorkflowJob, 'project')
        workflowJob.definition = flow

        final WorkflowRun firstResult = rule.buildAndAssertSuccess(workflowJob)
        rule.assertLogContains('The build number is odd', firstResult)

        final WorkflowRun secondResult = rule.buildAndAssertSuccess(workflowJob)
        rule.assertLogContains('The build number is even', secondResult)
    }
}
