def call() {
  pipeline {
      agent {
        kubernetes {
          defaultContainer 'docker'
          yaml """
          kind: Pod
          spec:
            containers:
            - name: docker
              image: docker:19.03.1-dind
              command:
              - dockerd-entrypoint.sh
              args:
              - --insecure-registry=10.43.232.207:8082
              securityContext:
              privileged: true
            - name: maven
              image: maven:3.8.5-openjdk-17
              command:
              - sleep
              args:
              - 99d
            - name: helm
              image: alpine/helm:3.15.3
              command:
              - sleep
              args:
              - 99d
          """
        }
      }
      tools {
          maven 'maven-3.9.8'
      }
      environment {
          GIT_URL= 'https://github.com/apostoliseq/test-quarkus'
          GIT_CREDS_ID= 'github'
          GIT_BRANCH= 'main'
  
          DOCKERHUB_CREDS = 'dockerhub'
  
          NEXUS_URL = '10.43.232.207'
          NEXUS_PORT = '8081'
          DOCKER_REPO_PORT = '8082'
          DOCKER_REPO_PATH = 'repository/docker-repo'
          HELM_REPO_PATH = 'repository/helm-repo'
          
          KUBERNETES_NAMESPACE = 'namespace1'
          CHART_NAME = 'quarkus-app-chart'
      }
      stages {
          stage('Checkout') {
              steps {
                  script {
                      currentBuild.displayName = "#${currentBuild.number} Branch: $GIT_BRANCH"
                  }
                  checkout([
                      $class: 'GitSCM',
                      branches: [[name: "$GIT_BRANCH"]],
                      userRemoteConfigs: 
                          [[
                              credentialsId: "$GIT_CREDS_ID",
                              url: "$GIT_URL"
                          ]]
                  ])
              }
          }
          stage('Build Maven Project') {
            steps {
              container('maven') {
                sh 'mvn -f ./pom.xml clean package'
              }
            }
          }
          stage('Build and Push Quarkus App Docker Image to Dockerhub') {
            steps {
              withCredentials([usernamePassword(credentialsId: 'dockerhub', passwordVariable: 'DOCKERHUB_PASSWORD', usernameVariable: 'DOCKERHUB_USERNAME')]) {
                sh 'docker build -t apostoliseq/test-app:1.0 -f ./src/main/docker/Dockerfile.jvm .'
                sh 'docker login -u "$DOCKERHUB_USERNAME" -p "$DOCKERHUB_PASSWORD" docker.io'
                sh 'docker push test-app:1.0'
              }
            }
          }
          stage('Deploy Quarkus App using local Helm Chart and Push Helm Chart to Nexus') {
            steps {
              container('helm') {
                sh 'helm uninstall $CHART_NAME -n $KUBERNETES_NAMESPACE'
                sh 'helm install $CHART_NAME ./$CHART_NAME -n $KUBERNETES_NAMESPACE'
              }
            }
          }
          stage('Push Quarkus App Docker Image to Nexus') {
            steps {
              withCredentials([usernamePassword(credentialsId: 'nexus', passwordVariable: 'NEXUS_PASSWORD', usernameVariable: 'NEXUS_USERNAME')]) {
                sh 'docker login -u "$NEXUS_USERNAME" -p "$NEXUS_PASSWORD" $NEXUS_URL:$DOCKER_REPO_PORT/$DOCKER_REPO_PATH'
                sh 'docker tag apostoliseq/test-app:1.0 $NEXUS_URL:$DOCKER_REPO_PORT/$DOCKER_REPO_PATH/apostoliseq/test-app:1.0'
                sh 'docker push $NEXUS_URL:$DOCKER_REPO_PORT/$DOCKER_REPO_PATH/apostoliseq/test-app:1.0'
              }
            }
          }
          stage('Package and Push Helm Chart to Nexus') {
            steps {
              container('helm') {
                withCredentials([usernamePassword(credentialsId: 'nexus', passwordVariable: 'NEXUS_PASSWORD', usernameVariable: 'NEXUS_USERNAME')]) {
                  sh 'helm repo add nexus-repo http://$NEXUS_USERNAME:$NEXUS_PASSWORD@$NEXUS_URL:$NEXUS_PORT/$HELM_REPO_PATH'
                  sh 'helm package ./$CHART_NAME'
                  sh 'curl -u $NEXUS_USERNAME:$NEXUS_PASSWORD --upload-file $CHART_NAME-0.1.0.tgz http://$NEXUS_URL:$NEXUS_PORT/$HELM_REPO_PATH/$CHART_NAME-0.1.0.tgz'
                }
              }
            }
          }
          stage('Deploy Quarkus App using Nexus Repo') {
            steps {
              container('helm') {
                sh 'helm uninstall -n $KUBERNETES_NAMESPACE $CHART_NAME'
                sh 'helm repo update'
                sh 'helm upgrade --install -n $KUBERNETES_NAMESPACE $CHART_NAME nexus-repo/$CHART_NAME'
          }
        }
      }
    }
  }
}
