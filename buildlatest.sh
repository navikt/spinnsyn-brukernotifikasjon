#!/bin/bash
echo "Bygger spinnsyn-brukernotifikasjon for bruk i flex-docker-compose"
rm -rf ./build
./gradlew bootJar
docker build -t spinnsyn-brukernotifikasjon:latest .
