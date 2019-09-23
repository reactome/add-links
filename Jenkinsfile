pipeline{
    agent any

    stages{
        stage('Setup: Build jar file'){
            steps{
		        script{
                    dir('add-links'){
			            withCredentials([file(credentialsId: 'Config', variable: 'FILE')]){
                  	        
					    sh 'mvn clean package -DskipTests'
			            }
                    }
          	    }
            }
        }
		stage('Main: Download AddLinks files'){
			steps{
				script{
					dir('add-links'){
						withCredentials([file(credentialsId: 'Config', variable: 'FILE')]){
							sh 'cp $FILE src/main/resources/auth.properties'
							sh 'ln -sf src/main/resources resources'
							sh 'rm src/main/resources/db.properties'
					    		sh 'java -cp "$(pwd)/resources" -Dconfig.location=$(pwd)/resources/addlinks.properties -Dlog4j.configurationFile=$(pwd)/resources/log4j2.xml -jar target/AddLinks-1.1.4-SHADED.jar file://$(pwd)/resources/application-context.xml'
							sh 'rm src/main/resources/auth.properties'
						}
					}
				}
			}
		}
    }
}
