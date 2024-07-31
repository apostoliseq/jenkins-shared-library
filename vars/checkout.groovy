def call(Map config = [:]) {
  script {
    currentBuild.displayName = "#${currentBuild.number} Branch: ${config.gitBranch}"
  }
  checkout([
    $class: 'GitSCM',
    branches: [[name: "${config.gitBranch}"]],
    userRemoteConfigs: 
        [[
            credentialsId: "${config.gitCredsId}",
            url: "${config.gitUrl}"
        ]]
  ])
}
