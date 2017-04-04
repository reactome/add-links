#! /bin/bash

java -cp "$(pwd)/resources" \
	-Dlog4j.configurationFile=$(pwd)/resources/log4j2.xml \
	-Dconfig.location=$(pwd)/resources/addlinks.properties \
	-jar AddLinks.jar file://$(pwd)/resources/application-context.xml
