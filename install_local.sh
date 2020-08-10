#!/bin/bash
set -e

PROJECT_ROOT=$(git rev-parse --show-toplevel)

echo "Building es-hadoop jars with gradlew..."
sh ${PROJECT_ROOT}/gradlew

# JAR and pom file 
JAR="${PROJECT_ROOT}/build/distributions/elasticsearch-hadoop-6.8.12-SNAPSHOT/dist/elasticsearch-spark-20_2.12-6.8.12-SNAPSHOT.jar"

#   These should match the existing maven dependency
# for the artifact you're pulling from repo
GROUP_ID="org.elasticsearch"
ARTIFACT_ID="elasticsearch-spark-20_2.12"
VERSION="6.8.12-SNAPSHOT"

echo "INSTALLING JAR FILE: ${JAR}"
echo "    [group].[artifact]-[version]: ${GROUP_ID}.${ARTIFACT_ID}-${VERSION}"
echo "INSTALLING POM FILE: ${POM}"

mvn install:install-file \
    -Dfile=${JAR} \
    -DgroupId=${GROUP_ID} \
    -DartifactId=${ARTIFACT_ID} \
    -Dversion=${VERSION} \
    -Dpackaging=jar