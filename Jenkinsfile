#!/usr/bin/env groovy

pipeline {
    agent any

    environment {
        APPLICATION_NAME = 'syfomottak'
        APPLICATION_SERVICE = 'TODO'
        APPLICATION_COMPONENT = 'TODO'
        FASIT_ENVIRONMENT = 'q1'
        ZONE = 'fss'
        DOCKER_SLUG = 'integrasjon'
    }

    stages {
            stage('initialize') {
                steps {
                    script {
                        sh './gradlew clean'
                        applicationVersion = sh(script: './gradlew -q printVersion', returnStdout: true).trim()
                    }
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
            stage('extract application files') {
                steps {
                    sh './gradlew installDist'
                }
            }

        stage('push docker image') {
            steps {
                dockerUtils action: 'createPushImage'
            }
        }
        stage('validate & upload nais.yaml to nexus m2internal') {
            steps {
                nais action: 'validate'
                nais action: 'upload'
            }
        }
        stage('deploy to preprod') {
            steps {
                deploy action: 'jiraPreprod'
            }
        }
        stage('deploy to production') {
            when { environment name: 'DEPLOY_TO', value: 'production' }
            steps {
                deploy action: 'jiraProd'
            }
        }
    }
}
