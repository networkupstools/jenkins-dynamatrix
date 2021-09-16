import org.nut.dynamatrix.dynamatrixGlobalState;
import org.nut.dynamatrix.Utils;

/*
 * Return the label (expression) string for a worker that handles
 * git checkouts and stashing of the source for other build agents.
 * Unlike the other agents, this worker should have appropriate
 * internet access, possibly reference git repository cache, etc.
 */
def labelDefaultWorker() {
    // Global/modifiable config point:
    if (Utils.isStringNotEmpty(dynamatrixGlobalState.labelDefaultWorker)) {
        return dynamatrixGlobalState.labelDefaultWorker
    }
    return 'master-worker'
}

def labelCheckoutWorker() {
    // Global/modifiable config point:
    if (Utils.isStringNotEmpty(dynamatrixGlobalState.labelCheckoutWorker)) {
        return dynamatrixGlobalState.labelCheckoutWorker
    }
    return labelDefaultWorker()
}

def labelDocumentationWorker() {
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

def branchDefaultStable() {
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
 */
def listChangedFiles() {
    def changedFiles = []

    // Is this a Git-driven build? And a PR at that?
    if (env?.CHANGE_TARGET && env?.GIT_COMMIT) {
        // Inspired by https://issues.jenkins.io/browse/JENKINS-54285?focusedCommentId=353839
        // ...and assumes running in the fetched unpacked workspace dir
        changedFiles = sh(
            script: "git diff --name-only origin/${env.CHANGE_TARGET}...${env.GIT_COMMIT}",
            returnStdout: true
        ).split('\n')
        if (changedFiles.size() > 0)
            return changedFiles
    }

    // https://stackoverflow.com/a/59462020/4715872
    def changeLogSets = currentBuild.changeSets
    // Not sure how well this works for PR changesets vs.
    // change from last build in the branch only
    // (and reportedly empty for new builds in a branch...)
    for (entries in changeLogSets) {
        for (entry in entries) {
            for (file in entry.affectedFiles) {
                //echo "Found changed file: ${file.path}"
                changedFiles += "${file.path}"
            }
        }
    }
    return changedFiles
}
