# TD/OMS Integration Plugin

This Jenkins plugin provides Pipeline steps for TD/OMS on IBM i. 

* Handles git branches
* Uploads sources to the IBM i
* Transforms these sources to actual programs and other objects
* It detects changed source files, uploads them to IFS, and invokes `BLDIFSOMS`.
* It can add objects to the build queue for compilation
* It can deploy the objects through the TD/OMS pipeline

## Requirements

- The Jenkins [IBM i Steps plugin](https://plugins.jenkins.io/ibmi-steps/)
- A TD/OMS installation and an IBM i server configured in Jenkins


## Pipeline steps

`omsChangedFiles` returns changed files relative to a comparison branch.

`omsPush` uploads one workspace file to IFS and invokes `BLDIFSOMS ACTC(*PUSH)` for it. It can use an enclosing `onIBMi` block or a configured server supplied through the `server` parameter. `omsReleaseBuildQ` and `omsDeploy` invoke `BLDIFSOMS ACTC(*RLSBQ)` and `BLDIFSOMS ACTC(*DEPLOY)` respectively, without uploading files. Both command-only steps must run inside `onIBMi` and accept only `branch`, `application`, and `task`.

### Example

```groovy
pipeline {
  agent any
  stages {
    stage('Deploy TD/OMS changes') {
      steps {
        onIBMi('my-ibmi-server') {
          script {
            def changedFiles = omsChangedFiles compareBranch: 'origin/master', logLevel: '3'
            changedFiles.each { file ->
              omsPush targetPath: '/QOpenSys/OMSIFS/XMP/GITSRC/DEV',
                      relativePath: file.relativePath,
                      branch: env.BRANCH_NAME ?: env.GIT_BRANCH
            }
            omsReleaseBuildQ branch: env.BRANCH_NAME ?: env.GIT_BRANCH
            omsDeploy branch: env.BRANCH_NAME ?: env.GIT_BRANCH
          }
        }
      }
    }
  }
}
```

## Configuration

Configure IBM i server profiles under **Manage Jenkins > System > IBM i Servers**. Use Jenkins credentials for authentication.

The `omsPush` options include `branch`, `application`, `task`, `routeCode`, `connectStreamFile`, `streamFileLabels`, `copyToSourceFile`, `connectObject`, `objectLabels`, `ccsid`, `addToBuildQueue`, `releaseBuildQueue`, and `logLevel`. `streamFileLabels` and `objectLabels` accept up to five comma-separated labels. `connectObject` defaults to `*YES` and supports `*YES`, `*NO`, `*VIRTUAL`, and `*MEMBER`; the other defaults are defined by the Pipeline step descriptor.

For all three steps, `application` and `task` default to `*CALC`. Supply `branch` unless both `application` and `task` are explicitly set to values other than `*CALC`; when omitted, `BRANCH` is not sent. Action codes are fixed by the step. Existing Pipeline calls with `omsPush action: ...` must switch to the corresponding step; calls with default application and task must supply a branch.

## Development

```powershell
mvn -B clean verify
```

## Local Test
Spins up a jenins instance with this plugin available.

```powershell
mvn hpi:run
```

See [CONTRIBUTING.md](CONTRIBUTING.md) for development guidance and [SECURITY.md](SECURITY.md) for vulnerability reporting.
