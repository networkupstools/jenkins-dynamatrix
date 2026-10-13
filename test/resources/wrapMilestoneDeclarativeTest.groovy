// TEST EXAMPLE: Declarative pipeline

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

