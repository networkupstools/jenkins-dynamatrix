// Steps should not be in a package, to avoid CleanGroovyClassLoader exceptions...
// package org.nut.dynamatrix;

import hudson.plugins.git.GitSCM;
import hudson.plugins.git.UserRemoteConfig;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMRevisionAction;
import jenkins.scm.api.SCMSource;
import org.jenkinsci.plugins.github_branch_source.GitHubSCMSource;
import org.jenkinsci.plugins.github_branch_source.PullRequestSCMRevision;
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
def reportGithubStageStatus(def stashName, String message, String state, String messageContext = null) {
    if (dynamatrixGlobalState.enableGithubStatusHighlights) {
        try {
            Map scmVars = DynamatrixStash.getSCMVars(stashName)
            def scmCommit = scmVars?.GIT_COMMIT
            def scmURL = scmVars?.GIT_URL

            // Most of the time cached info is present, except
            // early in the job run - while the git checkout
            // and stash operation runs in a parallel stage.
            // The cached SCMVars only become known after the
            // appearance of that git workspace, but we send
            // some statuses before that.
            if (scmVars == null) {
                // At least if there's just one SCMSource attached
                // to the job definition, we can do this (TOCHECK:
                // what if there are many sources in definition?):
                SCMSource src = SCMSource.SourceByItem.findSource(currentBuild.rawBuild.getParent());
                SCMRevision revision = (src != null ? SCMRevisionAction.getRevision(src, currentBuild.rawBuild) : null);

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
                    if (!("dynamatrix" in c.getUrl())) {
                        scmURL = c.getUrl()
                        //scmCommit = ... ?
                    }
                }
            }

            if (scmCommit == null) {
                scmCommit = env?.GIT_COMMIT
            }

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
            }

            // e.g. "ci/jenkins/build-status", "integration" or "build"
            if (Utils.isStringNotEmpty(messageContext))
                stepArgs['contextSource'] = [$class: "ManuallyEnteredCommitContextSource", context: messageContext]

            if (dynamatrixGlobalState.enableDebugTrace) {
                echo "[DEBUG] reportGithubStageStatus():\n\t" +
                        "stashName=${Utils.castString(stashName)}\n\t" +
                        "scmVars=${Utils.castString(scmVars)}\n\t" +
                        "scmURL=${Utils.castString(scmURL)}\n\t" +
                        "scmCommit=${Utils.castString(scmCommit)}\n\t" +
                        "stepArgs=${Utils.castString(stepArgs)}"
            }

            step(stepArgs);
        } catch (Throwable t) {
            echo "WARNING: Tried to use GitHubCommitStatusSetter but got an exception; is github-plugin installed and configured?"
            if (dynamatrixGlobalState.enableDebugTrace)
                echo t.toString()
        }
    }
}
