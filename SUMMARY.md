# TD/OMS Jenkins Integration Plugin (`td-oms-jenkins-plugin`)

## Overview

The `td-oms-jenkins-plugin` provides dedicated Jenkins Pipeline DSL steps (`tdOmsChangedFiles` and `bldIfsOms`) designed specifically for TD/OMS on IBM i. The plugin builds directly on top of the official [IBM i Pipeline Steps Plugin (`ibmi-steps-plugin`)](https://github.com/jenkinsci/ibmi-steps-plugin), eliminating complex custom Groovy scripts and removing the need for standalone JT400/SSH transport management.

---

## Key Features & Pipeline Steps

### 1. `tdOmsChangedFiles`
- **Purpose**: Detects changed source files between the active Git branch and a target comparison branch (`origin/master`).
- **Parameters**: `compareBranch` (default `origin/master`), optional `gitCredentialsId`, and `logLevel` (default `INFO`).

### 2. `bldIfsOms`
- **Purpose**: Uploads one source file to IFS and executes `BLDIFSOMS` for it.
- **Parameters**:
  - `server` *(optional when inside `onIBMi`)*, `targetPath` *(required)*, and `relativePath` *(required)*.
  - `action` (default `*PUSH`), `branch`, `application` (default `*CALC`), `task` (default `*CALC`), and `routeCode` (default `*REG`).
  - `connectStreamFile` (default `*REG`), `copyToSourceFile` (default `*REG`), `ccsid` (default `1208`), `addToBuildQueue` (default `*NO`), and `releaseBuildQueue` (default `*NO`).
  - `logLevel` (default `3`): numeric level `1=TRACE`, `2=DEBUG`, `3=INFO`, `4=WARNING`, or `5=ERROR`. `targetPath` is passed to BLDIFSOMS as `DIR`. Notifications are handled independently.

---

## Architecture & Design Decisions

- **Direct Dependency on `ibmi-steps-plugin`**: Reuses `IBMiContext` and `IBMi` from `io.jenkins.plugins:ibmi-steps`. No duplicated connection or credential management code.
- **Server Configuration Reuse**: Server hostnames, ports, SSL, and credentials are configured once under **Manage Jenkins > System > IBM i Servers**.
- **Native Git Diff Resolution**: Calculates workspace diffs using JGit / Jenkins Git Client API without spawning shell commands.

---

## Pipeline Usage Example ([Jenkinsfile](Jenkinsfile))

```groovy
pipeline {
  agent any

  options {
    timestamps()
  }

  parameters {
    string(name: 'IBMI_SERVER', defaultValue: 'Plato', description: 'IBM i server profile name configured in Jenkins')
    string(name: 'TARGET_PATH', defaultValue: '/QOpenSys/OMSIFS/XMP/GITSRC/DEV/XT0748', description: 'IFS path for uploads')
    string(name: 'COMPARE_BRANCH', defaultValue: 'origin/master', description: 'Branch to compare against for changed files')
    choice(name: 'LOG_LEVEL', choices: ['3', '4', '5', '2', '1'], description: 'Minimum TD/OMS log level (1=TRACE, 2=DEBUG, 3=INFO, 4=WARNING, 5=ERROR)')
  }

  stages {
    stage('Build & Deploy TD/OMS') {
      steps {
        onIBMi(params.IBMI_SERVER) {
          def changedFiles = tdOmsChangedFiles compareBranch: params.COMPARE_BRANCH,
                                               gitCredentialsId: 'bitbucket-eunice-creds',
                                               logLevel: params.LOG_LEVEL

          changedFiles.each { file ->
            bldIfsOms targetPath: params.TARGET_PATH,
                      relativePath: file.relativePath,
                      branch: env.BRANCH_NAME ?: env.GIT_BRANCH,
                      logLevel: params.LOG_LEVEL
          }
        }
      }
    }
  }
}
```

---

## Building and Packaging

- **Requirements**: Java 17+, Maven 3.9+
- **Compile**:
  ```powershell
  mvn compile -DskipTests
  ```
- **Package HPI Plugin**:
  ```powershell
  mvn package -DskipTests
  ```
  *(Output generated at `target/td-oms-jenkins-plugin.hpi`)*
