#!/usr/bin/groovy
package jenkins.utils;

/********************************************************************************
 Function to compare Helm Chart Major Version check and invoke notification func
 Mandatory Environment variables:
 - ${WORKSPACE}
 - ${APPLICATION_NAME}
 - ${HELM_CMD}
 - ${CHART_NAME}
 Mandatory Parameter:
 - ${ADPGS_AUTO_UPDATE_MAJOR_VERSIONS_ENABLED}
 *******************************************************************************/
def majorChartVersionsCompare(String helmCmd, String helmChartDir) {
    script {
        env.HELM_DEP_CHART_NAME = getHelmDependencyChartName("${helmCmd}","${helmChartDir}")
        env.HELM_DEP_CHART_VERSION = getHelmDependencyChartVersion("${helmCmd}","${helmChartDir}")
        env.HELM_INT_CMD = "${helmCmd}"
        env.HELM_INT_CHART_DIR = "${helmChartDir}"
    }
    sh '''
        set +x
        IFS='.' # dot is set as delimiter
        read -ra input_majorv <<< "${CHART_VERSION}" # string is read into an array as tokens separated by IFS
        read -ra req_majorv <<< "${HELM_DEP_CHART_VERSION}" # string is read into an array as tokens separated by IFS

        if [[ "${req_majorv[0]}" != "" && "${req_majorv[0]}" != "." ]]; then        
            echo "By default ADPGS_AUTO_UPDATE_MAJOR_VERSIONS_ENABLED parameter set to FALSE (unchecked) and these pipeline stages list will be skipped if Major Version increment:\n\
                - 'Execute Common TAF'\n\
                - 'Commit Changes'\n\
                - 'Publish'"
            echo "You can change ADPGS_AUTO_UPDATE_MAJOR_VERSIONS_ENABLED default value to TRUE to update Major version Automatically without mentioned stages skipping."
            echo "Here is ${APPLICATION_NAME} Helm up-to-date Dependency list:"

            echo "${HELM_INT_CMD} dependency list ${HELM_INT_CHART_DIR}"
        
            echo "---------------------------------------------------------------------------------------------------------"
            echo "Source(input) Microservice Chart Name: ${CHART_NAME}" && echo "Source(input) Microservice Helm Chart Version: ${CHART_VERSION}"
            echo "---------------------------------------------------------------------------------------------------------"
            echo "'${APPLICATION_NAME}' Helm dependency Microservice Name: ${HELM_DEP_CHART_NAME}" && echo "'${APPLICATION_NAME}' Helm dependency Microservice Version: ${HELM_DEP_CHART_VERSION}"
            echo "---------------------------------------------------------------------------------------------------------"            

            if [ "${input_majorv[0]}" = "${req_majorv[0]}" ]; then
                echo "Major Version: ${input_majorv[0]} is EQUAL to son-common-package requirement Chart Major Version: ${req_majorv[0]}"
                echo "---------------------------------------------------------------------------------------------------------"
            else
                echo "Microservice Helm Chart Name: ${CHART_NAME}" && echo "Input Chart Major Version: ${input_majorv[0]} is DIFFER with son-common-package requirement Chart Major Version: ${req_majorv[0]}."
                echo 'true' >> MAJOR_VERSION_IS_DIFF
            fi
        else 
            echo "Chart ${CHART_NAME} is not at helm dependency list, nothing to compare."
        fi
    '''
    script {
        if (isNotify()) {
            sendEmailNotification(params.NOTIFY_EMAIL)
        }
    }
}

/**********************************************************************************************
 Function for determining whether to send an email notification about a major version increment
 *********************************************************************************************/
def isNotify() {
    if ( isMajorVersionChanged() ) {
        echo "Major Version was incremented!"
        return true
    } else {
        echo "Major Version was NOT incremented!"
        return false
    }
}

/******************************************************************************
 Function for sending email notification of a increment Microservice Major version
 Mandatory Parameter:
 - ${NOTIFY_EMAIL}
 *****************************************************************************/
def sendEmailNotification(String notifyEmail) {
    try {
        emailext to: "${notifyEmail}",
                recipientProviders: [culprits(), developers(), requestor(), brokenBuildSuspects()],
                subject: "Major version of ADP Generic Service ${CHART_NAME} was incremented ${currentBuild.fullDisplayName}",
                body: "Microservice Chart Name: <b>${CHART_NAME}</b><br>Microservice Helm Chart Major Version: <b>${CHART_VERSION}</b> is DIFFER with '${APPLICATION_NAME}' Helm up-to-date Dependence Chart Major Version: <b>${HELM_DEP_CHART_VERSION}</b><br>Job Name: ${env.JOB_NAME}<br>Build Number: ${env.BUILD_NUMBER}<br>By default ADPGS_AUTO_UPDATE_MAJOR_VERSIONS_ENABLED parameter set to FALSE.<br>You can change ADPGS_AUTO_UPDATE_MAJOR_VERSIONS_ENABLED default value to TRUE to update Major version automatically.<br>More details at Jenkins job console output:<br>${env.BUILD_URL}console", mimeType: 'text/html'
        echo "Email notification successfully sent!"
    }
    catch(Exception e)
    {
        echo "Something went wrong(... Notification was not sent.\n"
        print e
    }
}

/***************************************************************
 Function to determine if Microservice Major Version has incremented
 **************************************************************/
def isMajorVersionChanged() {
    IMD_FILE = fileExists "${env.WORKSPACE}/MAJOR_VERSION_IS_DIFF"
    return IMD_FILE
}

/*******************************************
 Function to get Helm Dependency Chart Name
 ******************************************/
def getHelmDependencyChartName(String helmCmd, String helmChartDir) {
    script {
        env.HELM_INT_CMD = "${helmCmd}"
        env.HELM_INT_CHART_DIR = "${helmChartDir}"
    }
    return "${sh(script:"set +x && ${HELM_INT_CMD} dependency list ${HELM_INT_CHART_DIR} | grep ${CHART_NAME} | awk '{print \$1}'", returnStdout: true)}"
}

/**********************************************
 Function to get Helm Dependency Chart Version
 *********************************************/
def getHelmDependencyChartVersion(String helmCmd, String helmChartDir) {
    script {
        env.HELM_INT_CMD = "${helmCmd}"
        env.HELM_INT_CHART_DIR = "${helmChartDir}"
    }
    return "${sh(script:"set +x && ${HELM_INT_CMD} dependency list ${HELM_INT_CHART_DIR} | grep ${CHART_NAME} | awk '{print \$2}'", returnStdout: true)}"
}