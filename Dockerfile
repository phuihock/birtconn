FROM phuihock/ubuntu:14.04.2-u20150708
MAINTAINER Chang Phui-Hock <phuihock@codekaki.com>
ENV DEBIAN_FRONTEND=noninteractive JETTY_VER=jetty-distribution-9.2.10.v20150310
EXPOSE 8080

RUN echo 'Asia/Kuala_Lumpur' > /etc/timezone && dpkg-reconfigure tzdata
RUN apt-get install -y openjdk-7-jre-headless;\
 apt-get clean
ADD var/$JETTY_VER.tar.gz /opt

RUN useradd -m -d /opt/birt -s /bin/bash -G www-data codekaki
WORKDIR /opt/birt
COPY . .
COPY target/birt jetty/webapps/birt

VOLUME /opt/birt
VOLUME /var/opt/birt/reports

RUN chown -R codekaki:codekaki /opt/birt
ENTRYPOINT java -jar /opt/$JETTY_VER/start.jar jetty.base=jetty
