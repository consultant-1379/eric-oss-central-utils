#!/usr/bin/env groovy
def call(){
    getQualityGate()
}

def getQualityGate() {
    // Wait for SonarQube Analysis is done and Quality Gate is pushed back
    qualityGate = waitForQualityGate()

    // If Analysis file exists, parse the Dashboard URL
    if (fileExists(file: 'target/sonar/report-task.txt')) {
        sh 'cat target/sonar/report-task.txt'
        def props = readProperties file: 'target/sonar/report-task.txt'
        env.DASHBOARD_URL = props['dashboardUrl']
    }

    if (qualityGate.status != 'OK') { // If Quality Gate Failed
        if (env.GERRIT_CHANGE_NUMBER) {
            env.SQ_MESSAGE = "'" + "SonarQube Quality Gate Failed: ${DASHBOARD_URL}" + "'"
            sh '''
               ssh -p 29418 lciadm100@gerrit.ericsson.se gerrit review --label 'SQ-Quality-Gate=-1'  \
                 --message ${SQ_MESSAGE} --project $GERRIT_PROJECT $GERRIT_PATCHSET_REVISION
            '''
            error "Pipeline aborted due to quality gate failure!\n Report: ${env.DASHBOARD_URL}"
        }
    } else if (env.GERRIT_CHANGE_NUMBER) { // If Quality Gate Passed
        env.SQ_MESSAGE = "'" + "SonarQube Quality Gate Passed: ${DASHBOARD_URL}" + "'"
        sh '''
            ssh -p 29418 lciadm100@gerrit.ericsson.se gerrit review --label 'SQ-Quality-Gate=+1'  \
                --message ${SQ_MESSAGE} --project $GERRIT_PROJECT $GERRIT_PATCHSET_REVISION
         '''
    }
}
