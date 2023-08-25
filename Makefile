docker-pull:
	docker pull openjdk:8
        docker pull maven:3.6.3-openjdk-8

.PHONY: build-image
build-image: docker-pull \
             $(call print-help,build, "Build the docker image.")
	docker build -t reactome/release-download-directory:latest .
