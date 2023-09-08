[![Build status](https://github.com/navikt/syfosmmottak/workflows/Deploy%20to%20dev%20and%20prod/badge.svg)](https://github.com/navikt/syfosmmottak/workflows/Deploy%20to%20dev%20and%20prod/badge.svg)
# SYFOsmmottak
This project contains just the receiving part of a sykmelding2013 message

## Technologies used
* Kotlin
* Ktor
* Gradle
* Junit
* Jackson
* MQ
* Kafka

#### Requirements

* JDK 17

## FlowChart
This the high level flow of the application
```mermaid
  graph LR
        
      EPJ --- eMottak
      eMottak --- id1([SYFOSMMOTTAK.INPUT])
      id1([MQ queue: SYFOSMMOTTAK.INPUT]) --> syfosmmottak
      syfosmmottak --> B[\teamsykmelding.sykmelding-apprec/]
      syfosmmottak --> id2([SYFOSMMOTTAK.INPUT_BOQ])
      id2([SYFOSMMOTTAK.INPUT_BOQ]) --> id1([SYFOSMMOTTAK.INPUT])
      syfosmmottak <--> id3[(Database)]
      syfosmmottak --- Azure-AD
      syfosmmottak --- PDL
      syfosmmottak --- smtss
      syfosmmottak --- eMottak-subscription
      syfosmmottak --- syfohelsenettproxy
      syfosmmottak --- GCP-Bucket
      syfosmmottak --- syfosmregler
      syfosmmottak --- C[\teamsykmelding.ok-sykmelding/]
      syfosmmottak --- D[\teamsykmelding.manuell-behandling-sykmelding/]
      syfosmmottak --- E[\teamsykmelding.avvist-sykmelding/]
      syfosmmottak --- F[\teamsykmelding.sykmelding-behandlingsutfall/]
      syfosmmottak --- G[\teamsykmelding.sykmelding-manuell/]
      syfosmmottak --- H[\teamsykmelding.oppgave-produser-oppgave/];
```

## Getting started
### Getting github-package-registry packages NAV-IT
Some packages used in this repo is uploaded to the GitHub Package Registry which requires authentication. It can, for example, be solved like this in Gradle:
```
val githubUser: String by project
val githubPassword: String by project

repositories {
    maven {
        credentials {
            username = githubUser
            password = githubPassword
        }
        setUrl("https://maven.pkg.github.com/navikt/syfosmmottak")
    }
}
```
`githubUser` and `githubPassword` can be put into a separate file `~/.gradle/gradle.properties` with the following content:
   
```                                                     
githubUser=x-access-token
githubPassword=[token]
```

Replace `[token]` with a personal access token with scope `read:packages`.
See githubs guide [creating-a-personal-access-token](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/creating-a-personal-access-token) on
how to create a personal access token.

Alternatively, the variables can be configured via environment variables:

* `ORG_GRADLE_PROJECT_githubUser`
* `ORG_GRADLE_PROJECT_githubPassword`

or the command line:

``` bash
./gradlew -PgithubUser=x-access-token -PgithubPassword=[token]
```
#### Running locally
``` bash
./gradlew run
```

### Building the application
#### Compile and package application
To build locally and run the integration tests you can simply run
``` bash
./gradlew shadowJar
```
 or  on windows 
`gradlew.bat shadowJar`

### Upgrading the gradle wrapper
Find the newest version of gradle here: https://gradle.org/releases/ Then run this command:

``` bash
./gradlew wrapper --gradle-version $gradleVersjon
```

### Contact
This project is maintained by [navikt/teamsykmelding](CODEOWNERS)

Questions and/or feature requests? 
Please create an [issue](https://github.com/navikt/syfosmmottak/issues)

If you work in [@navikt](https://github.com/navikt) you can reach us at the Slack
channel [#team-sykmelding](https://nav-it.slack.com/archives/CMA3XV997)
