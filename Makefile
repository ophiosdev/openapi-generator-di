.PHONY: default
default: build

.PHONY: java-clean
java-clean:
	docker run --pull always -t --rm -v maven-repo:/root/.m2 -v "${PWD}":/work -w /work maven:3-eclipse-temurin-11 mvn clean

.PHONY: docker-clean
docker-clean:
	docker rmi -f openapi-generator-di:dev

.PHONY: clean
clean: java-clean docker-clean
	rm -rf tests/.generated*

.PHONY: build
build:
	docker run --pull always -t --rm -v maven-repo:/root/.m2 -v "${PWD}:${PWD}" -w "${PWD}" maven:3-eclipse-temurin-11 mvn package -Dmaven.test.skip

.PHONY: test
test:
	docker run --pull always -t --rm -v maven-repo:/root/.m2 -v "${PWD}":/work -w /work maven:3-eclipse-temurin-11 mvn test

.PHONY: docker
docker: build
	DOCKER_BUILDKIT=1 docker build -t openapi-generator-di:dev -f docker/Dockerfile .
