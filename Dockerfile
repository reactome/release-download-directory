FROM openjdk:8 as pathway-exchange-image

ENV ANT_VERSION=1.8.0
ENV ANT_HOME=/opt/ant

# change to tmp folder
WORKDIR /tmp

# Download and extract apache ant to opt folder
RUN wget --no-check-certificate --no-cookies http://archive.apache.org/dist/ant/binaries/apache-ant-${ANT_VERSION}-bin.tar.gz 
RUN wget --no-check-certificate --no-cookies http://archive.apache.org/dist/ant/binaries/apache-ant-${ANT_VERSION}-bin.tar.gz.md5
RUN echo "$(cat apache-ant-${ANT_VERSION}-bin.tar.gz.md5) apache-ant-${ANT_VERSION}-bin.tar.gz" | md5sum
RUN tar -zvxf apache-ant-${ANT_VERSION}-bin.tar.gz -C /opt/
RUN ln -s /opt/apache-ant-${ANT_VERSION} /opt/ant
RUN rm -f apache-ant-${ANT_VERSION}-bin.tar.gz
RUN rm -f apache-ant-${ANT_VERSION}-bin.tar.gz.md5

# add executables to path
RUN update-alternatives --install "/usr/bin/ant" "ant" "/opt/ant/bin/ant" 1 && \
    update-alternatives --set "ant" "/opt/ant/bin/ant" 

WORKDIR ${ANT_HOME}

ENV URL="https://github.com/reactome/Pathway-Exchange.git"
ENV DIRECTORY="Pathway-Exchange"
ENV ANT_FILE="PathwayExchangeJar.xml"
ENV OUTPUT="pathwayExchange.jar"
ENV START_DIR=.


RUN mkdir /gitroot

WORKDIR /gitroot

RUN git clone $URL

WORKDIR /gitroot/$DIRECTORY

RUN ant -DdestDir="$START_DIR" -buildfile ant/$ANT_FILE

FROM maven:3.6.3-openjdk-8

WORKDIR  /gitroot/reactome-release-directory

COPY . .

COPY --from=pathway-exchange-image /gitroot/Pathway-Exchange/$OUTPUT /tmp/$OUTPUT

ENV GROUP_ID="org.reactome.pathway-exchange"
ENV ARTIFACT_ID="pathwayExchange"
ENV OUTPUT="pathwayExchange.jar"
ENV VERSION=1.0.1


RUN mvn install:install-file -Dfile="/tmp/$OUTPUT" \
    -DgroupId=$GROUP_ID -DartifactId=$ARTIFACT_ID -Dversion=$VERSION -Dpackaging=jar

RUN mvn clean package -DskipTests

RUN cd target && mkdir lib && cd lib && jar -xvf ../download-directory.jar

RUN apt update;
RUN apt install apt-utils -y
RUN apt install tree -y

#install dependencies for protege perl program
RUN apt install make -y
RUN apt install cpanminus -y
RUN apt install libssl-dev zlib1g-dev -y
RUN apt install libz-dev -y

RUN cpanm CGI


RUN wget https://cpan.metacpan.org/authors/id/C/CJ/CJFIELDS/BioPerl-1.7.8.tar.gz
RUN tar -xzvf BioPerl-1.7.8.tar.gz
RUN mv BioPerl-1.7.8/lib/Bio .
RUN rm BioPerl-1.7.8 -r
RUN rm BioPerl-1.7.8.tar.gz

RUN cpanm Log::Log4perl

RUN apt install libdbi-perl -y

RUN apt-get install build-essential -y
RUN wget https://github.com/libgd/libgd/releases/download/gd-2.2.5/libgd-2.2.5.tar.gz
RUN tar zxvf libgd-2.2.5.tar.gz
RUN cd libgd-2.2.5; ./configure; make; make install; make installcheck

RUN apt install libxml-simple-perl -y

RUN apt install pkg-config -y
RUN apt install libgd3 libgd-perl -y
RUN cpanm GD::Polygon --force

RUN cpanm WWW::SearchResult

RUN apt install mariadb-client -y
RUN apt install libdbd-mysql-perl -y

RUN git clone https://github.com/reactome/Release.git
ENV PERL5LIB=/gitroot/reactome-release-directory/Release/modules/

RUN mv /gitroot/reactome-release-directory/Secrets.pm  /gitroot/reactome-release-directory/Release/modules/GKB/

RUN cpanm Net::SSLeay
RUN cpanm IO::Socket::SSL
RUN cpanm XML::LibXML::Reader
RUN cpanm XML::Twig
RUN cpanm XML::DOM
RUN cpanm PadWalker # said needed for test-memory-cycle
RUN cpanm Test::Memory::Cycle
RUN cpanm Bio::Root::Root
RUN cpanm XML::Parser::PerlSAX
RUN cpanm Graph::Directed XML::LibXML
RUN cpanm Set::Object
