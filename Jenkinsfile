#!/usr/bin/env groovy

pipeline {
    agent any

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
    }
