Remove-Item *.jar -Force
mvn clean install -DskipTests
Copy-Item target\*.jar -Destination fluveny.jar

docker-compose -f docker-compose.exec.yml build
docker-compose -f docker-compose.exec.yml up