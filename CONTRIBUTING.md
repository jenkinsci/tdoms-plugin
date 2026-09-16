# Contributing

## Development requirements

- Java 17 or newer
- Maven 3.9 or newer
- A Jenkins development environment
- Access to a test IBM i system for integration testing

## Build and test

Run the full verification build before opening a pull request:

```powershell
mvn -B clean verify
```

Keep changes focused, add tests for behavior changes, and do not commit generated files from `target/` or `work/`.

## Pull requests

Describe the user-visible behavior, test coverage, and any IBM i or Jenkins compatibility considerations. Do not include credentials, server details, or production logs.
