# ===== stage 1 =====
FROM openjdk:8 as pathway-exchange-image

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
FROM maven:3.6.3-openjdk-8

ENV GROUP_ID="org.reactome.pathway-exchange" \
    ARTIFACT_ID="pathwayExchange" \
    OUTPUT="pathwayExchange.jar" \
    VERSION=1.0.1

WORKDIR  /gitroot/reactome-release-directory

# copy "release-download-directory" from local
COPY . .

# copy jar build artifact from stage 1
COPY --from=pathway-exchange-image /gitroot/$DIRECTORY/$OUTPUT /tmp/$OUTPUT

# install jar build artifact from stage 1, build "release-download-directory", and uncompress artifacts
RUN mvn install:install-file -Dfile="/tmp/$OUTPUT" -DgroupId=$GROUP_ID -DartifactId=$ARTIFACT_ID -Dversion=$VERSION -Dpackaging=jar && \
    mvn clean package -DskipTests && \
    mkdir target/lib && \
    jar -xvf target/download-directory.jar -C target/lib

# install dependencies for protege perl program
RUN apt update && apt install -y \
    apt-utils \
    tree \
    make \
    build-essential \
    cpanminus \
    libssl-dev \ 
    zlib1g-dev \
    libz-dev 

RUN cpanm CGI 

RUN wget https://cpan.metacpan.org/authors/id/C/CJ/CJFIELDS/BioPerl-1.7.8.tar.gz && \
    tar -xzvf BioPerl-1.7.8.tar.gz && \
    mv BioPerl-1.7.8/lib/Bio . && \
    rm BioPerl-1.7.8 -r && \
    rm BioPerl-1.7.8.tar.gz

RUN cpanm Log::Log4perl

RUN apt install -y libdbi-perl

RUN wget https://github.com/libgd/libgd/releases/download/gd-2.2.5/libgd-2.2.5.tar.gz && \
    tar zxvf libgd-2.2.5.tar.gz && \
    cd libgd-2.2.5 && \
    ./configure && \
    make && \
    make install && \
    make installcheck

RUN apt install -y \
    libxml-simple-perl \
    pkg-config \
    libgd3 \
    libgd-perl

RUN cpanm GD::Polygon --force && \
    cpanm WWW::SearchResult

RUN apt install -y \
    mariadb-client \
    libdbd-mysql-perl

ENV PERL5LIB=/gitroot/reactome-release-directory/Release/modules/

RUN git clone https://github.com/reactome/Release.git

RUN mv /gitroot/reactome-release-directory/Secrets.pm  /gitroot/reactome-release-directory/Release/modules/GKB/

# PadWalker needed for test-memory-cycle
RUN cpanm Net::SSLeay && \
    cpanm IO::Socket::SSL && \
    cpanm XML::LibXML::Reader && \
    cpanm XML::Twig && \
    cpanm XML::DOM && \
    cpanm PadWalker && \
    cpanm Test::Memory::Cycle && \
    cpanm Bio::Root::Root && \
    cpanm XML::Parser::PerlSAX && \
    cpanm Graph::Directed XML::LibXML && \
    cpanm Set::Object
