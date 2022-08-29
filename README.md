[![Build Status](https://ci.jenkins.io/job/Plugins/job/loadrunner-cloud-plugin/job/main/badge/icon)](https://ci.jenkins.io/job/Plugins/job/loadrunner-cloud-plugin/job/main/)
[![CodeQL](https://github.com/jenkinsci/loadrunner-cloud-plugin/actions/workflows/codeql.yml/badge.svg)](https://github.com/jenkinsci/loadrunner-cloud-plugin/actions/workflows/codeql.yml)

# Jenkins plugin for LoadRunner Cloud

- [Introduction](#introduction)
- [Getting Started](#getting-started)
- [Notes](#notes)
- [Releases](#releases)

## Introduction

The plugin lets you run a test in [LoadRunner Cloud](https://admhelp.microfocus.com/lrc/en/Latest/Content/Storm/c_Getting_started.htm) and collect results.  
It provides a build step **Run test in LoadRunner Cloud** to run tests, and a post-build action **Generate LoadRunner Cloud trending report** to generate trending reports.
Both steps are also exposed in the pipeline job as `lrcRunTest` and `lrcGenTrendingReport`.

## Getting started

### System configuration
- Navigate to **Manage Jenkins** &rarr; **System Configuration** &rarr; **Configure System** &rarr; **LoadRunner Cloud** and then configure the following settings: 
  - **Username** and **Password**  
  - If you use [API Access keys](https://admhelp.microfocus.com/lrc/en/Latest/Content/Storm/Admin-APIAccess.htm), select the **Use OAuth token** checkbox, then input the **Client ID** and **Client Secret**.
  - **URL**, default: "https://loadrunner-cloud.saas.microfocus.com"
  - **Tenant Id**, for example: 123456789
  - **Proxy** settings (optional). If you need to use a proxy to access LoadRunner Cloud, select **Connect LoadRunner Cloud via proxy** checkbox, then configure the following fields.
    - Proxy Host	    - The proxy server host name.
    - Proxy Port	    - The proxy server port number.
    - Proxy Username	- The username to log into the proxy server.
    - Proxy Password	- The password to log into the proxy server.
    > **Notes**: The above proxy settings are only applicable for connections between Jenkins and LoadRunner Cloud.  

![System configuration](/images/system_config.png "LoadRunner Cloud")

- Sample configuration for 
[Jenkins configuration as Code](https://github.com/jenkinsci/configuration-as-code-plugin):  
```yaml
unclassified:
  lrcRunTest:
    tenantId: "<TENANT ID>"
    username: "<USERNAME>"
    password: "<PASSWORD>"
    url: "https://loadrunner-cloud.saas.microfocus.com"
    useOAuth: false
    clientId: "<CLIENT ID>"
    clientSecret: "<CLIENT SECRET>"
    useProxy: false
    proxyHost: "<PROXY HOST>"
    proxyPort: "<PROXY PORT>"
    proxyUsername: "<PROXY USERNAME>"
    proxyPassword: "<PROXY PASSWORD>"
```

### Job configuration

#### Freestyle
In a freestyle project, in **Add build step**, select **Run test in LoadRunner Cloud** and then configure the **Test ID** and **Project ID**.  
> **Tip:** To build a more flexible job, you can use string parameters (LRC_TEST_ID, LRC_PROJECT_ID) to override the **Test ID** and **Project ID**.  
     
![Job configuration](/images/job_config.png "Run test in LoadRunner Cloud")

#### Pipeline
Below is an example on how to run a test and generate a trending report in a pipeline:

```groovy
pipeline {
    agent any
    
    stages {
        stage('lrc') {    
            steps {
                lrcRunTest testId: "2398", projectId:'45', sendEmail: false
                lrcGenTrendingReport benchmark: 0, runsCount: 5, trtAvgThresholdImprovement: 5, trtAvgThresholdMajorRegression: 10, trtAvgThresholdMinorRegression: 5, trtPercentileThresholdImprovement: 5, trtPercentileThresholdMajorRegression: 10, trtPercentileThresholdMinorRegression: 5
            }
        }
    }
}
```
### Results

After the build is completed, the plugin generates the following files (if they are available) in the workspace folder.

| File                         | Description                                                                                                     |
|------------------------------|-----------------------------------------------------------------------------------------------------------------|
| **lrc_report_XXX.xml**       | A JUnit XML file containing basic information about the test run, such as name, status, duration, and so forth. |
| **lrc_report_XXX.csv**       | A CSV file containing detailed test run results with metrics, such as Vuser count, error count, and so forth.   |
| **lrc_report_XXX.pdf**       | A PDF file containing report data for the test run.                                                             |
| **lrc_report_trans_XXX.csv** | A CSV file containing detailed statistics for each transaction in the test run.                                 |

> **Notes:**  
> - In the above, "XXX" refers to tenant id-run id. For example: 652261300-123.
> - If a Jenkins job that includes a running test is aborted, the plugin will attempt to stop the corresponding test run in LoadRunner Cloud. **It does not collect results**. The attempt may fail if there are network problems, or if Jenkins aborts the job before the plugin can stop the test run.
> - If a PDF report is not needed, define a boolean or string parameter (LRC_SKIP_PDF_REPORT: true) to skip it.
> - The test run id is exposed in the environment variable: **LRC_RUN_ID**.

### Trending
If you need a trending report, select **Generate LoadRunner Cloud trending report** in **Add post-build action**.
![Trending configuration](/images/trending_config.png "Generate LoadRunner Cloud trending report")

| Item                             | Description                                                                                                                                                                                                                                                                                                         |
|----------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Number of Runs**               | The number of last successful runs to include in the report. The valid range is 5-10.                                                                                                                                                                                                                               |
| **Benchmark**                    | Enter a specific test run ID to use as a benchmark, or leave blank to compare a run to the previous run.<br/>**Note**: If you change the benchmark run ID, only those load tests that run after the change are compared to the new benchmark. Load tests that ran before the change show their original comparison. |
| **Thresholds: TRT (Avg)**        | <Enter a positive integer from 1 to 100>                                                                                                                                                                                                                                                                            |
| Improvement                      | The percentage decrease in average transaction response time considered to be an improvement in performance.                                                                                                                                                                                                        |
| Minor Regression                 | The percentage increase in average transaction response time considered to be a minor regression in performance.                                                                                                                                                                                                    |
| Major Regression                 | The percentage increase in average transaction response time considered to be a major regression in performance.                                                                                                                                                                                                    |
| **Thresholds: TRT (Percentile)** | <Enter a positive integer from 1 to 100>                                                                                                                                                                                                                                                                            |
| Improvement                      | The percentage decrease in percentile transaction response time considered to be an improvement in performance.                                                                                                                                                                                                     |
| Minor Regression                 | The percentage increase in percentile transaction response time considered to be a minor regression in performance.                                                                                                                                                                                                 |
| Major Regression                 | The percentage increase in percentile transaction response time considered to be a major regression in performance.                                                                                                                                                                                                 |

You can view the trending report by clicking the "**LoadRunner Cloud Trending**" menu. It shows the trends for the last 5-10 runs of the load test configured in the job. 

![Trending menu](/images/trending_menu.png "LoadRunner Cloud Trending menu")
> **Notes:** If you start multiple LoadRunner Cloud test runs in one Jenkins build, only the last test run will be processed by **Generate LoadRunner Cloud trending report**.

## Notes
- Keep your password or secret safe.
- The plugin requires Jenkins version **2.289.3** or above.
- It is recommended to use the latest Jenkins [**LTS**](https://get.jenkins.io/war-stable/) release.
- It is recommended to update the plugin to the latest version.
  > **Tip**: **Manage Jenkins** &rarr; **Manage Plugins**, on the **Updates** tab, click **Check now** to check for the most recent plugin updates.

## Releases
See [GitHub Releases](https://github.com/MicroFocus/loadrunner-cloud-plugin/releases)
