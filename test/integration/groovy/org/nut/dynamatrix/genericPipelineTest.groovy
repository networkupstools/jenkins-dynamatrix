package org.nut.dynamatrix;

// Inspired by https://github.com/mkobit/jenkins-pipeline-shared-library-example/blob/master/test/integration/groovy/com/mkobit/libraryexample/VarsExampleJunitTest.groovy
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.jvnet.hudson.test.recipes.WithTimeout;

/**
 * NOTE: Sometimes JenkinsRule takes a long while to start,
 * timing out (default 180s) before the actual test-case
 * pipeline begins to run. A second test case usually
 * succeeds as Jenkins is just about ready by then.
 * We bump this for all cases to succeed better.
 */
@WithJenkins
class genericPipelineTest {
    @Test
    void "Example testing env interaction, inline method definitions and standard pipeline script step calling"(JenkinsRule rule) {
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

    /** Half test, half JSL/test developer troubleshooting aid */
    @Test
    @WithTimeout(1500)
    void "Testing list of loaded plugins"(JenkinsRule rule) {
        final CpsFlowDefinition flow = new CpsFlowDefinition('''
            //import jenkins.model.Jenkins
            def plugins = jenkins.model.Jenkins.instance.getPluginManager().getPlugins()
            echo "=== The following ${plugins?.size()} plugins are installed:"
            String l = ""
			plugins.each {
				l += "${it.getDisplayName()} (${it.getShortName()}): ${it.getVersion()}\\n"
			}
			echo "${l}"
            echo "=== End of list"
        ''', false)

        final WorkflowJob workflowJob = rule.createProject(WorkflowJob, 'pList')
        workflowJob.definition = flow
        WorkflowRun run = workflowJob.scheduleBuild2(0).get()

        rule.assertBuildStatus(hudson.model.Result.SUCCESS, run)

        def plugins = rule.getInstance().pluginManager.plugins
        System.err.println("=== For reference, JenkinsRule plugin list (${plugins?.size()}) reported by test harness:".toString())
        String l = ""
        plugins?.each {
            l += "${it.getDisplayName()} (${it.getShortName()}): ${it.getVersion()}\n"
        }
        System.err.println(l + "\n=== End of list")

        rule.assertLogContains("durable-task", run)
        rule.assertLogContains("Durable Task Plugin", run)
    }

    /** Make sure gradle setup lets us run all the integration tests
     * (some fail without prerequisites below, solutions to which
     * are on the recipe's side rather than JSL or test code base).
     */
    @Test
    @WithTimeout(1500)
    void "Testing durable task plugin availability - sh step"(JenkinsRule rule) {
        final CpsFlowDefinition flow = new CpsFlowDefinition('''
            node {
                sh 'echo Hello from Shell'
            }
        ''', false)

        final WorkflowJob workflowJob = rule.createProject(WorkflowJob, 'pSh')
        workflowJob.definition = flow
        WorkflowRun run = workflowJob.scheduleBuild2(0).get();

        rule.assertBuildStatus(hudson.model.Result.SUCCESS, run)
        rule.assertLogContains("Hello from Shell", run)
    }
}
