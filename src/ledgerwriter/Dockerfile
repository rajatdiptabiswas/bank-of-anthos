FROM maven:3.6.3-jdk-11-slim

WORKDIR /usr/share/ft

ADD entrypoint.sh ./entrypoint.sh
ADD src ./src
ADD pom.xml ./pom.xml

RUN chmod +x entrypoint.sh

ENTRYPOINT ["/usr/share/ft/entrypoint.sh"]