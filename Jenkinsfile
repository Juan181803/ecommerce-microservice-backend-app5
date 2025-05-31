pipeline {
    agent any

    tools {
        maven 'withMaven'
        jdk 'JDK_11'
    }

    environment {
        DOCKERHUB_USER = 'collinx0108'
        DOCKER_CREDENTIALS_ID = 'docker_hub_pwd'
        SERVICES = 'api-gateway cloud-config favourite-service order-service payment-service product-service proxy-client service-discovery shipping-service user-service'
        K8S_NAMESPACE = 'ecommerce'
    }

    stages {
        stage('Init') {
            steps {
                script {
                    echo "Detected branch: ${env.BRANCH_NAME}"
                    def profileConfig = [
                        master : ['prod', '-prod'],
                        stage: ['stage', '-stage'],
                        develop: ['dev', '-dev']
                    ]
                    def config = profileConfig.get(env.BRANCH_NAME, ['dev', '-dev'])

                    env.SPRING_PROFILES_ACTIVE = config[0]
                    env.IMAGE_TAG = config[0]
                    env.DEPLOYMENT_SUFFIX = config[1]

                    env.IS_MASTER = env.BRANCH_NAME == 'master' ? 'true' : 'false'
                    env.IS_STAGE = env.BRANCH_NAME == 'stage' ? 'true' : 'false'
                    env.IS_DEVELOP = env.BRANCH_NAME == 'develop' ? 'true' : 'false'
                    env.IS_FEATURE = env.BRANCH_NAME.startsWith('feature/') ? 'true' : 'false'

                    echo "Spring profile: ${env.SPRING_PROFILES_ACTIVE}"
                    echo "Image tag: ${env.IMAGE_TAG}"
                    echo "Deployment suffix: ${env.DEPLOYMENT_SUFFIX}"
                    echo "Flags: IS_MASTER=${env.IS_MASTER}, IS_STAGE=${env.IS_STAGE}, IS_DEVELOP=${env.IS_DEVELOP}, IS_FEATURE=${env.IS_FEATURE}"
                }
            }
        }

        stage('Checkout') {
            steps {
                git branch: "${env.BRANCH_NAME}", url: 'https://github.com/collinx0108/ecommerce-microservice-backend-app'
            }
        }

        stage('Verify Tools') {
            steps {
                sh 'java -version'
                sh 'mvn -version'
                sh 'docker --version'
                sh 'kubectl config current-context'
            }
        }

        stage('Build Services') {
            when {
                anyOf {
                    branch 'develop'
                    branch 'stage'
                    branch 'master'
                }
            }
            steps {
                sh 'mvn clean package -DskipTests'
            }
        }

        stage('Build Docker Images') {
            when {
                anyOf {
                    branch 'develop'
                    branch 'stage'
                    branch 'master'
                }
            }
            steps {
                script {
                    SERVICES.split().each { service ->
                        sh "docker build -t ${DOCKERHUB_USER}/${service}:${IMAGE_TAG} --build-arg SPRING_PROFILES_ACTIVE=${SPRING_PROFILES_ACTIVE} ./${service}"
                    }
                }
            }
        }

        stage('Push Docker Images') {
            when {
                anyOf {
                    branch 'develop'
                    branch 'stage'
                    branch 'master'
                }
            }
            steps {
                withCredentials([string(credentialsId: "${DOCKER_CREDENTIALS_ID}", variable: 'docker_hub_pwd')]) {
                    sh "docker login -u ${DOCKERHUB_USER} -p ${docker_hub_pwd}"
                    script {
                        SERVICES.split().each { service ->
                            sh "docker push ${DOCKERHUB_USER}/${service}:${IMAGE_TAG}"
                        }
                    }
                }
            }
        }

        stage('Unit Tests') {
            when { branch 'develop' }
            steps {
                script {
                    ['user-service', 'product-service'].each {
                        sh "mvn test -pl ${it}"
                    }
                }
            }
        }

        stage('Integration Tests') {
            when { branch 'stage' }
            steps {
                script {
                    ['user-service', 'product-service'].each {
                        sh "mvn verify -pl ${it}"
                    }
                }
            }
        }

        stage('E2E Tests') {
            when { branch 'stage' }
            steps {
                sh 'mvn clean test -pl e2e-tests'
            }
        }

        stage('Ensure Namespace') {
            when { branch 'master' }
            steps {
                sh "kubectl get namespace ${K8S_NAMESPACE} || kubectl create namespace ${K8S_NAMESPACE}"
            }
        }

        stage('Deploy Common Config') {
            when { branch 'master' }
            steps {
                sh "kubectl apply -f k8s/common-config.yaml -n ${K8S_NAMESPACE}"
            }
        }

        stage('Deploy Core Services') {
            when { branch 'master' }
            steps {
                sh "kubectl apply -f k8s/service-discovery/ -n ${K8S_NAMESPACE}"
                sh "kubectl set image deployment/service-discovery service-discovery=${DOCKERHUB_USER}/service-discovery:${IMAGE_TAG} -n ${K8S_NAMESPACE}"
                sh "kubectl set env deployment/service-discovery SPRING_PROFILES_ACTIVE=${SPRING_PROFILES_ACTIVE} -n ${K8S_NAMESPACE}"
                sh "kubectl get pods -n ${K8S_NAMESPACE} -l app=service-discovery"
                sh "kubectl describe deployment service-discovery -n ${K8S_NAMESPACE}"
                sh "kubectl rollout status deployment/service-discovery -n ${K8S_NAMESPACE} --timeout=300s"

                sh "kubectl apply -f k8s/cloud-config/ -n ${K8S_NAMESPACE}"
                sh "kubectl set image deployment/cloud-config cloud-config=${DOCKERHUB_USER}/cloud-config:${IMAGE_TAG} -n ${K8S_NAMESPACE}"
                sh "kubectl set env deployment/cloud-config SPRING_PROFILES_ACTIVE=${SPRING_PROFILES_ACTIVE} -n ${K8S_NAMESPACE}"
                sh "kubectl rollout status deployment/cloud-config -n ${K8S_NAMESPACE} --timeout=300s"
            }
        }

        stage('Deploy Microservices') {
            when { branch 'master' }
            steps {
                script {
                    def appServices = ['user-service', 'product-service', 'order-service', 'payment-service', 'shipping-service']

                    for (svc in appServices) {
                        def image = "${DOCKERHUB_USER}/${svc}:${IMAGE_TAG}"

                        sh "kubectl apply -f k8s/${svc}/ -n ${K8S_NAMESPACE}"
                        sh "kubectl set image deployment/${svc} ${svc}=${image} -n ${K8S_NAMESPACE}"
                        sh "kubectl set env deployment/${svc} SPRING_PROFILES_ACTIVE=${SPRING_PROFILES_ACTIVE} -n ${K8S_NAMESPACE}"
                        sh "kubectl rollout status deployment/${svc} -n ${K8S_NAMESPACE} --timeout=200s"
                    }
                }
            }
        }
    }

    post {
        success {
            script {
                echo "✅ Pipeline completed successfully for ${env.BRANCH_NAME} branch."
                echo "📊 Environment: ${env.SPRING_PROFILES_ACTIVE}"

                if (env.BRANCH_NAME == 'master') {
                    echo '🚀 Production deployment completed successfully!'
                } else if (env.BRANCH_NAME == 'stage') {
                    echo '🎯 Staging deployment completed successfully!'
                } else if (env.BRANCH_NAME == 'develop') {
                    echo '🔧 Development tests completed successfully!'
                }
            }
        }
        failure {
            script {
                echo "❌ Pipeline failed for ${env.BRANCH_NAME} branch."
                echo '🔍 Check the logs for details.'
                echo '📧 Notify the development team about the failure.'
            }
        }
        unstable {
            script {
                echo "⚠️ Pipeline completed with warnings for ${env.BRANCH_NAME} branch."
                echo '🔍 Some tests may have failed. Review test reports.'
            }
        }
    }
} 