[![CodeQL](https://github.com/MicroFocus/lrc-jk-plugin/actions/workflows/codeql.yml/badge.svg)](https://github.com/MicroFocus/lrc-jk-plugin/actions/workflows/codeql.yml)

# Jenkins plugin for LoadRunner Cloud

Jenkins plugin for LoadRunner Cloud

# Introduction

This plugin allows you to run LoadRunner Cloud tests from Jenkins.

# Getting started

The plugin provides a build step "LoadRunner Cloud" to run LoadRunner Cloud tests, and a post-build action "Generate LoadRunner Cloud Trending" to generate trending reports.

This two action are also available in pipeline jobs as `srlRunTest` and `srlGetTrendingReport`.

## Usage

### Global configuration

Before running any tests, you will need provide following information in jenkins global configuration.

 - Username and password for LoadRunner Cloud account
 - LoadRunner Cloud server url
 - Tenant Id

You can also use Client ID and Client Secret to authenticate to LoadRunner Cloud instead of username and password.

### Run test

Below is an example of how to run a test and generate its trending report.

```groovy
pipeline {
    agent any
    
    stages {
        stage('lrc') {    
            steps {
                srlRunTest projectId:'45', testId: "2398", sendEmail: true
                srlGetTrendingReport benchmark: 0, runsCount: 5, trtAvgThresholdImprovement: 7,trtAvgThresholdMajorRegression: 9, trtAvgThresholdMinorRegression: 8, trtPercentileThresholdImprovement: 10, trtPercentileThresholdMajorRegression: 12, trtPercentileThresholdMinorRegression: 11
            }
        }
    }
}
```

For freestyle jobs, simply provides same parameters in the job configuration.

### Reports

After the build step "LoadRunner Cloud" finished, several reports will be downloaded to the workspace if available, including "csv", "pdf" and "docx". A JUnit format XML report will also be generated. All those reports will be named as "srl_report_{TENANTID}_{RUNID}".

Besides, if post-action "Generate LoadRunner Cloud Trending" is configured, a trending report html will also be generated in workspace. A menu button on the left panel named "LoadRunner Cloud Trend" will be added to the job page. You can view the trending report directly by clicking the button.



