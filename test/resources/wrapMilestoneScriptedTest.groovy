// TEST EXAMPLE: Scripted pipeline

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

