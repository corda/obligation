<p align="center">
    <img src="https://www.corda.net/wp-content/uploads/2016/11/fg005_corda_b.png" alt="Corda" width="500">
</p>

# Obligation CorDapp

## Reminder

This project is open source under an Apache 2.0 licence. That means you
can submit PRs to fix bugs and add new features if they are not currently
available.

### Adding confidential identities dependencies to an existing CorDapp

First, add a variable for the confidential identities version you wish to use:

    buildscript {
        ext {
            obligation_release_version = '1.0-SNAPSHOT'
            obligation_release_group = 'com.r3.corda.lib.obligation'
        }
    }

Second, you must add the confidential identities development artifactory repository to the
list of repositories for your project:

    repositories {
        maven { url 'http://ci-artifactory.corda.r3cev.com/artifactory/corda-lib-dev' }
    }

Now, you can add the confidential identities dependencies to the `dependencies` block
in each module of your CorDapp. In your workflow `build.gradle` add:

    cordaCompile "obligation_release_group:contracts:obligation_release_version"
    cordaCompile "obligation_release_group:workflows:obligation_release_version"

If you want to use the `deployNodes` task, you will need to add the
following dependency to your root `build.gradle` file:

    cordapp "obligation_release_group:contracts:$obligation_release_version"
    cordapp "obligation_release_group:workflows:$obligation_release_version"

These should also be added to the `deployNodes` task with the following syntax:

    nodeDefaults {
        projectCordapp {
            deploy = false
        }
        cordapp("obligation_release_group:contracts:$obligation_release_version")
        cordapp("obligation_release_group:workflows:$obligation_release_version")
    }

## Flows 
//TODO
