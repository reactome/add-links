pipeline {
    agent any

    stages {
        stage('Setup: Build jar file') {
            steps {
		        script {
                    dir ('add-links') {
			            withCredentials([file(credentialsId: 'Config', variable: 'FILE')]) {
                  	        sh 'touch thing.txt'
			            }
                    }
          	    }
            }
        }
    }
}
