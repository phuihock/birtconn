FROM ubuntu:14.04.2
MAINTAINER Chang Phui-Hock <phuihock@codekaki.com>
ENV DEBIAN_FRONTEND=noninteractive JETTY_VER=jetty-distribution-9.2.10.v20150310
EXPOSE 8080
VOLUME /var/opt/birt/reports

RUN useradd -m -d /opt/birt -s /bin/bash -G www-data codekaki
ADD extras/birt-rs/var/$JETTY_VER.tar.gz /opt

WORKDIR /opt/birt
COPY extras/birt-rs .
RUN bash migrations/0001

USER codekaki
ENTRYPOINT java -jar /opt/$JETTY_VER/start.jar jetty.base=jetty
