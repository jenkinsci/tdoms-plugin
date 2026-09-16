# TD/OMS Integration Plugin

This Jenkins plugin provides Pipeline steps for TD/OMS on IBM i. 

* Handles git branches
* Uploads sources to the IBM i
* Transforms these sources to actual programs and other objects
* It detects changed source files, uploads them to IFS, and invokes `BLDIFSOMS`.
* It can add objects to the build queue for compilation
* It can deploy the objects through the TD/OMS pipeline

## Requirements

- Jenkins 2.528.3 or newer
- Java 17 or newer
- The Jenkins [IBM i Steps plugin](https://plugins.jenkins.io/ibmi-steps/)
- A TD/OMS installation and an IBM i server configured in Jenkins


## Pipeline steps

`omsChangedFiles` returns changed files relative to a comparison branch.

`omsPush` uploads one workspace file to IFS and invokes `BLDIFSOMS` for it. It can use an enclosing `onIBMi` block or a configured server supplied through the `server` parameter.

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
          }
        }
      }
    }
  }
}
```

## Configuration

Configure IBM i server profiles under **Manage Jenkins > System > IBM i Servers**. Use Jenkins credentials for authentication.

The BLDIFSOMS options include `action`, `branch`, `application`, `task`, `routeCode`, `connectStreamFile`, `copyToSourceFile`, `ccsid`, `addToBuildQueue`, `releaseBuildQueue`, and `logLevel`. Defaults are defined by the Pipeline step descriptor.

## Development

```powershell
mvn -B clean verify
```

See [CONTRIBUTING.md](CONTRIBUTING.md) for development guidance and [SECURITY.md](SECURITY.md) for vulnerability reporting.
