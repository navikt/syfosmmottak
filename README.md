![https://github.com/navikt/syfosmmottak/workflows/Deploy%20to%20dev%20and%20prod/badge.svg](Build status)
# SYFO sm mottak
This project contains just the receving a sykmelding2013 message

## Technologies used
* Kotlin
* Ktor
* Gradle
* Spek
* Jackson

#### Requirements

* JDK 11

## Getting started
#### Running locally
`./gradlew run`

### Building the application
#### Compile and package application
To build locally and run the integration tests you can simply run `./gradlew shadowJar` or  on windows 
`gradlew.bat shadowJar`

#### Creating a docker image
Creating a docker image should be as simple as `docker build -t syfosmmottak .`

#### Running a docker image
`docker run --rm -it -p 8080:8080 syfosmmottak`

## Contact us
### Code/project related questions can be sent to
* Joakim Kartveit, `joakim.kartveit@nav.no`

### For NAV employees
We are available at the Slack channel #team-sykmelding
