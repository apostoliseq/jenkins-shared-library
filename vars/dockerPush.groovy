def call(String repoName) {
  sh "docker push ${repoName}"
}
