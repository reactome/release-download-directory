.PHONY: build-image
build-image:  \
             $(call print-help,build, "Build the docker image.")
	docker build -t reactome/release-download-directory .
