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
Each returned item is a map with these string values:

| Key | Description |
| --- | --- |
| `fileName` | File name without its directory path. |
| `relativePath` | Path to the file relative to the workspace. |
| `extension` | File extension without the dot, or an empty string if there is none. |
| `isRootFile` | `"true"` if the file is in the workspace root, otherwise `"false"`. |
| `isHidden` | `"true"` if any file or directory name in the path starts with a dot, otherwise `"false"`. |

`omsPush` uploads one workspace file to IFS and invokes `BLDIFSOMS ACTC(*PUSH)` for it. It can use an enclosing `onIBMi` block or a configured server supplied through the `server` parameter. `omsReleaseBuildQ` and `omsDeploy` invoke `BLDIFSOMS ACTC(*RLSBQ)` and `BLDIFSOMS ACTC(*DEPLOY)` respectively, without uploading files. Both command-only steps must run inside `onIBMi` and accept only `branch`, `application`, and `task`.

### Example

```groovy
I’ll trace the current pipeline examples and step APIs around `TdOmsBuildIfsOmsStep.java`, then update the script at its owning file and run the narrowest available validation.

Searched codebase for "Find the Jenkins pipeline script matching Build & Deploy TD/OMS, omsChangedFiles, omsPush, BLDIFSOMS, and identify the latest intended pipeline syntax and examples.", 6 results

The current API has dedicated `omsReleaseBuildQ` and `omsDeploy` steps, so the raw `ibmiCommand "BLDIFSOMS ..."` calls are the likely obsolete part. I’m checking the repository’s canonical Jenkinsfile and tests to confirm the exact changed-files step name before editing.

Searched for regex `tdOmsChangedFiles|omsChangedFiles|omsReleaseBuildQ|omsDeploy` (`**/src/test/**`), 4 results

Searched for files matching `**/src/main/java/**/*Release*Step.java`, 1 match

Read 

Read , lines 35 to 160

Confirmed the public DSL name is `omsChangedFiles` (not the stale `tdOmsChangedFiles` name in part of the summary). The migration can preserve the per-extension push behavior while replacing the release and deploy commands with their typed pipeline steps.

Read 

Read 

Read , lines 90 to 145

Read , lines 35 to 80

Updated for the latest pipeline steps. `omsReleaseBuildQ` and `omsDeploy` replace the raw `BLDIFSOMS` commands. INFO is now the default log level.

```groovy
pipeline {
  agent any

  options {
    timestamps()
  }

  parameters {
    string(name: 'IBMI_SERVER', defaultValue: 'Plato', description: 'IBM i server profile name configured in Jenkins')
    string(name: 'TARGET_PATH', defaultValue: '/tmp/remain/jenkins/upload', description: 'IFS path for uploads')
    string(name: 'COMPARE_BRANCH', defaultValue: 'origin/master', description: 'Branch to compare against for changed files')
    choice(name: 'LOG_LEVEL', choices: ['3', '4', '5', '2', '1'], description: 'Minimum TD/OMS log level (1=TRACE, 2=DEBUG, 3=INFO, 4=WARNING, 5=ERROR)')
  }

  stages {
    stage('Build & Deploy TD/OMS') {
      steps {
        onIBMi(params.IBMI_SERVER) {

          // Do some initial work
          ibmiCommand 'CHGENV OMSXMP'
          ibmiCommand 'ADDLIBLE V19T0081'

          // Push and add to task
          script {
            def branch = env.BRANCH_NAME ?: env.GIT_BRANCH
            def changedFiles = omsChangedFiles(
              compareBranch: params.COMPARE_BRANCH,
              gitCredentialsId: 'bitbucket-eunice-creds',
              logLevel: params.LOG_LEVEL
            )

            changedFiles.each { file ->
              if (file.extension.toLowerCase() in ['rpgle', 'clle', 'sqlrpgle']) {
                omsPush(
                  targetPath: params.TARGET_PATH,
                  relativePath: file.relativePath,
                  branch: branch,
                  addToBuildQueue: '*YES',
                  logLevel: params.LOG_LEVEL
                )
              } else if (file.extension.toLowerCase() in ['dspf', 'pnlgrp']) {
                omsPush(
                  targetPath: params.TARGET_PATH,
                  relativePath: file.relativePath,
                  branch: branch,
                  copyToSourceFile: '*YES',
                  addToBuildQueue: '*YES',
                  logLevel: params.LOG_LEVEL
                )
              } else {
                omsPush(
                  targetPath: params.TARGET_PATH,
                  relativePath: file.relativePath,
                  branch: branch,
                  connectStreamFile: '*YES',
                  connectObject: '*NO',
                  logLevel: params.LOG_LEVEL
                )
              }
            }

            // Release the build queue
            omsReleaseBuildQ branch: branch

            // Deploy out of development
            omsDeploy branch: branch
          }
        }
      }
    }
  }
}
```

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
