def call(dockerRepoName, imageName, portNum) {
    pipeline {
        agent any // Use any available agent to run the pipeline

        parameters {
            booleanParam(defaultValue: false, description: 'Deploy the App', name: 'DEPLOY')
        }

        stages {
            // Stage for setting up the virtual environment and installing dependencies
            stage('Build') {
                steps {
                    sh "python3 -m venv venv" // Create a virtual environment
                    sh ". venv/bin/activate" // Activate the virtual environment
                    // Install dependencies listed in requirements.txt, ignoring installed packages
                    sh "venv/bin/pip install -r requirements.txt"
                    // Upgrade Flask within the virtual environment, ignoring installed packages
                    sh "venv/bin/pip install --upgrade flask"
                    sh "venv/bin/pip install coverage" // Install coverage for code coverage analysis
                }
            }

            // Stage for running Python Lint to check code quality
            stage('Python Lint') {
                steps {
                    sh 'pylint --fail-under 5.0 *.py' // Run pylint on all .py files, failing if score < 5.0
                }
            }

            // Stage for running tests with coverage
            stage('Test and Coverage') {
                steps {
                    script {
                        // Check if old test reports exist and remove them to start fresh
                        def test_reports_exist = fileExists 'test-reports'
                        if (test_reports_exist) { 
                            sh 'rm test-reports/*.xml || true'
                        }
                        def api_test_reports_exist = fileExists 'api-test-reports'
                        if (api_test_reports_exist) { 
                            sh 'rm api-test-reports/*.xml || true'
                        }
                    }
                    script {
                        // Find all test files starting with 'test' and run them with coverage
                        def files = findFiles(glob: 'test*.py')
                        for (file in files) {
                            sh "venv/bin/coverage run --omit */site-packages/*,*/dist-packages/* ${file}"
                        }
                        sh "venv/bin/coverage report" // Generate an aggregated coverage report
                    }
                }
                post {
                    always {
                        script {
                            // Publish junit test results and coverage reports if they exist
                            def test_reports_exist = fileExists 'test-reports'
                            if (test_reports_exist) { 
                                junit 'test-reports/*.xml'
                            }
                            def api_test_reports_exist = fileExists 'api-test-reports'
                            if (api_test_reports_exist) { 
                                junit 'api-test-reports/*.xml'
                            }
                        }
                    }
                }
            }

            stage('Package') {
                when {
                    expression { env.GIT_BRANCH == 'origin/main' }
                }
                steps {
                    withCredentials([string(credentialsId: 'DockerHub', variable: 'TOKEN')]) {
                        sh "docker login -u 'mleeee' -p '$TOKEN' docker.io"
                        sh "docker build -t ${dockerRepoName}:latest --tag mleeee/${dockerRepoName}:${imageName} ."
                        sh "docker push mleeee/${dockerRepoName}:${imageName}"
                    }
                }
            }

            // Stage for zipping all Python (.py) files into an archive
            stage('Zip Artifacts') {
                steps {
                    sh "zip app.zip *.py" // Zip all .py files into app.zip
                }
                post {
                    always {
                        // Archive the zip file as a build artifact
                        archiveArtifacts artifacts: 'app.zip', allowEmptyArchive: true
                    }
                }
            }
                    
            stage('Deliver') {
                when {
                    expression { params.DEPLOY }
                }
                steps {
                    // sh "docker run ${dockerRepoName}:latest"
                    sh "docker stop ${dockerRepoName} || true && docker rm ${dockerRepoName} || true"
                    sh "docker run -d -p ${portNum}:${portNum} --name ${dockerRepoName} ${dockerRepoName}:latest"
                }
            }
        }
    }
}
