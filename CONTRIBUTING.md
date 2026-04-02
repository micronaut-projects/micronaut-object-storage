# Contributing Code or Documentation to Micronaut

Sign the [Contributor License Agreement (CLA)](https://cla-assistant.io/pgressa/micronaut-object-storage). This is required before any of your code or pull requests are accepted.

## Finding Issues to Work On

If you are interested in contributing to Micronaut and are looking for issues to work on, check the [help wanted](https://github.com/micronaut-projects/micronaut-object-storage/issues?q=is%3Aopen+is%3Aissue+label%3A%22status%3A+help+wanted%22) issues in this repository.

## JDK Setup

This repository currently targets the Java version declared in [`.sdkmanrc`](.sdkmanrc).

If you use SDKMAN, run:

```bash
sdk env install
sdk env
```

## IDE Setup

Micronaut Object Storage can be imported into IntelliJ IDEA by opening `settings.gradle.kts` from the repository root.

## Docker Setup

Some tests use Testcontainers-backed services, so Docker must be installed and available before running the full suite.

## Running Tests

Run targeted module tests while iterating, then finish with full repository verification before opening a pull request.

Examples:

```bash
./gradlew test
./gradlew :object-storage-aws:test
./gradlew :object-storage-azure:test
./gradlew check
```

## Building Documentation

The documentation sources are located at `src/main/docs/guide`.

To build the guide, run `./gradlew publishGuide` (or `./gradlew pG`), then open `build/docs/index.html`.

To also build the Javadocs, run `./gradlew docs`.

## Working on the Code Base

To publish a local snapshot for downstream testing, run:

```bash
./gradlew publishToMavenLocal
```

You can then reference the version specified with `projectVersion` in `gradle.properties` in a test project's `build.gradle` or `pom.xml`. If you use Gradle, add the `mavenLocal` repository:

```groovy
repositories {
    mavenLocal()
    mavenCentral()
}
```

## Creating a Pull Request

Once you are satisfied with your changes:

- Commit your changes in your local branch.
- Push your changes to your remote branch on GitHub.
- Open a [pull request](https://docs.github.com/github/collaborating-with-pull-requests/proposing-changes-to-your-work-with-pull-requests/creating-a-pull-request).

## Checkstyle

We want to keep the code clean, following good practices about organization, Javadoc, and style as much as possible.

Micronaut Object Storage uses [Checkstyle](https://checkstyle.sourceforge.io/) to make sure that the code follows those standards. The configuration is defined in `config/checkstyle/checkstyle.xml`. To execute Checkstyle, run:

```bash
./gradlew <module-name>:checkstyleMain
```

Before starting to contribute new code, we recommend that you install the IntelliJ [CheckStyle-IDEA](https://plugins.jetbrains.com/plugin/1065-checkstyle-idea) plugin and configure it to use Micronaut's Checkstyle configuration file.
