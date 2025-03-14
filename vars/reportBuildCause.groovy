#!/usr/bin/env groovy
/// ^^^ For syntax highlighters

import org.nut.dynamatrix.dynamatrixGlobalState;
import org.nut.dynamatrix.Utils;
import org.nut.dynamatrix.*;

/**
 * reportBuildCause - Report a build cause as an {@code addShortText} message.
 * @return      true if reached the end; null if there was nothing to report
 * @see https://stackoverflow.com/questions/43597803/how-to-differentiate-build-triggers-in-jenkins-pipeline
 * @see https://gist.github.com/SGTMcClain/15afb8a342910ccca4c24c3351fa1054
 */
def call() {
    def user = null
    def cause = null
    String msg = null

    // Preserve insertion order, and at most one of each entry:
    LinkedHashSet<String> causes = []
    currentBuild?.buildCauses?.each {
        def descr = it?.shortDescription?.trim()
        if (Utils.isStringNotEmpty(descr))
	    causes.add(descr)
    }

    String completeBuildCause = causes.join(' & ').trim()
    if (!(Utils.isStringNotEmpty(completeBuildCause))) {
        // Started by user? Several ways to test for that...
        def specificCause = findCauseType(
            currentBuild.rawBuild,
            hudson.model.Cause$UserIdCause)

        if (!specificCause) {
            // Following fine example from https://code-maven.com/jenkins-get-current-user
            specificCause = currentBuild.getBuildCauses('hudson.model.Cause$UserIdCause')
        }

        if (!specificCause) {
            specificCause = currentBuild.rawBuild.getCause(Cause.UserIdCause)
        }

        if (specificCause) {
            cause = specificCause
            user = specificCause.userName
        } else {
            // started by commit?
            specificCause = currentBuild.getBuildCauses('jenkins.branch.BranchEventCause')

            if (specificCause) {
                user = null
                cause = specificCause
                msg = "Started by commit (branch event)"
            } else {
                // started by timer?
                specificCause = currentBuild.getBuildCauses('hudson.triggers.TimerTrigger$TimerTriggerCause')

                if (specificCause) {
                    user = null
                    cause = specificCause
                    msg = "Started by timer"
                } else {
                    try {
                        if (dynamatrixGlobalState.enableDebugTrace)
                            echo("[DEBUG] Sleep a bit to time out and see a user cause, hopefully")
                        timeout(time: 1, unit: 'SECONDS') {
                            sleep 5
                        }
                    } catch (err) { // timeout reached
                        try {
                            user = err.getCauses()[0].getUser()
                            cause = err.getCauses()[0]
                        } catch (err2) {
                            try {
                                user = err.getCause().getUser()
                                cause = err.getCause()
                            } catch (err3) {
                                user = null
                                cause = null
                            }
                        }
                    }
                }
            }
        }

        if (msg == null)
            msg = ""
        if (user != null && (!(Utils.isString(user)) || (Utils.isStringNotEmpty(user))))
            msg += "Requested by " + user.toString()
    }

    if (!(Utils.isStringNotEmpty(msg)))
        msg = completeBuildCause

    if (currentBuild?.buildCauses)
        if (dynamatrixGlobalState.enableDebugTrace)
            echo("[DEBUG] currentBuild.buildCauses:" + currentBuild?.buildCauses)

    if (Utils.isStringNotEmpty(completeBuildCause) && msg != completeBuildCause) {
        if (dynamatrixGlobalState.enableDebugTrace)
            echo("[DEBUG] Summarized complete build cause(s):" + completeBuildCause)
    }

    if (Utils.isStringNotEmpty(msg)) {
        try {
            // https://plugins.jenkins.io/badge/ with API v2.x
            // https://www.jenkins.io/doc/pipeline/steps/badge/#addbadge-add-badge
            addBadge([
                text: msg,
                cssClass: "badge-jenkins-dynamatrix-Baseline badge-jenkins-dynamatrix-ReportBuildCause"
            ])
        } catch (Throwable t0) {
            try {
                // https://plugins.jenkins.io/groovy-postbuild/
                // Positional args:
                // addShortText(text, color, background, border, borderColor) -
                //   puts a badge with a short text, using the specified format.
                //   Supports html color names.
                manager.addShortText(
                    msg,
                    '#000000',
                    '#00FFC0',
                    1,
                    '#00C0A0'
                )
            } catch (Throwable t1) {
                try {
                    // https://plugins.jenkins.io/badge/ API before v2.x
                    // With one "text" arg or named parameters (map)
                    // addShortText(text: <text>, background: <background>,
                    //              border: <border>, borderColor: <borderColor>,
                    //              color: <color>, link: <link>)
                    addShortText([
                        text: msg,
                        color:       '#000000',
                        background:  '#00FFC0',
                        border: 1,
                        borderColor: '#00C0A0'
                    ])
                } catch (Throwable t2) {
                    echo "WARNING: Tried to addShortText(), but failed to; " +
                        "are the Groovy Postbuild plugin and/or " +
                        "jenkins-badge-plugin installed?" +
                        "\n" + (dynamatrixGlobalState.enableDebugTrace ? t1.toString() : t1.getMessage()) +
                        "\n" + (dynamatrixGlobalState.enableDebugTrace ? t2.toString() : t2.getMessage()) +
                        "\nMeant to say: " + msg
                }
            }
        }
    } else {
        return null
    }

    return true
}
