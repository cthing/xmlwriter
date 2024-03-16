# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [unreleased]

### Added

- [Dependency analysis Gradle plugin](https://github.com/autonomousapps/dependency-analysis-gradle-plugin)
- The `check` task now depends on the `buildHealth` task and will fail the build on health violations

### Changed

- Changed JSR-305 dependency from `implementation` to `api`
- Upgraded `org.gradle.toolchains.foojay-resolver-convention`

## [2.0.1] - 2023-12-23

### Added

- Improved `null` checking and readability using the [cthing-annotations](https://github.com/cthing/cthing-annotations) library.

## [2.0.0] - 2023-09-19

### Added

- First version published to Maven Central

## 1.0.0 - 2022-07-12

### Added

- First source code release

[unreleased]: https://github.com/cthing/xmlwriter/compare/2.0.1...HEAD
[2.0.1]: https://github.com/cthing/xmlwriter/releases/tag/2.0.1
[2.0.0]: https://github.com/cthing/xmlwriter/releases/tag/2.0.0
