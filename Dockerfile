# ===== stage 1 =====
FROM eclipse-temurin:11-jdk as build-pathway-exchange

ENV ANT_VERSION=1.8.0 \
    ANT_HOME=/opt/ant

WORKDIR /tmp

# download apache ant, extract to opt, and add exec to path
RUN wget --no-check-certificate --no-cookies http://archive.apache.org/dist/ant/binaries/apache-ant-${ANT_VERSION}-bin.tar.gz && \
    wget --no-check-certificate --no-cookies http://archive.apache.org/dist/ant/binaries/apache-ant-${ANT_VERSION}-bin.tar.gz.md5 && \  
    echo "$(cat apache-ant-${ANT_VERSION}-bin.tar.gz.md5) apache-ant-${ANT_VERSION}-bin.tar.gz" | md5sum && \ 
    tar -zvxf apache-ant-${ANT_VERSION}-bin.tar.gz -C /opt/ && \ 
    ln -s /opt/apache-ant-${ANT_VERSION} /opt/ant && \ 
    rm -f apache-ant-${ANT_VERSION}-bin.tar.gz && \ 
    rm -f apache-ant-${ANT_VERSION}-bin.tar.gz.md5 && \ 
    update-alternatives --install "/usr/bin/ant" "ant" "/opt/ant/bin/ant" 1 && \
    update-alternatives --set "ant" "/opt/ant/bin/ant" 

ENV URL="https://github.com/reactome/Pathway-Exchange.git" \
    DIRECTORY="Pathway-Exchange" \
    ANT_FILE="PathwayExchangeJar.xml" \
    OUTPUT="pathwayExchange.jar" \
    START_DIR=.

WORKDIR /gitroot/$DIRECTORY

# clone "Pathway-Exchange" repo, and build with ant
RUN git clone $URL . && \
    ant -DdestDir="$START_DIR" -buildfile ant/$ANT_FILE


# ===== stage 2 =====
FROM maven:3.6.3-openjdk-11 as build-download-directory

ENV GROUP_ID="org.reactome.pathway-exchange" \
    ARTIFACT_ID="pathwayExchange" \
    DIRECTORY="Pathway-Exchange" \
    OUTPUT="pathwayExchange.jar" \
    VERSION=1.0.1

WORKDIR /gitroot/reactome-release-directory

# copy "release-download-directory" from local
COPY . .

# copy jar build artifact from stage 1
COPY --from=build-pathway-exchange /gitroot/$DIRECTORY/$OUTPUT /tmp/$OUTPUT

# install jar build artifact from stage 1, build "release-download-directory", and uncompress artifacts
RUN mvn install:install-file -Dfile="/tmp/$OUTPUT" -DgroupId=$GROUP_ID -DartifactId=$ARTIFACT_ID -Dversion=$VERSION -Dpackaging=jar && \
    mvn clean package -DskipTests


# ===== stage 3 =====
FROM eclipse-temurin:11-jre-focal

WORKDIR /opt/release-download-directory

COPY --from=build-download-directory /gitroot/reactome-release-directory/target/download-directory.jar target/

COPY --from=build-download-directory /gitroot/reactome-release-directory/src/main/resources/ src/main/resources/

RUN apt-get update && \
    apt-get install -y mariadb-client && \
    rm -rf /var/lib/apt/lists/*
