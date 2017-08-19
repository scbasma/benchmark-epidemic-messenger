FROM frolvlad/alpine-oraclejdk8:full
MAINTAINER Gardner Vickers <gardner@vickers.me>


RUN apk add --update libgcc libstdc++ bash wget tar

RUN wget --no-check-certificate https://github.com/just-containers/s6-overlay/releases/download/v1.11.0.1/s6-overlay-amd64.tar.gz

#ADD https://github.com/just-containers/s6-overlay/releases/download/v1.11.0.1/s6-overlay-amd64.tar.gz/ /tmp/

RUN tar xzf /s6-overlay-amd64.tar.gz -C /
RUN rm /s6-overlay-amd64.tar.gz
ADD scripts/run_media_driver.sh /etc/services.d/media_driver/run
ADD scripts/finish_media_driver.sh /etc/s6/media_driver/finish

ADD scripts/run_messenger.sh /opt/run_messenger.sh
ADD target/benchmark-epidemic.jar /opt/benchmark-epidemic.jar

ENTRYPOINT ["/init"]
EXPOSE 3196 3197 3198 40199
EXPOSE 40199
EXPOSE 40199/udp

CMD ["opt/run_messenger.sh"]
