#!/usr/bin/env groovy
import groovy.json.JsonSlurper

def call(){
    getDeveloperName()
    setJira()
    setTeamName()
    setSonarBranch()
    setBuildName()
}


def getDeveloperName() {
    if (env.GERRIT_CHANGE_OWNER_NAME) {
        env.DEVELOPER_NAME = env.GERRIT_CHANGE_OWNER_NAME.split(" ")[0]
        if (env.TEAM_NAME) {
            env.CONTRIBUTOR = "$DEVELOPER_NAME - $TEAM_NAME"
        } else {
            env.CONTRIBUTOR = "$DEVELOPER_NAME"
        }
    }
}

def setBuildName() {
    if (env.CONTRIBUTOR){
        currentBuild.displayName = "${BUILD_NUMBER} | ${env.SONAR_BRANCH} | ${CONTRIBUTOR}"
    } else {
        currentBuild.displayName = "${BUILD_NUMBER}"
    }
}

def setTeamName() {
    if (env.JIRA != '' && !env.JIRA.toUpperCase().contains('NO JIRA')) {
        def getTeamNameURL = "https://ci-portal.seli.wh.rnd.internal.ericsson.com/api/getteamfromjira/number/" + env.JIRA + "/?format=json"
        try {
            jsonText = new URL(getTeamNameURL).text
            TEAM_NAME = new JsonSlurper().parseText(jsonText).team
        }
        catch (Exception e) {
            println "no team"
        }
    }
}

def setJira() {
    def JiraMatcher = null
    if (env.GERRIT_CHANGE_SUBJECT) {
        if (GERRIT_CHANGE_SUBJECT =~ /(?i)(TORF-\d+(?!\d+))/) {
            JiraMatcher = (GERRIT_CHANGE_SUBJECT =~ /(?i)(TORF-\d+(?!\d+))/) // extract TORF reference in commit message
        }else if (GERRIT_CHANGE_SUBJECT =~ /(?i)(PDUOSSCNG-\d+(?!\d+))/) {
            JiraMatcher = (GERRIT_CHANGE_SUBJECT =~ /(?i)(PDUOSSCNG-\d+(?!\d+))/) // extract PDUOSSCNG reference in commit message
        }else if (GERRIT_CHANGE_SUBJECT =~ /(?i)(CIP-\d+(?!\d+))/) {
            JiraMatcher = (GERRIT_CHANGE_SUBJECT =~ /(?i)(CIP-\d+(?!\d+))/) // extract CIP reference in commit message
        }

        if (JiraMatcher) {
            env.JIRA = JiraMatcher[0][1]
        } else {
            env.JIRA = 'NO JIRA'
        }
    } else {
        env.JIRA = ''
    }
    JiraMatcher = null
}

def setSonarBranch() {
    if (env.GERRIT_CHANGE_SUBJECT) {
        if (env.JIRA != '' && !env.JIRA.toUpperCase().contains('NO JIRA')) {
            env.SONAR_BRANCH = env.JIRA
        } else {
            env.SONAR_BRANCH = 'No-Jira-' + GERRIT_CHANGE_NUMBER
        }
    } else if (env.VERSION) {
        env.SONAR_BRANCH = env.VERSION
    } else {
        env.SONAR_BRANCH = ''
    }
}

