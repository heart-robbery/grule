FROM gradle:7.1.1-jdk8
ENV TZ Asia/Shanghai

RUN mkdir -p /srv/grule

ADD bin /srv/grule/bin
ADD conf /srv/grule/conf
ADD src /srv/grule/src
ADD build.gradle /srv/grule/build.gradle
ADD start.sh /srv/grule/start.sh

WORKDIR /srv/grule/

RUN gradle deps

ENTRYPOINT exec sh start.sh $JAVA_OPTS