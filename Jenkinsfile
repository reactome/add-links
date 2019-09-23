pipeline {
    agent any

    stages {
        stage('Setup: Build jar file') {
            steps {
		        script {
                    dir ('add-links') {
			            withCredentials([file(credentialsId: 'Config', variable: 'FILE')]) {
                  	        sh 'rm src/main/resources/db.properties'
							sh 'cp $FILE src/main/resources/auth.properties'
							sh 'ln -sf src/main/resources resources'
							sh 'mvn clean package -DskipTests'
			            }
                    }
          	    }
            }
        }
    }
}
