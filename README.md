[![Build](https://github.com/MicroFocus/lrc-jk-plugin/actions/workflows/build.yml/badge.svg)](https://github.com/MicroFocus/lrc-jk-plugin/actions/workflows/build.yml)
[![Test](https://github.com/MicroFocus/lrc-jk-plugin/actions/workflows/test.yml/badge.svg)](https://github.com/MicroFocus/lrc-jk-plugin/actions/workflows/test.yml)

# Jenkins plugin for LoadRunner Cloud

## Introduction

The plugin allows you to run test in [LoadRunner Cloud](https://admhelp.microfocus.com/lrc/en/Latest/Content/Storm/c_Getting_started.htm) and collect results.  
It provides a build step **Run test in LoadRunner Cloud** to run tests, and a post-build action **Generate LoadRunner Cloud trending report** to generate trending reports.
Both of them are also exposed in pipeline job as `lrcRunTest` and `lrcGenTrendingReport`.

## Getting started

### System configuration
- Go to **Manage Jenkins** &rarr; **System Configuration** &rarr; **Configure System** to specify below settings: 
  - **Username** and **Password**  
  - If you use [API Access keys](https://admhelp.microfocus.com/lrc/en/Latest/Content/Storm/Admin-APIAccess.htm), select the **Use OAuth token** checkbox, then input **Client Id** and **Client Secret**.
  - **URL**, default: "https://loadrunner-cloud.saas.microfocus.com"
  - **Tenant Id**, for example: 652261300
  - **Proxy** settings (optional). If you need to use a proxy to access LoadRunner Cloud, select **Connect LoadRunner Cloud via proxy** checkbox, then input below fields.
    - Proxy Host	    - The proxy server host name.
    - Proxy Port	    - The proxy server port number.
    - Proxy Username	- The username to log into the proxy server.
    - Proxy Password	- The password to log into the proxy server.
    > Above proxy settings are only effective for connections between Jenkins and LoadRunner Cloud.

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
1. **Freestyle**  
   - In a freestyle project, select "Run test in LoadRunner Cloud" in **Add build step**, then input **Test ID** and **Project ID**.  
     > **Tip:** To build a more flexible job, you can use string parameters (LRC_TEST_ID, LRC_PROJECT_ID) to override the **Test ID** and **Project ID**.  

   - If you need trending report, select "Generate LoadRunner Cloud trending report" in **Add post-build action**.    

| Item                             | Description                                                                                                                                                                                                                                                                                                         |
|----------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Number of Runs**               | The number of last successful runs to include in the report. The valid range is 5-10.                                                                                                                                                                                                                               |
| **Benchmark**                    | Enter a specific test run ID to use as a benchmark, or leave blank to compare a run to the previous run.<br/>**Note**: If you change the benchmark run ID, only those load tests that run after the change are compared to the new benchmark. Load tests that ran before the change show their original comparison. |
| **Thresholds: TRT (Avg)**        | <Enter a positive integer between from 1 to 100>                                                                                                                                                                                                                                                                    |
| Improvement                      | The percentage decrease in average transaction response time considered to be an improvement in performance.                                                                                                                                                                                                        |
| Minor Regression                 | The percentage increase in average transaction response time considered to be a minor regression in performance.                                                                                                                                                                                                    |
| Major Regression                 | The percentage increase in average transaction response time considered to be a major regression in performance.                                                                                                                                                                                                    |
| **Thresholds: TRT (Percentile)** | <Enter a positive integer between from 1 to 100>                                                                                                                                                                                                                                                                    |
| Improvement                      | The percentage decrease in percentile transaction response time considered to be an improvement in performance.                                                                                                                                                                                                     |
| Minor Regression                 | The percentage increase in percentile transaction response time considered to be a minor regression in performance.                                                                                                                                                                                                 |
| Major Regression                 | The percentage increase in percentile transaction response time considered to be a major regression in performance.                                                                                                                                                                                                 |

2. **Pipeline**  
   Below is an example on how to run a test and generate trending report in pipeline:

```groovy
pipeline {
    agent any
    
    stages {
        stage('lrc') {    
            steps {
                lrcRunTest testId: "2398", projectId:'45', sendEmail: false
                lrcGenTrendingReport benchmark: 0, runsCount: 5, trtAvgThresholdImprovement: 5,trtAvgThresholdMajorRegression: 10, trtAvgThresholdMinorRegression: 5, trtPercentileThresholdImprovement: 5, trtPercentileThresholdMajorRegression: 10, trtPercentileThresholdMinorRegression: 5
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
> - If you don't need PDF report, define a boolean or string parameter (LRC_SKIP_PDF_REPORT: true) in job to skip it.

### Trending
If post-build action **Generate LoadRunner Cloud trending report** is configured, a menu named as "LoadRunner Cloud Trending" will be displayed. You can view the trending report by clicking the menu.
> **Notes:**
> - If you start multiple LoadRunner Cloud test runs in one Jenkins build, only the last test run will be processed by **Generate LoadRunner Cloud trending report**.

## Releases
See [GitHub Releases](https://github.com/MicroFocus/lrc-jk-plugin/releases)

## Notes
- The plugin requires Jenkins version **2.289.3** or above.
- It's highly recommended to use the latest Jenkins [LTS](https://get.jenkins.io/war-stable/) releases.
- It's highly recommended to update the plugin to the latest version,  
  **Manage Jenkins** &rarr; **Manage Plugins**, on the **Updates** tab, click **Check now** to check for the most recent plugin updates.
