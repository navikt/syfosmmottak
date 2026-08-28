FROM gcr.io/distroless/java25-debian13@sha256:8ce26d023018ca2f11bf2530cd3a10a7fd8456c3142b5f9a7d6b135a1411c86a
WORKDIR /app
COPY build/install/*/lib /lib
ENV JAVA_OPTS="-Dlogback.configurationFile=logback.xml -Dcom.ibm.mq.cfg.TCP.ClntSndBuffSize=32768 -Dcom.ibm.mq.cfg.TCP.ClntRcvBuffSize=32768 -Dcom.ibm.msg.client.commonservices.log.outputName=System.out"
ENV TZ="Europe/Oslo"
EXPOSE 8080
USER nonroot
ENTRYPOINT ["java", "-cp", "/lib/*", "no.nav.syfo.ApplicationKt"]
