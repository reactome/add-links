#! /bin/bash

java -cp ./resources/ -Dlog4j.configurationFile=resources/log4j2.xml -jar AddLinks.jar ./resources/application-context.xml
