#!/bin/bash

rm -f *.jar
mvn clean install -DskipTests
cp target/*.jar fluveny.jar

docker-compose -f docker-compose.exec.yml build
docker-compose -f docker-compose.exec.yml up