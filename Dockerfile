FROM ubuntu:14.04.2
MAINTAINER Chang Phui-Hock <phuihock@codekaki.com>
ENV DEBIAN_FRONTEND=noninteractive JETTY_VER=jetty-distribution-9.2.10.v20150310
EXPOSE 8080
VOLUME /var/opt/birt/reports

RUN echo 'Asia/Kuala_Lumpur' > /etc/timezone && dpkg-reconfigure tzdata
RUN apt-get update &&\
 apt-get install -y openjdk-7-jre-headless;\
 apt-get clean
ADD extras/birt-rs/var/$JETTY_VER.tar.gz /opt

RUN useradd -m -d /opt/birt -s /bin/bash -G www-data codekaki
USER codekaki
WORKDIR /opt/birt
COPY extras/birt-rs .
COPY extras/birt-rs/target/birt jetty/webapps/birt

ENTRYPOINT java -jar /opt/$JETTY_VER/start.jar jetty.base=jetty
