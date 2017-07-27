pipeline {
  agent { docker 'maven:3-alpine' }
  stages {
    stage('Build') {
      steps {
        sh 'mvn clean install'
      }
    }
    stage('Archive') {
      steps {
        archiveArtifacts artifacts: '**/target/*.jar', fingerprint: true
      }
    }
  }
}
