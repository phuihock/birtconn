FROM buildpack-deps:trusty
MAINTAINER Chang Phui-Hock <phuihock@codekaki.com>
ENV DEBIAN_FRONTEND=noninteractive JAVA_VER=jdk-8u60-linux-x64 JETTY_VER=jetty-distribution-9.2.10.v20150310
ENV PATH=/opt/jdk1.8.0_60/bin:$PATH
ENV JETTY_START_JAR=/opt/${JETTY_VER}/start.jar
RUN echo 'UTC' > /etc/timezone && dpkg-reconfigure tzdata

# Download ${JAVA_VER}.tar.gz from http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html into var/ directory
ADD var/${JAVA_VER}.tar.gz  /opt

# Download ${JETTY_VER}.tar.gz from http://download.eclipse.org/jetty/stable-9/dist/
ADD var/${JETTY_VER}.tar.gz /opt

WORKDIR /opt/birt
COPY entrypoint.sh entrypoint.sh
COPY jetty jetty
COPY target/birt jetty/webapps/birt
COPY reports reports

VOLUME ["/opt/birt/reports"]
EXPOSE 8080

ENTRYPOINT ["/opt/birt/entrypoint.sh"]
