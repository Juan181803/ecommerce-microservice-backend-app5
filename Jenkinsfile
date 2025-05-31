pipeline {
    agent any

    tools {
        maven 'MAVEN
        jdk 'JDK11'
    }

    environment {
        DOCKERHUB_USER = 'juanito0702'
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
                        dev: ['dev', '-dev']
                    ]
                    def config = profileConfig.get(env.BRANCH_NAME, ['dev', '-dev'])

                    env.SPRING_PROFILES_ACTIVE = config[0]
                    env.IMAGE_TAG = config[0]
                    env.DEPLOYMENT_SUFFIX = config[1]

                    env.IS_MASTER = env.BRANCH_NAME == 'master' ? 'true' : 'false'
                    env.IS_STAGE = env.BRANCH_NAME == 'stage' ? 'true' : 'false'
                    env.IS_DEV = env.BRANCH_NAME == 'dev' ? 'true' : 'false'
                    env.IS_FEATURE = env.BRANCH_NAME.startsWith('feature/') ? 'true' : 'false'

                    echo "Spring profile: ${env.SPRING_PROFILES_ACTIVE}"
                    echo "Image tag: ${env.IMAGE_TAG}"
                    echo "Deployment suffix: ${env.DEPLOYMENT_SUFFIX}"
                    echo "Flags: IS_MASTER=${env.IS_MASTER}, IS_STAGE=${env.IS_STAGE}, IS_DEV=${env.IS_DEV}, IS_FEATURE=${env.IS_FEATURE}"
                }
            }
        }

        stage('Checkout') {
            steps {
                git branch: "${env.BRANCH_NAME}", url: 'https://github.com/Juan181803/ecommerce-microservice-backend-app5'
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
                    branch 'dev'
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
                    branch 'dev'
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
                    branch 'dev'
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
            when { branch 'dev' }
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

        stage('Start containers for load and stress testing') {
            when { branch 'stage' }
            steps {
                script {
                    sh '''
                    docker network create ecommerce-test || true

                    docker run -d --name zipkin-container --network ecommerce-test -p 9411:9411 openzipkin/zipkin

                    docker run -d --name service-discovery-container --network ecommerce-test -p 8761:8761 \\
                    -e SPRING_PROFILES_ACTIVE=stage \\
                    -e SPRING_ZIPKIN_BASE_URL=http://zipkin-container:9411 \\
                    ${DOCKERHUB_USER}/service-discovery:${IMAGE_TAG}

                    until curl -s http://localhost:8761/actuator/health | grep '"status":"UP"' > /dev/null; do
                        echo "Waiting for service discovery to be ready..."
                        sleep 10
                    done

                    docker run -d --name cloud-config-container --network ecommerce-test -p 9296:9296 \\
                    -e SPRING_PROFILES_ACTIVE=stage \\
                    -e SPRING_ZIPKIN_BASE_URL=http://zipkin-container:9411 \\
                    -e EUREKA_CLIENT_SERVICEURL_DEFAULTZONE=http://service-discovery-container:8761/eureka/ \\
                    -e EUREKA_INSTANCE=cloud-config-container \\
                    ${DOCKERHUB_USER}/cloud-config:${IMAGE_TAG}

                    until curl -s http://localhost:9296/actuator/health | grep '"status":"UP"' > /dev/null; do
                        echo "Waiting for cloud config to be ready..."
                        sleep 10
                    done

                    docker run -d --name order-service-container --network ecommerce-test -p 8300:8300 \\
                    -e SPRING_PROFILES_ACTIVE=stage \\
                    -e SPRING_ZIPKIN_BASE_URL=http://zipkin-container:9411 \\
                    -e SPRING_CONFIG_IMPORT=optional:configserver:http://cloud-config-container:9296 \\
                    -e EUREKA_CLIENT_SERVICE_URL_DEFAULTZONE=http://service-discovery-container:8761/eureka \\
                    -e EUREKA_INSTANCE=order-service-container \\
                    ${DOCKERHUB_USER}/order-service:${IMAGE_TAG}

                    until [ "$(curl -s http://localhost:8300/order-service/actuator/health | jq -r '.status')" = "UP" ]; do
                        echo "Waiting for order service to be ready..."
                        sleep 10
                    done

                    docker run -d --name payment-service-container --network ecommerce-test -p 8400:8400 \\
                    -e SPRING_PROFILES_ACTIVE=stage \\
                    -e SPRING_ZIPKIN_BASE_URL=http://zipkin-container:9411 \\
                    -e SPRING_CONFIG_IMPORT=optional:configserver:http://cloud-config-container:9296 \\
                    -e EUREKA_CLIENT_SERVICE_URL_DEFAULTZONE=http://service-discovery-container:8761/eureka \\
                    -e EUREKA_INSTANCE=payment-service-container \\
                    ${DOCKERHUB_USER}/payment-service:${IMAGE_TAG}

                    until [ "$(curl -s http://localhost:8400/payment-service/actuator/health | jq -r '.status')" = "UP" ]; do
                        echo "Waiting for payment service to be ready..."
                        sleep 10
                    done

                    docker run -d --name favourite-service-container --network ecommerce-test -p 8800:8800 \\
                    -e SPRING_PROFILES_ACTIVE=stage \\
                    -e SPRING_ZIPKIN_BASE_URL=http://zipkin-container:9411 \\
                    -e SPRING_CONFIG_IMPORT=optional:configserver:http://cloud-config-container:9296 \\
                    -e EUREKA_CLIENT_SERVICE_URL_DEFAULTZONE=http://service-discovery-container:8761/eureka \\
                    -e EUREKA_INSTANCE=favourite-service-container \\
                    ${DOCKERHUB_USER}/favourite-service:${IMAGE_TAG}

                    until [ "$(curl -s http://localhost:8800/favourite-service/actuator/health | jq -r '.status')" = "UP" ]; do
                        echo "Waiting for favourite service to be ready..."
                        sleep 10
                    done
                    '''
                }
            }
        }

        stage('Run Load Tests with Locust') {
            when { branch 'stage' }
            steps {
                script {
                    sh '''
                    mkdir -p locust-reports

                    docker run --rm --network ecommerce-test \\
                    -v $PWD/locust-reports:/mnt/locust \\
                    ${DOCKERHUB_USER}/locust:${IMAGE_TAG} \\
                    -f test/order-service/locustfile.py \\
                    --host http://order-service-container:8300 \\
                    --headless -u 10 -r 2 -t 1m \\
                    --only-summary \\
                    --html /mnt/locust/order-service-report.html

                    docker run --rm --network ecommerce-test \\
                    -v $PWD/locust-reports:/mnt/locust \\
                    ${DOCKERHUB_USER}/locust:${IMAGE_TAG} \\
                    -f test/payment-service/locustfile.py \\
                    --host http://payment-service-container:8400 \\
                    --headless -u 10 -r 1 -t 1m \\
                    --only-summary \\
                    --html /mnt/locust/payment-service-report.html

                    docker run --rm --network ecommerce-test \\
                    -v $PWD/locust-reports:/mnt/locust \\
                    ${DOCKERHUB_USER}/locust:${IMAGE_TAG} \\
                    -f test/favourite-service/locustfile.py \\
                    --host http://favourite-service-container:8800 \\
                    --headless -u 10 -r 2 -t 1m \\
                    --only-summary \\
                    --html /mnt/locust/favourite-service-report.html
                    '''
                }
            }
        }

        stage('Run Stress Tests with Locust') {
            when { branch 'stage' }
            steps {
                script {
                    sh '''
                    docker run --rm --network ecommerce-test \\
                    -v $PWD/locust-reports:/mnt/locust \\
                    ${DOCKERHUB_USER}/locust:${IMAGE_TAG} \\
                    -f test/order-service/locustfile.py \\
                    --host http://order-service-container:8300 \\
                    --headless -u 50 -r 5 -t 1m \\
                    --only-summary \\
                    --html /mnt/locust/order-service-stress-report.html

                    docker run --rm --network ecommerce-test \\
                    -v $PWD/locust-reports:/mnt/locust \\
                    ${DOCKERHUB_USER}/locust:${IMAGE_TAG} \\
                    -f test/payment-service/locustfile.py \\
                    --host http://payment-service-container:8400 \\
                    --headless -u 50 -r 5 -t 1m \\
                    --only-summary \\
                    --html /mnt/locust/payment-service-stress-report.html

                    docker run --rm --network ecommerce-test \\
                    -v $PWD/locust-reports:/mnt/locust \\
                    ${DOCKERHUB_USER}/locust:${IMAGE_TAG} \\
                    -f test/favourite-service/locustfile.py \\
                    --host http://favourite-service-container:8800 \\
                    --headless -u 50 -r 5 -t 1m \\
                    --only-summary \\
                    --html /mnt/locust/favourite-service-stress-report.html

                    echo "✅ Pruebas de estrés completadas"
                    '''
                }
            }
        }

        stage('Stop and Remove Containers') {
            when { branch 'stage' }
            steps {
                script {
                    sh '''                    
                    docker rm -f locust || true
                    docker rm -f favourite-service-container || true
                    docker rm -f user-service-container || true
                    docker rm -f shipping-service-container || true
                    docker rm -f product-service-container || true
                    docker rm -f payment-service-container || true
                    docker rm -f order-service-container || true
                    docker rm -f cloud-config-container || true
                    docker rm -f service-discovery-container || true
                    docker rm -f zipkin-container || true

                    docker network rm ecommerce-test || true
                    '''
                }
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
                    publishHTML([
                        reportDir: 'locust-reports',
                        reportFiles: 'order-service-report.html, payment-service-report.html, favourite-service-report.html, order-service-stress-report.html, payment-service-stress-report.html, favourite-service-stress-report.html',
                        reportName: 'Locust Stress Test Reports',
                        keepAll: true
                    ])
                } else if (env.BRANCH_NAME == 'dev') {
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