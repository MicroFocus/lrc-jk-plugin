/*
 * © Copyright 2022 Micro Focus or one of its affiliates.
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.microfocus.lrc.core.service

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonSyntaxException
import com.microfocus.lrc.core.ApiClient
import com.microfocus.lrc.core.Constants
import com.microfocus.lrc.core.XmlReport
import com.microfocus.lrc.core.entity.*
import com.microfocus.lrc.jenkins.LoggerProxy
import java.io.ByteArrayOutputStream
import java.io.IOException

class ReportDownloader(
    private val apiClient: ApiClient,
    private val loggerProxy: LoggerProxy,
    private val testRunOptions: TestRunOptions
) {
    companion object {
        @JvmStatic
        fun writeCsvBytesArray(transactions: Array<TestRunTransactionsResponse>): ByteArray {
            val stream = ByteArrayOutputStream()
            val writer = stream.writer()
            writer.appendLine("Script Name, Transaction, %Breakers, SLA Status, AVG Duration, Min, Max, STD. Deviation, Passed, Failed, Percentile, SLA Threshold, Percentile Trend")
            transactions.forEach { tx ->
                writer.appendLine("${tx.scriptName}, ${tx.name}, ${tx.breakers}, ${tx.slaStatus}, ${tx.avgTRT}, ${tx.minTRT}, ${tx.maxTRT}, ${tx.stdDeviation}, ${tx.passed}, ${tx.failed}, ${tx.percentileTRT}, ${tx.slaThreshold}, ${tx.slaTrend}")
            }
            writer.flush()

            return stream.toByteArray()
        }
    }

    fun download(testRun: LoadTestRun, reportTypes: Array<String>) {
        var validReportTypes = arrayOf("csv", "pdf")
        if (this.testRunOptions.skipPdfReport) {
            validReportTypes = arrayOf("csv")
        }
        // validate report types
        val filteredReportTypes = reportTypes.filter {
            it in validReportTypes
        }
        if (filteredReportTypes.isEmpty()) {
            this.loggerProxy.info("Invalid report types: ${reportTypes.joinToString(", ")}")
            this.loggerProxy.info("Skip downloading reports")
            return
        }

        // request reports generating
        filteredReportTypes.map { reportType ->
            val reportId = this.requestReportId(testRun.id, reportType)
            // wait for the report to be ready
            var retryWaitingTimes = 0
            var reportContent: ByteArray? = null
            var maxRetry = 6
            if (reportType == "pdf") {
                maxRetry = 24  // max 8 minutes for pdf report generation
            }
            while (retryWaitingTimes < maxRetry && reportContent == null) {
                reportContent = this.isReportReady(reportId)
                if (reportContent == null) {
                    Thread.sleep(Constants.REPORT_DOWNLOAD_POLLING_INTERVAL)
                    retryWaitingTimes += 1
                }
            }

            if (reportContent == null) {
                this.loggerProxy.info("Report #$reportId is not ready after $retryWaitingTimes retries")
                return
            }

            val fileName = genFileName(reportType, testRun)
            testRun.reports[fileName] = reportContent
            this.loggerProxy.info("Report $fileName downloaded.")
        }

        genXmlFile(testRun)
        genTxCsv(testRun)
    }

    private fun requestReportId(runId: Int, reportType: String): Int {
        val apiPath = ApiGenTestRunReport(
            mapOf(
                "projectId" to "${this.apiClient.getServerConfiguration().projectId}",
                "runId" to "$runId",
            )
        ).path

        val payload = JsonObject()
        payload.addProperty("reportType", reportType)

        val res = this.apiClient.post(apiPath, payload = payload)
        val body = res.body?.string()
        if (res.code != 200) {
            throw Exception("Failed to request report: ${res.code}, $body")
        }
        this.loggerProxy.debug("Requested report: $body")
        val result = Gson().fromJson(body, JsonObject::class.java)
        if (!result.has("reportId")) {
            throw Exception("Failed to request report: $body")
        }

        val reportId = result.get("reportId").asInt

        return reportId
    }

    private fun isReportReady(reportId: Int): ByteArray? {
        val apiPath = ApiTestRunReport(
            mapOf(
                "reportId" to "$reportId",
            )
        ).path

        val res = this.apiClient.get(apiPath)
        if (res.code != 200) {
            this.loggerProxy.info("Report #$reportId is not ready: ${res.code}, ${res.body?.string()}")
            return null
        }
        val contentType = res.header("content-type", null)
        if (contentType?.contains(Constants.APPLICATION_JSON) == true) {
            val body = res.body?.string()
            val result = Gson().fromJson(body, JsonObject::class.java)
            if (result["message"]?.asString == "In progress") {
                this.loggerProxy.info("Report #$reportId is not ready yet...")
                return null
            } else {
                throw Exception("Report #$reportId invalid status: $body")
            }
        }

        if (contentType?.contains("application/octet-stream") == true) {
            this.loggerProxy.info("Report #$reportId is ready.")

            return res.body?.bytes()
        }

        throw Exception("Unknown content type: $contentType")
    }

    private fun genFileName(reportType: String, testRun: LoadTestRun): String {
        return "lrc_report_${this.apiClient.getServerConfiguration().tenantId}-${testRun.id}.${reportType}"
    }

    fun genXmlFile(testRun: LoadTestRun) {
        val fileName = genFileName("xml", testRun)
        val reportUrl ="${this.apiClient.getServerConfiguration().url}/run-overview/${testRun.id}/report/?TENANTID=${this.apiClient.getServerConfiguration().tenantId}&projectId=${this.apiClient.getServerConfiguration().projectId}"
        val dashboardUrl = "${this.apiClient.getServerConfiguration().url}/run-overview/${testRun.id}/dashboard/?TENANTID=${this.apiClient.getServerConfiguration().tenantId}&projectId=${this.apiClient.getServerConfiguration().projectId}"
        this.loggerProxy.info("View report at: $reportUrl")
        this.loggerProxy.info("View dashboard at: $dashboardUrl")
        val content = XmlReport.write(
            testRun,
            reportUrl,
            dashboardUrl
        )
        testRun.reports[fileName] = content
    }

    private fun fetchTestRunResults(runId: Int): TestRunResultsResponse {
        val apiPath = ApiTestRunResults(
            mapOf(
                "runId" to "$runId",
            )
        ).path

        val res = this.apiClient.get(apiPath)
        if (res.code != 200) {
            val msg = "Failed to fetch test run results: ${res.code}, ${res.body?.string()}"
            this.loggerProxy.info(msg)
            throw IOException(msg)
        }

        val body = res.body?.string()
        this.loggerProxy.debug("Fetched test run results: $body")
        try {
            return Gson().fromJson(body, TestRunResultsResponse::class.java)
        } catch (e: JsonSyntaxException) {
            this.loggerProxy.info("Failed to parse test run results: $body")
            throw e
        }
    }

    private fun fetchTestRunTx(runId: Int): Array<TestRunTransactionsResponse> {
        val apiPath = ApiTestRunTx(
            mapOf(
                "runId" to "$runId",
            )
        ).path

        val res = this.apiClient.get(apiPath)
        if (res.code != 200) {
            val msg = "Failed to fetch test run transactions: ${res.code}, ${res.body?.string()}"
            this.loggerProxy.info(msg)
            throw IOException(msg)
        }

        val body = res.body?.string()
        this.loggerProxy.debug("Fetched transactions results: $body")
        try {
            return Gson().fromJson(body, Array<TestRunTransactionsResponse>::class.java)
        } catch (e: JsonSyntaxException) {
            this.loggerProxy.info("Failed to parse test run transactions: $body")
            throw e
        }
    }

    private fun genTxCsv(testRun: LoadTestRun) {
        val txArr = fetchTestRunTx(testRun.id)
        val fileName = "lrc_report_trans_${this.apiClient.getServerConfiguration().tenantId}-${testRun.id}.csv"
        testRun.reports[fileName] = writeCsvBytesArray(txArr)
    }

    fun fetchTrending(testRun: LoadTestRun, benchmark: TrendingDataWrapper?): TrendingDataWrapper {
        val results = this.fetchTestRunResults(testRun.id)
        val txArr = this.fetchTestRunTx(testRun.id)
        return TrendingDataWrapper(
            testRun,
            results,
            txArr,
            this.apiClient.getServerConfiguration().tenantId,
            benchmark
        )
    }
}
