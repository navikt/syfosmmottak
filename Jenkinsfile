#!/usr/bin/env groovy

pipeline {
    agent any

    environment {
        APPLICATION_NAME = 'syfosmmottak'
        DOCKER_SLUG = 'syfo'
        DISABLE_SLACK_MESSAGES = true
    }

    stages {
        stage('initialize') {
            steps {
                init action: 'gradle'
            }
        }
        stage('build') {
            steps {
                sh './gradlew build -x test'
            }
        }
        stage('run tests (unit & intergration)') {
            steps {
                sh './gradlew test'
            }
        }
        stage('create uber jar') {
            steps {
                sh './gradlew shadowJar'
                slackStatus status: 'passed'
            }
        }
        stage('push docker image') {
            steps {
                dockerUtils action: 'createPushImage'
            }
        }
        stage('Create kafka topics') {
            steps {
                sh 'echo TODO'
                // TODO
            }
        }
        stage('deploy to preprod') {
            steps {
                deployApp action: 'kubectlSubcommand', cluster: 'preprod-fss', subcmd: "create configmap syfosmmottak-application-config --from-file config/preprod/application.json"
                deployApp action: 'kubectlDeploy', cluster: 'preprod-fss'
            }
        }
        stage('deploy to production') {
            when { environment name: 'DEPLOY_TO', value: 'production' }
            steps {
                deployApp action: 'kubectlSubcommand', cluster: 'prod-fss', subcmd: "create configmap syfosmmottak-application-config --from-file config/prod/application.json"
                deployApp action: 'kubectlDeploy', cluster: 'prod-fss'
                githubStatus action: 'tagRelease'
            }
        }
    }
    post {
        always {
            postProcess action: 'always'
        }
        success {
            postProcess action: 'success'
        }
        failure {
            postProcess action: 'failure'
        }
    }
}
