FROM buildpack-deps:trusty
MAINTAINER Chang Phui-Hock <phuihock@codekaki.com>
ENV DEBIAN_FRONTEND=noninteractive JAVA_VER=jdk-8u60-linux-x64 JETTY_VER=9.2.19.v20160908
ENV PATH=/opt/jdk1.8.0_60/bin:$PATH
ENV JETTY_START_JAR=/opt/jetty/start.jar
RUN echo 'UTC' > /etc/timezone && dpkg-reconfigure tzdata

RUN apt-get update && apt-get install -y --force-yes --no-install-recommends openjdk-7-jdk &&\
 mkdir /opt/jetty &&\
 curl -o - http://repo1.maven.org/maven2/org/eclipse/jetty/jetty-distribution/${JETTY_VER}/jetty-distribution-${JETTY_VER}.tar.gz | tar xzf - -C /opt/jetty --strip 1
 
WORKDIR /opt/main

COPY entrypoint.sh entrypoint.sh
COPY jetty jetty
COPY target/birt.war jetty/webapps/birt.war

EXPOSE 8080

ENTRYPOINT ["/opt/main/entrypoint.sh"]
