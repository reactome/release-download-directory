#!/bin/bash
set -e

DIR=$(dirname "$(readlink -f "$0")") # Directory of the script -- allows the script to invoked from anywhere
cd $DIR

## Make sure the repo is up to date
git pull

## Execute script to build and locally install pathwayExchange.jar
./build_pathway_exchange.sh

## Generate the jar file and run the Download Directory program
mvn clean package -DskipTests
unzip -o target/download-directory-distr.zip
java -Xmx4096m -javaagent:download-directory/lib/spring-instrument-4.2.4.RELEASE.jar -jar download-directory/download-directory.jar
