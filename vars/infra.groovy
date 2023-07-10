// Steps should not be in a package, to avoid CleanGroovyClassLoader exceptions...
// package org.nut.dynamatrix;

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
