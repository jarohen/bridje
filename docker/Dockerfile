FROM oracle/graalvm-ce:20.2.0-java11
MAINTAINER James Henderson <james@jarohen.me.uk>

ENTRYPOINT ["./bin/brj"]
WORKDIR /opt/graalvm-ce-java11-20.2.0

ADD languages/brj/ languages/brj/
ADD lib/brj/ lib/brj/
RUN ln -s ../languages/brj/bin/brj bin/brj
