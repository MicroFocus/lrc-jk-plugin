[![Build](https://github.com/MicroFocus/lrc-jk-plugin/actions/workflows/build.yml/badge.svg)](https://github.com/MicroFocus/lrc-jk-plugin/actions/workflows/build.yml)
[![Test](https://github.com/MicroFocus/lrc-jk-plugin/actions/workflows/test.yml/badge.svg)](https://github.com/MicroFocus/lrc-jk-plugin/actions/workflows/test.yml)

# Jenkins plugin for LoadRunner Cloud

# Introduction

The plugin allows you to run test in [LoadRunner Cloud](https://admhelp.microfocus.com/lrc/en/Latest/Content/Storm/c_Getting_started.htm) and collect results. For details, refer to [Use the Jenkins plugin](https://admhelp.microfocus.com/lrc/en/Latest/Content/Storm/t_ci_plugins.htm#mt-item-0) in DOC.

# Getting started

The plugin provides a build step **LoadRunner Cloud** to run test, and a post-build action **Generate LoadRunner Cloud Trending** to generate trending reports.
Both of them are also exposed in pipeline job as `srlRunTest` and `srlGetTrendingReport`.

## Usage

### System configuration
Go to **Manage Jenkins** &rarr; **System Configuration** &rarr; **Configure System** to specify below settings: 
 - **Client Id** and **Client Secret** (recommended) or **Username** and **Password**  
   If you use [API Access keys](https://admhelp.microfocus.com/lrc/en/Latest/Content/Storm/Admin-APIAccess.htm), select the **Use OAuth token** checkbox.
 - **Tenant Id**, for example: 652261300
 - **LoadRunner Cloud URL**, default: "https://loadrunner-cloud.saas.microfocus.com"
 - **Proxy** settings (optional)

### Job configuration
- **Freestyle**  
   In a freestyle project, select "LoadRunner Cloud" in **Add build step**, then input **Test ID** and **Project ID**.  
   > **Tip:**
   To build a more flexible job, you can use string parameters (LRC_TEST_ID, LRC_PROJECT_ID) to override the **Test ID** and **Project ID**.  

   If you need trending report, select "Generate LoadRunner Cloud Trending" in **Add post-build action**.
- **Pipeline**  
   Below is an example on how to run a test and generate trending report in pipeline:

```groovy
pipeline {
    agent any
    
    stages {
        stage('lrc') {    
            steps {
                srlRunTest projectId:'45', testId: "2398", sendEmail: true
                srlGetTrendingReport benchmark: 0, runsCount: 5, trtAvgThresholdImprovement: 5,trtAvgThresholdMajorRegression: 10, trtAvgThresholdMinorRegression: 5, trtPercentileThresholdImprovement: 5, trtPercentileThresholdMajorRegression: 10, trtPercentileThresholdMinorRegression: 5
            }
        }
    }
}
```

### Results

The plugin generates the following files (if they are available) in workspace folder after the build is completed.

| File                                    | Description                                                                                                   |
|-----------------------------------------|---------------------------------------------------------------------------------------------------------------|
| **lrc_report_TENANTID-RUNID.xml**       | A JUnit XML file containing basic information about test run, such as name, status, duration, and so forth.   |
| **lrc_report_TENANTID-RUNID.csv**       | A CSV file containing detailed test run results with metrics, such as Vuser count, error count, and so forth. |
| **lrc_report_TENANTID-RUNID.pdf**       | A PDF file containing report data for the test run.                                                           |
| **lrc_report_trans_TENANTID-RUNID.csv** | A CSV file containing detailed statistics for each transaction in the test run.                               |

> **Notes:**  
> - If build / job is aborted or cancelled, the plugin will not try to collect results.  
> - If you don't need PDF report, define a boolean or string parameter (LRC_SKIP_PDF_REPORT: true) in job.

### Trending
If post-build action **Generate LoadRunner Cloud Trending** is configured, a menu named as "LoadRunner Cloud Trending" will be displayed. You can view the trending report by clicking the menu.
> **Notes:**
> - If you start multiple LoadRunner Cloud test runs in one Jenkins build, only last test run will be processed by **Generate LoadRunner Cloud Trending**.
