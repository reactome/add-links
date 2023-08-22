docker-pull:
	docker pull maven:3.8.6-openjdk-11-slim

.PHONY: build-image
build-image: docker-pull \
             $(call print-help,build, "Build the docker image.")
	docker build -t reactome/add-links:latest .

.PHONY: run-image
run-image: $(call print-help,run, "Run the docker image.")
	docker run reactome/add-links:latest -v $(pwd)/output:/graphdb --net=host
