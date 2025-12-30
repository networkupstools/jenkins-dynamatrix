// Steps should not be in a package, to avoid CleanGroovyClassLoader exceptions...
// package org.nut.dynamatrix;

import jenkins.model.InterruptedBuildAction;
import org.nut.dynamatrix.Utils;
import org.nut.dynamatrix.dynamatrixGlobalState;

/** wrapMilestone: Wrapper around {@code milestone} step to help
 *  report cases when a job does not pass it. Note we usually need
 *  to also {@code reportUnpassedMilestones} in a post{} phase of
 *  the declarative pipeline (or try/finally in scripted), because
 *  this all behaves very non-deterministically in Jenkins/Groovy/CPS
 *  machinery.
 */
def call(Map stepArgs) {
    Boolean _passed = null
    Boolean _reported = false
    String _msg = null
    Map stats = [
        passed: _passed,
        msg: _msg,
        reported: _reported,
        stepArgs: stepArgs,
        stepDescr: "Milestone step" +
            (Utils.isStringNotEmpty(stepArgs?.get('label')?.trim()) ? " (${stepArgs.label})" : "")
    ]
    // Note: for some reason, this does not always "flush" into
    // pipeline's stored knowledge before the milestone fails,
    // so the array can remain empty :( So we have a fallback
    // of checking all result data of the job too.
    dynamatrixGlobalState.startedMilestones << stats

    try {
        if (dynamatrixGlobalState.enableDebugTrace)
            echo "Starting milestone: ${stepArgs}"

        catchError {
            // Note: most of this hassle seems to be in vain,
            // the job thread gets interrupted and we have to
            // use pipeline{} post{} handling to report this.
            milestone(stepArgs)
        }

        if (dynamatrixGlobalState.enableDebugTrace)
            echo "Completed milestone: ${stepArgs}"

        analyzeMilestoneAftermath(stats)

        if (stats.msg == null) {
            // assume all is ok?
            stats.passed = true
        }
    } catch (Throwable t) {
        // Note: no summary here, it is added to the build page by plugin itself
        // (as it is a `CancelledCause extends CauseOfInterruption`, probably)
        stats.passed = false
        stats.msg = stats.stepDescr + " threw: ${t}".toString()
        throw t
    } finally {
        reportMilestone(stats)
    }
}

/** Changes the caller-provided map */
def reportMilestone(Map stats) {
    if (stats.passed == null && stats.msg == null) {
        // was e.interrupt() too harsh in
        // https://github.com/jenkinsci/pipeline-milestone-step-plugin/blob/master/src/main/java/org/jenkinsci/plugins/pipeline/milestone/MilestoneStorage.java ?
        stats.msg = stats.stepDescr + " did not succeed"
    }

    if (stats.msg != null) {
        if (dynamatrixGlobalState.enableDebugTrace || stats.passed == null) {
            // We do not echo this by default, as the plugin reports
            // e.g. `Superseded by nut/nut/PR-3200#8` on its own; but
            // maybe the throwable message has more/different details?
            echo "${stats.msg}"
        }

        // Avoid duplicate badges
        if (stats.reported)
            return

        try {
            // Badge v2.x API, with style
            addBadge([
                text: "${stats.msg}".toString(),
                cssClass: "badge-jenkins-dynamatrix-Baseline badge-jenkins-dynamatrix-SlowBuild-NOT_BUILT"
            ])
        } catch (Throwable ignored) {
            try {
                // https://plugins.jenkins.io/groovy-postbuild/
                // addShortText(text, color, background, border, borderColor)
                manager.addShortText(
                    stats.msg,
                    '#000000',
                    '#B0B0B0',
                    1,
                    '#00C0A0'
                )
            } catch (Throwable ignored2) {
                try {
                    // https://plugins.jenkins.io/badge/ API before v2.x
                    addShortText([
                        text: "${stats.msg}".toString(),
                        color: '#000000',
                        background: '#B0B0B0',
                        border: 1,
                        borderColor: '#00C0A0'
                    ])
                } catch (Throwable t2) {
                    echo "WARNING: Tried to addInfoBadge(), but failed to; is the jenkins-badge-plugin installed?"
                    if (dynamatrixGlobalState.enableDebugTrace) {
                        echo t2.toString()
                    }
                }
            }
        } // try adding the badge

        stats.reported = true
    }
}

/** Changes the caller-provided map */
def analyzeMilestoneAftermath(Map stats) {
    if (stats.msg == null) {
        try {
            if (dynamatrixGlobalState.enableDebugTrace)
                echo "Looking at build causes: ${currentBuild.getBuildCauses()}"
            currentBuild.getBuildCauses().each {
                if ("${it}" ==~ /.*CancelledCause.*/) {
                    stats.msg = stats.stepDescr + " ended with this job cancelled: ${it}"
                }
            }
        } catch (Throwable ignored) {
            if (dynamatrixGlobalState.enableDebugTrace)
                echo "Could not look at build causes: ${ignored}"
        }
    }

    if (stats.msg == null) {
        try {
            if (dynamatrixGlobalState.enableDebugTrace)
                echo "Looking at raw build causes: ${currentBuild.rawBuild.causes}"
            currentBuild.rawBuild.causes.each {
                if ("${it}" ==~ /.*CancelledCause.*/) {
                    stats.msg = stats.stepDescr + " ended with this job cancelled: ${it}"
                }
            }
        } catch (Throwable ignored) {
            if (dynamatrixGlobalState.enableDebugTrace)
                echo "Could not look at raw build causes: ${ignored}"
        }
    }

    if (stats.msg == null) {
        try {
            if (dynamatrixGlobalState.enableDebugTrace)
                echo "Looking at raw build actions: ${currentBuild.rawBuild.actions}"
            currentBuild.rawBuild.actions.each {
                if ("${it}" ==~ /.*CancelledCause.*/) {
                    stats.msg = stats.stepDescr + " ended with this job cancelled: ${it}"
                }
                if ("${it}" ==~ /.*InterruptedBuildAction.*/ || it instanceof InterruptedBuildAction) {
                    if (dynamatrixGlobalState.enableDebugTrace)
                        echo "InterruptedBuildAction: ${it} =>"
                    if (dynamatrixGlobalState.enableDebugTrace)
                        echo "InterruptedBuildAction.causes(): ${it.causes}"
                    it.causes.each { it2 ->
                        if ("${it2}" ==~ /.*org.jenkinsci.plugins.pipeline.milestone.CancelledCause.*/) {
                            stats.msg = stats.stepDescr + " ended with this job interrupted and cancelled: ${it2.getShortDescription()}"
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
            if (dynamatrixGlobalState.enableDebugTrace)
                echo "Could not look at raw build causes: ${ignored}"
        }
    }

    if (stats.msg == null && currentBuild.result == 'FAILURE') {
        stats.msg = stats.stepDescr + " ended with this job result becoming FAILURE"
    }

    if (stats.msg == null && currentBuild.result == 'NOT_BUILT') {
        stats.msg = stats.stepDescr + " ended with this job result becoming NOT_BUILT"
    }

    if (stats.msg == null
        && (Thread.interrupted() || Thread.currentThread().isInterrupted())
    ) {
        stats.msg = stats.stepDescr + " ended with this job thread interrupted"
    }
}

/** For {@code pipeline{post{always{...}}}} notification handling */
def reportUnpassedMilestones() {
    if (dynamatrixGlobalState.enableDebugTrace)
        echo "dynamatrixGlobalState.startedMilestones: ${Utils.castString(dynamatrixGlobalState.startedMilestones)}"

    def reported = false
    dynamatrixGlobalState.startedMilestones.each { Map stats ->
        if (stats.reported)
            return // continue

        if (stats.passed == null)
            analyzeMilestoneAftermath(stats)

        reportMilestone(stats)
        if (stats.reported)
            reported = true
    }

    // TOTHINK: Go here only if NOT_BUILT?..
    //  And/or of dynamatrixGlobalState.startedMilestones
    //  did not store anything (alas, it does not always
    //  "flush" the added entry quickly enough, it seems).
    if (!reported) {
        Boolean _passed = null
        Boolean _reported = false
        String _msg = null
        Map stats = [
            passed: _passed,
            msg: _msg,
            reported: _reported,
            stepArgs: [:],
            stepDescr: "A milestone step"
        ]

        analyzeMilestoneAftermath(stats)

        if (stats.msg != null) {
            reportMilestone(stats)
            reported = true
        }
    }

    return reported
}

/* TEST EXAMPLE: Declarative pipeline

////////////////////////////////////////////////////////////////
@Library('jenkins-dynamatrix@${BRANCH_NAME}') _
import org.nut.dynamatrix.dynamatrixGlobalState;
import org.nut.dynamatrix.*;

dynamatrixGlobalState.enableDebugTrace = true

pipeline {
    agent any
    stages {
        stage ("Discovery") {
            steps {
                sleep 10
                wrapMilestone label: "Waiting for discovery"
            }
        }
        stage ("Heavy work") {
            steps {
                echo "Doing heavy work"
                sleep 5
            }
        }
    }
    post {
        always {
            script {
                reportUnpassedMilestones()
            }
        }
    }
}
////////////////////////////////////////////////////////////////

 */

/* TEST EXAMPLE: Scripted pipeline

////////////////////////////////////////////////////////////////
@Library('jenkins-dynamatrix@${BRANCH_NAME}') _
import org.nut.dynamatrix.dynamatrixGlobalState;
import org.nut.dynamatrix.*;

dynamatrixGlobalState.enableDebugTrace = true

node() {
    try {
        stage ("Discovery") {
            sleep 10
            wrapMilestone label: "Waiting for discovery"
        }

        stage ("Heavy work") {
            echo "Doing heavy work"
            sleep 5
        }
    } finally {
        reportUnpassedMilestones()
    }
}
////////////////////////////////////////////////////////////////

 */
