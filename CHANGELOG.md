# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [unreleased]

## [3.0.0] - 2024-04-14

### Added

- XML characters outside the [Unicode Basic Multilingual Plane](https://en.wikipedia.org/wiki/Plane_(Unicode))
  (i.e. 0x10000-0x10FFFF) are now supported and escaped
- The `setEscapeNonAscii` and `setUseDecimal` methods have been added to control escaping behavior
- [Dependency analysis Gradle plugin](https://github.com/autonomousapps/dependency-analysis-gradle-plugin)
- The `check` task now depends on the `buildHealth` task and will fail the build on health violations such as
  unused dependencies
- New dependency on the [escapers](https://central.sonatype.com/artifact/org.cthing/escapers) library

### Changed

- The escape behavior has changed. By default, characters outside the ASCII range are no longer escaped. To
  escape these characters, call `setEscapeNonAscii(true)`.
- By default, numeric character entities are now written in hexadecimal (e.g. `&#xA9;`) rather than decimal.
  To write numeric entities in decimal, call `setUseDecimal(true)`.
- Invalid XML characters are no longer written. In previous versions, they were written in decimal with the prefix
  "ctrl-".
- Changed JSR-305 dependency from `implementation` to `api`

### Removed

- The `setEscaping` method has been removed. Use the `setEscapeNonAscii` and `setUseDecimal` methods.

## [2.0.1] - 2023-12-23

### Added

- Improved `null` checking and readability using the [cthing-annotations](https://github.com/cthing/cthing-annotations) library.

## [2.0.0] - 2023-09-19

### Added

- First version published to Maven Central

## 1.0.0 - 2022-07-12

### Added

- First source code release

[unreleased]: https://github.com/cthing/xmlwriter/compare/3.0.0...HEAD
[3.0.0]: https://github.com/cthing/xmlwriter/releases/tag/3.0.0
[2.0.1]: https://github.com/cthing/xmlwriter/releases/tag/2.0.1
[2.0.0]: https://github.com/cthing/xmlwriter/releases/tag/2.0.0
