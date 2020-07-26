FROM docker.io/gradle:6.5
ENV TZ Asia/Shanghai

RUN mkdir -p /srv/gy

ADD bin /srv/gy/bin
ADD conf /srv/gy/conf
ADD src /srv/gy/src
ADD .gitignore /srv/gy/.gitignore
ADD build.gradle /srv/gy/build.gradle
ADD start.sh /srv/gy/start.sh

WORKDIR /srv/gy/

RUN gradle deps

ENTRYPOINT exec sh start.sh $JAVA_OPTS