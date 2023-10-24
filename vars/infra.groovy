// Steps should not be in a package, to avoid CleanGroovyClassLoader exceptions...
// package org.nut.dynamatrix;

import hudson.plugins.git.GitSCM;
import hudson.plugins.git.UserRemoteConfig;
import hudson.plugins.git.util.BuildData;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMRevisionAction;
import jenkins.scm.api.SCMSource;
import org.jenkinsci.plugins.github_branch_source.GitHubSCMSource;
import org.jenkinsci.plugins.github_branch_source.PullRequestSCMRevision;
import org.jenkinsci.plugins.workflow.steps.scm.MultiSCMRevisionState;
import org.nut.dynamatrix.DynamatrixStash;
import org.nut.dynamatrix.dynamatrixGlobalState;
import org.nut.dynamatrix.Utils;

/**
 * Return the label (expression) string for a worker that handles
 * git checkouts and stashing of the source for other build agents.
 * Unlike the other agents, this worker should have appropriate
 * internet access, possibly reference git repository cache, etc.
 */
String labelDefaultWorker() {
    // Global/modifiable config point:
    if (Utils.isStringNotEmpty(dynamatrixGlobalState.labelDefaultWorker)) {
        return dynamatrixGlobalState.labelDefaultWorker
    }
    return 'master-worker'
}

/**
 * Returns the Jenkins agent label (expression) for a worker that
 * can be used for SCM checkouts.
 * @see infra#labelDefaultWorker
 * @see dynamatrixGlobalState#labelCheckoutWorker
 */
String labelCheckoutWorker() {
    // Global/modifiable config point:
    if (Utils.isStringNotEmpty(dynamatrixGlobalState.labelCheckoutWorker)) {
        return dynamatrixGlobalState.labelCheckoutWorker
    }
    return labelDefaultWorker()
}

/**
 * Returns the Jenkins agent label (expression) for a worker that
 * can be used for documentation processing (spell check, HTML/PDF
 * rendering, etc.)<br/>
 *
 * As a fallback (if not specified in the
 * {@link dynamatrixGlobalState#labelDocumentationWorker} setting,
 * looks for {@code 'doc-builder'} in currently defined build agents.
 *
 * @see infra#labelDefaultWorker
 * @see dynamatrixGlobalState#labelDocumentationWorker
 */
String labelDocumentationWorker() {
    // Global/modifiable config point:
    if (Utils.isStringNotEmpty(dynamatrixGlobalState.labelDocumentationWorker)) {
        return dynamatrixGlobalState.labelDocumentationWorker
    }

    // Do any nodes declare a capability to build any doc types
    // that the hosted projects might need?
    def nodeList = nodesByLabel (label: 'doc-builder', offline: true)
    if (nodeList.size() > 0)
        return 'doc-builder'

    return labelDefaultWorker()
}

/**
 * Returns the default "stable branch" name for SCM operations, from
 * settings or a hard-coded default.
 * @see dynamatrixGlobalState#branchDefaultStable
 */
String branchDefaultStable() {
    if (Utils.isStringNotEmpty(dynamatrixGlobalState.branchDefaultStable)) {
        return dynamatrixGlobalState.branchDefaultStable
    }

    // TODO: Detect via Git or SCM REST API somehow?
    return "master"
    // return "main"
}

/**
 * Compares the current branch and target branch and list the changed files.
 *
 * @return the PR or directly branch changed files.
 *
 * @see #listChangedFilesJenkinsData
 */
Set<String> listChangedFilesGitWorkspace() {
    Set<String> changedFiles = []

    // Is this a Git-driven build? And a PR at that?
    if (env?.CHANGE_TARGET) {
        if (env?.GIT_COMMIT) {
            // Inspired by https://issues.jenkins.io/browse/JENKINS-54285?focusedCommentId=353839
            // ...and assumes running in the fetched unpacked workspace dir
            changedFiles = sh(
                script: "git diff --name-only origin/${env.CHANGE_TARGET}...${env.GIT_COMMIT}",
                returnStdout: true
            ).split('\n').each { it.trim() }
            if (changedFiles.size() > 0)
                return changedFiles
        }

        // GIT_COMMIT is not an ubiquitously available variable,
        // so fall back to reporting the current workspace state
        changedFiles = sh(
            script: "git diff --name-only origin/${env.CHANGE_TARGET}...HEAD",
            returnStdout: true
        ).split('\n').each { it.trim() }
        if (changedFiles.size() > 0)
            return changedFiles

        changedFiles = sh(
            script: "git diff --name-only origin/${env.CHANGE_TARGET}",
            returnStdout: true
        ).split('\n').each { it.trim() }
        if (changedFiles.size() > 0)
            return changedFiles
    }

    // empty if here
    return changedFiles
}

/**
 * Returns the source code path names impacted by the current build
 * as compared to an earlier one, according to Jenkins' own logic.
 *
 * @see #listChangedFilesGitWorkspace
 */
Set<String> listChangedFilesJenkinsData() {
    // https://stackoverflow.com/a/59462020/4715872
    Set<String> changedFiles = []
    def changeLogSets = currentBuild.changeSets
    // Not sure how well this works for PR changesets vs.
    // change from last build in the branch only
    // (and reportedly empty for new builds in a branch...)
    for (def entries in changeLogSets) {
        for (def entry in entries) {
            for (def file in entry.affectedFiles) {
                try {
                    //echo "Found changed file: ${file.path}"
                    changedFiles += "${file.path}".trim()
                } catch (Throwable ignored) {} // no-op
            }
        }
    }
    return changedFiles
}

/**
 * Compares different clues to list the changed files.
 *
 * @return the PR or directly branch changed files.
 *
 * @see #listChangedFilesJenkinsData
 * @see #listChangedFilesGitWorkspace
 */
Set<String> listChangedFiles() {
    Set<String> changedFiles = listChangedFilesGitWorkspace()
    changedFiles += listChangedFilesJenkinsData()
    return (Set<String>)(changedFiles.flatten())
}

/**
 * Optional reporter of github status events which allows to trace certain
 * but not all stages or situations (e.g. only failures of matrix cells).
 * For Git URL/commit references uses DynamatrixStash.getSCMVars() cached data.
 * Inspired by https://github.com/jenkinsci/github-plugin README examples,
 * https://github.com/jenkinsci/github-plugin/blob/master/src/main/java/org/jenkinsci/plugins/github/status/GitHubCommitStatusSetter.java
 * and https://github.com/jenkinsci/github-branch-source-plugin/blob/master/src/main/java/org/jenkinsci/plugins/github_branch_source/GitHubBuildStatusNotification.java#L337
 * sources. See also https://docs.github.com/rest/commits/statuses#create-a-commit-status
 */
def reportGithubStageStatus(def stashName, String message, String state, String messageContext = null, String backrefUrl = null) {
    boolean doDebug = (
            dynamatrixGlobalState.enableDebugTraceGithubStatusHighlights
            || (dynamatrixGlobalState.enableDebugTrace && dynamatrixGlobalState.enableDebugTraceGithubStatusHighlights != false)
    )

    if (doDebug) {
        echo "[DEBUG] reportGithubStageStatus called; dynamatrixGlobalState.enableGithubStatusHighlights=${dynamatrixGlobalState.enableGithubStatusHighlights}, stashName=${stashName}, message=${message}, state=${state}, messageContext=${messageContext}, backrefUrl=${backrefUrl}"
    }

    if (dynamatrixGlobalState.enableGithubStatusHighlights) {
        try {
            String stashNameUsed = null
            Map scmVars = null
            // If this was a first report before/during checkout, so info about
            // it was not stashed yet and we had to discover git information
            // elsewhere - cache that. In particular, this may help "against"
            // pull requests rebuilt automatically as a "merge" job with an
            // ephemeral commit (after the target branch marches on due to
            // other development), and the actual stashed info about the git
            // workspace checked out and recorded would have a tip commit hash
            // unknown to github. So we actually prefer that info if available.
            if (stashName != null) {
                stashNameUsed = stashName + ":reportGithubStageStatus-orig"
                scmVars = DynamatrixStash.getSCMVars(stashNameUsed)
            }
            if (scmVars == null) {
                stashNameUsed = stashName
                scmVars = DynamatrixStash.getSCMVars(stashNameUsed)
            }
            String scmCommit = scmVars?.GIT_COMMIT
            String scmURL = scmVars?.GIT_URL

            // Most of the time cached info is present, except
            // early in the job run - while the git checkout
            // and stash operation runs in a parallel stage.
            // The cached SCMVars only become known after the
            // appearance of that git workspace, but we send
            // some statuses before that.
/*
    // INVESTIGATION HACKS
    def j = Jenkins.instance.getItemByFullName("nut/nut/fightwarn")
    println "job: ${j}"
    def build = j.getBuildByNumber(117)

    println build

    def commitHashForBuild(build) {
      def scmAction = null
      build?.actions.each { action ->
        println "action: <${action?.getClass()}>'${action}'"
        if (action instanceof jenkins.scm.api.SCMRevisionAction)
            scmAction = action
      }
      println "scmAction: <${scmAction?.getClass()}>'${scmAction}'"
      println "scmAction.revision: <${scmAction?.revision?.getClass()}>'${scmAction?.revision}'"
      println "scmAction.revision.hash: <${scmAction?.revision?.hash?.getClass()}>'${scmAction?.revision?.hash}'"
      return scmAction?.revision?.hash
    }

    println commitHashForBuild(build)
*/
            if (scmVars == null) {
                // At least if there's just one SCMSource attached
                // to the job definition, we can do this (TOCHECK:
                // what if there are many sources in definition?):
                SCMSource src = SCMSource.SourceByItem.findSource(currentBuild.rawBuild.getParent());
                SCMRevision revision = (src != null ? SCMRevisionAction.getRevision(src, currentBuild.rawBuild) : null);

                if (doDebug) {
                    echo ("[DEBUG] reportGithubStageStatus discovering from SCMSource=${Utils.castString(src)} and SCMRevision=${Utils.castString(revision)}")
                }

                if (src != null && revision != null
                && revision instanceof PullRequestSCMRevision
                && src instanceof GitHubSCMSource
                ) {
                    // PRs are "<source>+<target> (<ephemeral>)", e.g.
                    // 1aaff29c6706228a1fcae1c933e611f8b6aad441+5dc7970253626986815d79c5c7fa295bf221c876 (2259e6ff57cfb00864beb056f1720bab28cd0a64)
                    String s = (revision.toString().trim() - ~/\+.*$/).trim()
                    scmCommit = s

                    // Use reflection to avoid casting and required
                    // imports and thus plugins installed
                    def methodName = "getRepositoryUrl"
                    scmURL = src."$methodName"()
                }
            }

            if (scmURL == null && scm != null && scm instanceof GitSCM) {
                for(UserRemoteConfig c : scm.getUserRemoteConfigs()) {
                    if (doDebug) {
                        echo ("[DEBUG] reportGithubStageStatus discovering from scm.getUserRemoteConfigs()): ${Utils.castString(c)}")
                    }

                    if (!("dynamatrix" in c.getUrl())) {
                        scmURL = c.getUrl()
                        //scmCommit = ... ?
                    }
                }

                if (scmURL != null && doDebug) {
                    echo ("[DEBUG] reportGithubStageStatus discovered for scmURL from scm.getUserRemoteConfigs()): ${scmURL}")
                }
            }

            if (scmCommit == null) {
                Set scmRevisionActions = []
                Set scmBuildDataActions = []
                currentBuild?.rawBuild?.actions?.each { def action ->
                    if (action instanceof SCMRevisionAction) {
                        // action: <class jenkins.scm.api.SCMRevisionAction>'jenkins.scm.api.SCMRevisionAction@4d7f0122'
                        def hash = action?.revision?.hash
                        if (hash)
                            scmRevisionActions << hash
                        return
                    }

                    if (action instanceof BuildData) {
                        // Be sure to use the tested application project repo, not the dynamatrix library one!
                        // action: <class hudson.plugins.git.util.BuildData>'hudson.plugins.git.util.BuildData@1e28ec57[scmName=,remoteUrls=[https://github.com/networkupstools/jenkins-dynamatrix.git],buildsByBranchName={fightwarn=Build #117 of Revision 324a60a8c515e12e246b3e2dd6006a2e24b92163 (fightwarn), gitcache=Build #73 of Revision 37befb64cf2c1050ee52e953b31f66131b3cdf50 (gitcache), master=Build #109 of Revision 1ebcfae07883a03683f6fd4f9e4f7b57af874b33 (master)},lastBuild=Build #117 of Revision 324a60a8c515e12e246b3e2dd6006a2e24b92163 (fightwarn)]'
                        // action: <class hudson.plugins.git.util.BuildData>'hudson.plugins.git.util.BuildData@ba407b9a[scmName=,remoteUrls=[https://github.com/networkupstools/nut.git],buildsByBranchName={fightwarn=Build #117 of Revision 971f53ef95cb275bae22a691106dff867bdb4c2c (fightwarn)},lastBuild=Build #117 of Revision 971f53ef95cb275bae22a691106dff867bdb4c2c (fightwarn)]'
                        if (!(action.hasBeenBuilt(null)))
                            return

                        Boolean wrongRepo = false
                        action?.getRemoteUrls()?.each {
                            if (wrongRepo) return
                            if (it?.toLowerCase() ==~ /.*dynamatrix.*/)
                                wrongRepo = true
                            if (scmURL != null && it?.toLowerCase() != scmURL.toLowerCase())
                                wrongRepo = true
                        }
                        if (wrongRepo)
                            return

                        def hash = action?.lastBuild?.SHA1?.toString()
                        if (hash)
                            scmBuildDataActions << hash
                        return
                    }

                    // CAN'T USE (without reflection): package-private final class MultiSCMRevisionState extends hudson.scm.SCMRevisionState
                    // action: <class org.jenkinsci.plugins.workflow.steps.scm.MultiSCMRevisionState>'MultiSCMRevisionState{git https://github.com/networkupstools/jenkins-dynamatrix.git=hudson.scm.SCMRevisionState$None@739f26cc, git https://github.com/networkupstools/nut.git=hudson.scm.SCMRevisionState$None@739f26cc}'

                    // action: <class io.jenkins.plugins.forensics.git.reference.GitCommitsRecord>'Commits in 'nut/nut/fightwarn #117': 5 (latest: 324a60a8c515e12e246b3e2dd6006a2e24b92163)'
                    // action: <class io.jenkins.plugins.forensics.git.reference.GitCommitsRecord>'Commits in 'nut/nut/fightwarn #117': 200 (latest: 971f53ef95cb275bae22a691106dff867bdb4c2c)'

                    // TOTHINK: Can we tap into discoveries of the competitors? :)
                    // action: <class io.jenkins.plugins.checks.github.GitHubChecksAction>'io.jenkins.plugins.checks.github.GitHubChecksAction@366e9872'
                    // action: <class org.jenkinsci.plugins.githubautostatus.BuildStatusAction>'org.jenkinsci.plugins.githubautostatus.BuildStatusAction@2b24af81'

                    if (Utils.isListNotEmpty(scmBuildDataActions) && scmBuildDataActions.size() == 1) {
                        scmCommit = scmBuildDataActions[0]
                    } else {
                        if (Utils.isListNotEmpty(scmRevisionActions) && scmRevisionActions.size() == 1) {
                            scmCommit = scmRevisionActions[0]
                        }
                    }

                    if (doDebug) {
                        echo ("[DEBUG] reportGithubStageStatus discovered for scmCommit: scmBuildDataActions=${scmBuildDataActions} scmRevisionActions=${scmRevisionActions} " +
                                (scmCommit == null ? "and could not pick exactly one" : "and picked ${scmCommit}")
                        )
                    }
                }
            }

            if (scmCommit == null) {
                scmCommit = env?.GIT_COMMIT
            }

/*
            // TODO: consider pr/1979/head or pull/1979/head - note
            //  this may march on while the build is running?
            if (scmCommit == null && env?.BRANCH_NAME ==~ /^PR-[0-9]+$/ ) {
                scmCommit = env?.GIT_COMMIT
            }
*/

            if (scmURL == null) {
                scmURL = env?.GIT_URL
            }

            // Honour the limit, 140 chars as last reported for this writing:
            //    Failed to update commit state on GitHub. Ignoring exception
            //    [{"message":"Validation Failed","errors":[{
            //     "resource":"Status","code":"custom","field":"description",
            //     "message":"description is too long (maximum is 140 characters)"}],
            // As of this writing, the plugin step does not return a value
            // in case of an error during posting, just logs it:
            // https://github.com/jenkinsci/github-plugin/blob/309cf75c74ba1f254b9fe09fc43b5d9e08956813/src/main/java/org/jenkinsci/plugins/github/status/GitHubCommitStatusSetter.java#L132
            if (message.length() > 140) {
                message = message.substring(0, 135) - ~/ [^\s]*$/ + "<...>"
            }

            Map stepArgs = [
                    $class            : "GitHubCommitStatusSetter",
                    errorHandlers     : [[$class: 'ShallowAnyErrorHandler']],
                    //errorHandlers: [[$class: "ChangingBuildStatusErrorHandler", result: "UNSTABLE"]],
                    statusResultSource: [
                            $class: "ConditionalStatusResultSource",
                            results: [[
                                              $class: "AnyBuildResult",
                                              message: message,
                                              state: state
                                      ]]
                    ]
            ]

            if (Utils.isStringNotEmpty(scmURL) && Utils.isStringNotEmpty(scmCommit)) {
                stepArgs['reposSource'] = [$class: "ManuallyEnteredRepositorySource", url: scmURL]
                stepArgs['commitShaSource'] = [$class: "ManuallyEnteredShaSource", sha: scmCommit]

                // See comments above
                if (scmVars == null && stashName != null) {
                    String scmVarsKey = "${stashName}:reportGithubStageStatus-orig"
                    scmVars = DynamatrixStash.getSCMVarsPrivate()
                    if (!(scmVars.containsKey(scmVarsKey))) {
                        scmVars[scmVarsKey] = [:]
                    }
                    scmVars[scmVarsKey].GIT_COMMIT = scmCommit
                    scmVars[scmVarsKey].GIT_URL = scmURL
                    stashNameUsed = scmVarsKey
                }
            }

            // e.g. "ci/jenkins/build-status", "integration" or "build"
            // re-use same context with different status or message as we progress from recognition of a codepath to its verdict
            // use different contexts for different practical job aspects, e.g. spellcheck vs shellcheck
            if (Utils.isStringNotEmpty(messageContext))
                stepArgs['contextSource'] = [$class: "ManuallyEnteredCommitContextSource", context: messageContext]

            // e.g. "https://ci.networkupstools.org/job/nut/job/nut/job/PR-2063/69//artifact/.ci.MD5_899dfa229658900e3de07f19c790e888.check.log.gz"
            // Defaults to https://github.com/jenkinsci/github-plugin/blob/master/src/main/java/org/jenkinsci/plugins/github/status/sources/BuildRefBackrefSource.java
            // that uses the Display URL of the build (redirects to one of UI implementation pages)
            if (Utils.isStringNotEmpty(backrefUrl))
                stepArgs['statusBackrefSource'] = [$class: "ManuallyEnteredBackrefSource", backref: backrefUrl]

            if (doDebug) {
                echo "[DEBUG] reportGithubStageStatus() with GitHubCommitStatusSetter step:\n\t" +
                        "stashName=${Utils.castString(stashName)}\n\t" +
                        "stashNameUsed=${Utils.castString(stashNameUsed)}\n\t" +
                        "scmVars=${Utils.castString(scmVars)}\n\t" +
                        "scmURL=${Utils.castString(scmURL)}\n\t" +
                        "scmCommit=${Utils.castString(scmCommit)}\n\t" +
                        "stepArgs=${Utils.castString(stepArgs)}"
            }

            step(stepArgs);
        } catch (Throwable t) {
            echo "WARNING: Tried to use GitHubCommitStatusSetter but got an exception; is github-plugin installed and configured?"

            if (doDebug) {
                echo t.toString()
            }
        }
    }
}
