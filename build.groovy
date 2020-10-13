properties([
        parameters([
                string(defaultValue: params.BRANCH_NAME ?: '7.6.2-interset', description: 'Git branch name', name: 'BRANCH_NAME', trim: false),
                string(defaultValue: params.GIT_USER_CREDENTIALS_ID ?: "git-interset-readonly", description: 'Git user credentials identifier', name: 'GIT_USER_CREDENTIALS_ID', trim: false),
                string(defaultValue: params.REPO_URL ?: "git@github.com:MicroFocus/elasticsearch-hadoop.git", description: 'Elasticsearch-hadoop Git Repo URL', name: 'REPO_URL', trim: false),
                string(defaultValue: params.BUILD_NODE ?: 'kb_build_dev', description: 'Node to perform build on', name: 'BUILD_NODE', trim: false)
        ]),
        buildDiscarder(logRotator(artifactNumToKeepStr: '10', numToKeepStr: '10')),
        disableConcurrentBuilds()
])

node("${params.BUILD_NODE}") {
    stage('Pull Source') {
        timeout(time: 5, unit: 'MINUTES') {
            git branch: params.BRANCH_NAME, noTags: true, shallow: true, credentialsId: params.GIT_USER_CREDENTIALS_ID, url: params.REPO_URL
        }
        sh 'git clean -d -x -f'
        sh 'git log --format="%ae" | head -1 > commit-author.txt'
        script {
            currentBuild.description = params.BRANCH_NAME + ", " + readFile('commit-author.txt').trim()
        }
    }

    stage('Build Project') {
        timeout(time: 10, unit: 'MINUTES') {
            catchError(buildResult: 'FAILURE', stageResult: 'FAILURE') {
                ansiColor('xterm') {
                    sh(script: "sh ./gradlew clean distZip --stacktrace")
                }
            }
            archiveArtifacts artifacts: 'spark/sql-20/build/libs/*.jar', fingerprint: true
        }
    }

    stage('Cleanup Workspace') {
        cleanWs()
    }
}
