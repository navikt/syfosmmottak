FROM navikt/java:10
COPY build/libs/syfosmmottak-*-all.jar app.jar
ENV JAVA_OPTS="'-Dlogback.configurationFile=logback-remote.xml'"
