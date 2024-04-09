def call(dockerRepoName, imageName, portNum) {
    pipeline {
        agent any
        parameters {
            booleanParam(defaultValue: false, description: 'Deploy the App', name: 'DEPLOY')
            string(defaultValue: 'staging', description: '', name: 'DEPLOY_ENV')
        }

        stages {
            // stage('Lint') {
            //     steps {
            //         script {
            //             def currentDir = pwd().split('/').last()
            //             newDir = currentDir.split('-').last() 
            //             sh "pylint --fail-under 5.0 ${newDir}/*.py" 
            //         }
            //     }
            // }
            stage('Security') {
                steps {
                    script {
                        def currentDir = pwd().split('/').last()
                        newDir = currentDir.split('-').last() 
                        sh """
                            python3 -m venv .venv
                        """
                        sh """
                            . .venv/bin/activate
                            pip install bandit
                            bandit -r ${newDir}/*.py
                        """
                    }
                }
            }
            stage('Package') {
                when {
                    expression { env.GIT_BRANCH == 'origin/main' }
                }
                steps {
                    withCredentials([string(credentialsId: 'DockerHub-Carmen', variable: 'ACCESS_TOKEN')]) {
                        sh "echo $ACCESS_TOKEN | docker login -u mymangos --password-stdin docker.io"
                        script {
                            def currentDir = pwd().split('/').last()
                            sh "docker build -t ${dockerRepoName}:latest --tag mymangos/${dockerRepoName}:${imageName} ${newDir}/."
                        }
                        sh "docker push mymangos/${dockerRepoName}:${imageName}"
                    }
                }
            }
            stage('Deploy') {
                when {
                    expression { params.DEPLOY }
                }
                steps {
                    sshagent(credentials: ['acit3855']) {
                        sh """
                            [ -d ~/.ssh ] || mkdir ~/.ssh && chmod 0700 ~/.ssh
                            ssh-keyscan -t rsa,dsa 172.203.113.97>> ~/.ssh/known_hosts
                        """
                        sh "ssh azureuser@172.203.113.97 'docker pull mymangos/${dockerRepoName}:${imageName}'"
                        sh "ssh azureuser@172.203.113.97 'docker compose up -d'"
                    }
                }
            }
        }
    }
}
