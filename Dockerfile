FROM phuihock/ubuntu:14.04.2_u150724.232741
MAINTAINER Chang Phui-Hock <phuihock@codekaki.com>
ENV DEBIAN_FRONTEND=noninteractive JAVA_VER=jdk-8u60-linux-x64 JETTY_VER=jetty-distribution-9.2.10.v20150310
ENV PATH=/opt/jdk1.8.0_60/bin:$PATH
EXPOSE 8080

RUN echo 'Asia/Kuala_Lumpur' > /etc/timezone && dpkg-reconfigure tzdata

# Download Jetty 9.2.10 from http://archive.eclipse.org/jetty/9.2.10.v20150310/dist/ into var/ directory
# wget http://archive.eclipse.org/jetty/9.2.10.v20150310/dist/jetty-distribution-9.2.10.v20150310.tar.gz -P var/
ADD var/$JAVA_VER.tar.gz  /opt

# Download JDK8 from http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html
# into var/ directory
ADD var/$JETTY_VER.tar.gz /opt
COPY jetty /opt/birt/jetty
COPY target/birt /opt/birt/jetty/webapps/birt
COPY reports /opt/birt/reports

RUN useradd -d /opt/birt -s /bin/bash codekaki
RUN chown -R codekaki:codekaki /opt/birt
USER codekaki
WORKDIR /opt/birt

ENTRYPOINT java -jar /opt/$JETTY_VER/start.jar jetty.base=jetty
