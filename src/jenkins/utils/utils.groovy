#!/usr/bin/groovy
package jenkins.utils;

/**********************************
 Helm / Kubernetes Functions
 **********************************/
def cleanupHelmReleases(String... releases) {
    echo 'Cleanup Helm releases...'

    sh 'mkdir -p helm-home'
    for (String release : releases) {
        sh "${HELM_CMD} delete --purge $release || true"
    }
    cleanupNamespaces(releases)
    sh 'sleep 30'
}

def cleanupNamespaces(String... namespaces) {
    echo 'Cleanup namespaces...'

    sh '${KUBECTL_CMD} get namespaces || true'
    for (String namespace : namespaces) {
        sh "${KUBECTL_CMD} delete namespace $namespace || true"
    }
    sh '${KUBECTL_CMD} get namespaces || true'
}

/**********************************
 Build Functions
 **********************************/
def mavenInstallWithStaticAnalysis() {
    script {
        if (env.STATIC_ANALYSIS_ENABLED == "true") {
            echo 'Static analysis has been enabled. (i.e. STATIC_ANALYSIS_ENABLED = true)'
            sh 'mvn install -B -V -P static-analysis jacoco:prepare-agent jacoco:report'
        } else {
            echo 'Static analysis has been disabled. (i.e. STATIC_ANALYSIS_ENABLED = false)'
            sh 'mvn install -B -V -P jacoco:prepare-agent jacoco:report'
        }
    }
}

def mavenReleaseWithStaticAnalysis() {
    script {
        echo 'Retrieve the POM version before the maven release in order to check out this release at a later stage.'
        pom = readMavenPom file: 'pom.xml'
        RELEASE_VERSION = pom.version.substring(0, pom.version.indexOf('-SNAPSHOT'))

        if (env.STATIC_ANALYSIS_ENABLED == "true") {
            echo 'Static analysis has been enabled. (i.e. STATIC_ANALYSIS_ENABLED = true)'
            sh "mvn -B -V -Dresume=false release:prepare release:perform -P static-analysis -DpreparationGoals='install' -Dgoals='clean deploy' -DlocalCheckout=true"
        } else {
            echo 'Static analysis has been disabled. (i.e. STATIC_ANALYSIS_ENABLED = false)'
            sh "mvn -B -V -Dresume=false release:prepare release:perform -DpreparationGoals='install' -Dgoals='clean deploy' -DlocalCheckout=true"
        }
    }
}

def checkOutRelease() {
    echo 'Checkout Release'
    sh "git checkout ${SERVICE_NAME}-${RELEASE_VERSION}"
}

def staticAnalysisReports() {
    script {
        if (env.STATIC_ANALYSIS_ENABLED == "true") {
            archiveArtifacts allowEmptyArchive: true, artifacts: '**/target/pit-reports/'
            step([$class: 'JacocoPublisher', execPattern: '**/**.exec'])

            recordIssues enabledForFailure: true, failedTotalAll: 1, aggregatingResults: true,
                    tools: [
                            checkStyle(pattern: '**/target/checkstyle-result.xml'),
                            spotBugs(pattern: '**/target/spotbugsXml.xml'),
                            pmdParser(pattern: '**/target/pmd.xml'),
                            pit(pattern: '**/target/pit-reports/**/mutations.xml')
                    ],
                    filters: [
                            excludeCategory('NO_COVERAGE') // Excluding from PIT analysis
                    ]
        }
    }
}

/**********************************
 Publish Functions
 **********************************/
def tag_release() {
    echo 'Get helm chart version from artifact.properties'
    def CHART_VERSION = sh(returnStdout: true, script: "cat artifact.properties | grep CHART_VERSION | cut -f2 -d'='").trim()
    echo 'Tag release with helm chart version'
    sh "git tag -a ${CHART_VERSION} -m \"Helm chart version: ${CHART_VERSION}\""
    echo 'Push tags to master'
    sh "git push --tags gcn master"
}

/**********************************
 Pre Functions
 **********************************/
def injectFiles() {
    echo 'Inject maven settings.xml file'
    configFileProvider([configFile(fileId: "$GAZELLES_SETTINGS_XML", targetLocation: "${HOME}/.m2/settings.xml")]) {}
    echo 'Inject kubernetes config file'
    configFileProvider([configFile(fileId: "$GAZELLES_K8S_CONFIG", targetLocation: "${env.WORKSPACE}/.kube/")]) {}
    echo 'Inject credentials for armdocker'
    configFileProvider([configFile(fileId: "$GAZELLES_DOCKER_REGISTRY_CREDENTIALS", targetLocation: "${env.WORKSPACE}/repositories.yaml")]) {}
}

/**********************************
 Post Functions
 **********************************/
def postAlways() {
    archiveArtifacts allowEmptyArchive: true, artifacts: 'design-rule-check-report.*'
    archiveArtifacts allowEmptyArchive: true, artifacts: '.bob/design-rule-check-report.*'
    archiveArtifacts allowEmptyArchive: true, artifacts: '*.log'

    step([$class: 'ClaimPublisher'])
}

def postSuccess() {
    echo 'Add steps here for jobs with success status here...'
}

def postFailure() {
    script {
        if (!env.PRE_CODE_REVIEW) {
            emailext to: 'PDLAZELLES@pdl.internal.ericsson.com', recipientProviders: [culprits(), developers(), requestor(), brokenBuildSuspects()], subject: "FAILURE: ${currentBuild.fullDisplayName}", body: "<b>Jenkins job failed:</b><br><br>Project: ${env.JOB_NAME}<br>Build Number: ${env.BUILD_NUMBER}<br>${env.BUILD_URL}", mimeType: 'text/html'
        }
    }
}

def modifyBuildDescription(GERRIT_REPO_NAME, DOCKER_REPO_NAME) {
    script {
        if (env.RELEASE == "true") {
            try {
                echo 'Get helm chart version from artifact.properties'
                def CHART_NAME = sh(returnStdout: true, script: "cat artifact.properties | grep CHART_NAME | cut -f2 -d'='").trim()
                def CHART_VERSION = sh(returnStdout: true, script: "cat artifact.properties | grep CHART_VERSION | cut -f2 -d'='").trim()
                def CHART_REPO = sh(returnStdout: true, script: "cat artifact.properties | grep CHART_REPO | cut -f2 -d'='").trim()

                CHART_DOWNLOAD_LINK = "${CHART_REPO}/${CHART_NAME}/${CHART_NAME}-${CHART_VERSION}.tgz"
                IMAGE_ARTIFACTORY_LINK = "https://arm.epk.ericsson.se/artifactory/webapp/#/artifacts/browse/tree/General/docker-v2-global-local/${DOCKER_REPO_NAME}/${CHART_NAME}/${CHART_VERSION}"
                currentBuild.description = "Helm Chart: <a href=${CHART_DOWNLOAD_LINK}>${CHART_NAME}-${CHART_VERSION}.tgz</a><br>Docker Image: <a href=${IMAGE_ARTIFACTORY_LINK}>${CHART_VERSION}</a><br>"

                pom = readMavenPom file: 'pom.xml'
                POM_VERSION = pom.version

                currentBuild.description = "Helm Chart: <a href=${CHART_DOWNLOAD_LINK}>${CHART_NAME}-${CHART_VERSION}.tgz</a><br>Docker Image: <a href=${IMAGE_ARTIFACTORY_LINK}>${CHART_VERSION}</a><br>Gerrit: ${POM_VERSION}<br>"
                GERRIT_LINK = "https://gerrit.sero.gic.ericsson.se/gitweb?p=${GERRIT_REPO_NAME}.git;a=tree;hb=refs/tags/${CHART_VERSION}"
                currentBuild.description = "Helm Chart: <a href=${CHART_DOWNLOAD_LINK}>${CHART_NAME}-${CHART_VERSION}.tgz</a><br>Docker Image: <a href=${IMAGE_ARTIFACTORY_LINK}>${CHART_VERSION}</a><br>Gerrit: <a href=${GERRIT_LINK}>${POM_VERSION}</a><br>"
            }
            catch (ignored) {
            }
        }
    }
}

/**********************************
 Log Functions
 **********************************/
def ossCentralFunction_get_logs_for_each_namespace(String... namespaces) {
    ossCentralFunction_get_server_address()
    try {
        def script_bash = libraryResource 'eric-oss-central/collect_ADP_logs.sh'
        // create a file with script_bash content
        writeFile file: './scripts/collect_ADP_logs.sh', text: script_bash
        sh "chmod 777 ./scripts/collect_ADP_logs.sh"
        sh "export HELM_CMD='docker run --rm linkyard/docker-helm:2.10.0'"
        sh "export KUBECTL_CMD='docker run --rm linkyard/kubectl kubectl '"
        for (String namepace : namespaces) {

            sh '''./scripts/collect_ADP_logs.sh ''' + namepace + ''''' '''
        }
        archiveArtifacts artifacts: 'logs_*.tgz'
    }
    catch (Exception e) {
        print "Error getting logs " + e
    }
}

def ossCentralFunction_get_server_address() {
    try {
        def server = sh(returnStdout: true, script: "${KUBECTL_CMD} get ing --all-namespaces | grep api | awk '{print \$3}'")
        print "************************SERVER INFORMATION************************\n" +
                "******************************************************************\n" +
                "******************************************************************\n" +
                server +
                "******************************************************************\n" +
                "******************************************************************\n" +
                "******************************************************************"
    }
    catch (Exception e) {
        print "Error getting getting server address " + e
    }
}