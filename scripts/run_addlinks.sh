#! /bin/bash

# Make sure you have the AddLinks jar file (or a link to it) named "AddLinks.jar" in this directory, as well as the resources directory (or a link to it).

java -cp "$(pwd)/resources" \
	-Dconfig.location=$(pwd)/resources/addlinks.properties \
	-Dlog4j.configurationFile=$(pwd)/resources/log4j2.xml \
	-jar AddLinks.jar file://$(pwd)/resources/application-context.xml
