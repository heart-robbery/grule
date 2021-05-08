FROM docker.io/gradle:6.8
ENV TZ Asia/Shanghai

RUN mkdir -p /srv/rule

ADD bin /srv/rule/bin
ADD conf /srv/rule/conf
ADD src /srv/rule/src
ADD build.gradle /srv/rule/build.gradle
ADD start.sh /srv/rule/start.sh

WORKDIR /srv/rule/

RUN gradle deps

ENTRYPOINT exec sh start.sh $JAVA_OPTS