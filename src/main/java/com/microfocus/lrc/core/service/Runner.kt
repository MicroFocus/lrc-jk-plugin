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

import com.microfocus.lrc.core.ApiClientFactory
import com.microfocus.lrc.core.Constants
import com.microfocus.lrc.core.entity.*
import com.microfocus.lrc.jenkins.LoggerOptions
import com.microfocus.lrc.jenkins.LoggerProxy
import java.io.Closeable
import java.io.IOException
import java.io.PrintStream
import java.io.Serializable

class Runner(
    private val serverConfiguration: ServerConfiguration,
    @Transient private val logger: PrintStream = System.out,
    private val testRunOptions: TestRunOptions
) : Serializable, Closeable {

    companion object {
        private const val serialVersionUID = 1L
    }

    @Transient
    private val loggerProxy = LoggerProxy(
        this.logger,
        LoggerOptions(this.testRunOptions.isDebug, "Runner")
    )

    @Transient
    private val apiClient = ApiClientFactory.getClient(
        this.serverConfiguration,
        LoggerProxy(this.logger, LoggerOptions(this.testRunOptions.isDebug, "ApiClient"))
    )

    @Transient
    private val loadTestService = LoadTestService(
        this.apiClient,
        LoggerProxy(this.logger, LoggerOptions(this.testRunOptions.isDebug, "LoadTestService"))
    )

    @Transient
    private val loadTestRunService = LoadTestRunService(
        this.apiClient,
        LoggerProxy(this.logger, LoggerOptions(this.testRunOptions.isDebug, "LoadTestRunService"))
    )

    @Transient
    private val reportDownloader = ReportDownloader(
        this.apiClient,
        LoggerProxy(this.logger, LoggerOptions(this.testRunOptions.isDebug, "ReportDownloader")),
        this.testRunOptions
    )

    var testRun: LoadTestRun? = null
        private set

    @kotlin.jvm.Throws(IOException::class, InterruptedException::class)
    fun run(): LoadTestRun {
        this.loggerProxy.info("Fetching load test #${this.testRunOptions.testId} ...")

        val lt = this.loadTestService.fetch(this.testRunOptions.testId)
        this.loggerProxy.info("Staring load test \"${lt.name}\" ...")

        val runId = this.loadTestService.startTestRun(lt.id, this.testRunOptions.sendEmail)
        this.loggerProxy.info("Test run #${runId} started.")
        val testRun = LoadTestRun(runId, lt)
        this.testRun = testRun
        this.waitingForTestRunToEnd(testRun)
        this.loggerProxy.info("Test run #${runId} ended with ${testRun.statusEnum.statusName} status.")

        this.loadTestRunService.fetchStatus(testRun)
        if (testRun.hasReport) {
            this.reportDownloader.download(testRun, arrayOf("csv", "pdf"))
        } else {
            this.loggerProxy.info("Test run #${testRun.id} doesn\'t have run results.")
            this.reportDownloader.genXmlFile(testRun)
        }

        return testRun
    }

    @SuppressWarnings("kotlin:S3776")
    private fun waitingForTestRunToEnd(testRun: LoadTestRun) {
        // refresh test run status
        // print status
        // if test run not end, repeat the loop
        val maxRetry = Constants.TEST_RUN_END_MAXRETRY
        val maxLoginRetry = Constants.TEST_RUN_END_LOGIN_MAXRETRY

        var retryTimes = 0
        var loginRetryTimes = 0

        while (!testRun.testRunCompletelyEnded()) {
            Thread.sleep(Constants.TEST_RUN_END_POLLING_INTERVAL)
            try {
                this.loadTestRunService.fetch(testRun)
                retryTimes = 0
                loginRetryTimes = 0
            } catch (e: Exception) {
                if (e.message == "401") {
                    if (loginRetryTimes < maxLoginRetry) {
                        this.loggerProxy.error("Authentication failed, retrying ...")
                        loginRetryTimes += 1

                        try {
                            this.apiClient.login()
                        } catch (ee: IOException) {
                            this.loggerProxy.error("Login failed: ${ee.message}")
                        }
                        continue
                    } else {
                        this.loggerProxy.error("Login retried $maxLoginRetry times, failed.")
                        throw e
                    }
                }

                retryTimes++
                if (retryTimes >= maxRetry) {
                    logger.println("Retried $maxRetry times, abort")
                    throw e
                }
                this.loggerProxy.error("Failed to fetch test run status: ${e.message}")
                this.loggerProxy.error("Error occurred during test running, retrying ...${retryTimes}/${maxRetry}")
            }
            this.printTestRunStatus(testRun)
        }
    }

    private fun printTestRunStatus(testRun: LoadTestRun) {
        this.loggerProxy.info("${testRun.statusEnum.statusName} - ${testRun.status}")
    }

    override fun close() {
        this.apiClient.close()
    }

    fun interruptHandler(): String {
        val testRun = this.testRun
        if (testRun == null) {
            this.loggerProxy.info("Test run is not started yet, aborting ...")
            this.loggerProxy.info("You may want to go to LoadRunner Cloud website to check if you need to stop the run manually.")
            return TestRunStatus.ABORTED.statusName
        }

        this.loggerProxy.info("Aborting test run #${testRun.id} ...")
        this.loadTestRunService.abort(testRun)
        this.testRun = testRun

        return TestRunStatus.ABORTED.statusName
    }

    fun fetchTrending(testRun: LoadTestRun, benchmark: Int?): TrendingDataWrapper {
        var benchmarkTrending: TrendingDataWrapper? = null
        if (benchmark != null) {
            val benchmarkRun = this.loadTestRunService.fetch(benchmark.toString())
            if (benchmarkRun != null) {
                benchmarkTrending = this.reportDownloader.fetchTrending(benchmarkRun, null)
            }
        }

        return this.reportDownloader.fetchTrending(testRun, benchmarkTrending)
    }
}
