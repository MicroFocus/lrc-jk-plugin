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
    );

    @Transient
    private val apiClient = ApiClientFactory.getClient(
        this.serverConfiguration,
        LoggerProxy(this.logger, LoggerOptions(this.testRunOptions.isDebug, "ApiClient"))
    );

    @Transient
    private val loadTestService = LoadTestService(
        this.apiClient,
        LoggerProxy(this.logger, LoggerOptions(this.testRunOptions.isDebug, "LoadTestService"))
    );

    @Transient
    private val loadTestRunService = LoadTestRunService(
        this.apiClient,
        LoggerProxy(this.logger, LoggerOptions(this.testRunOptions.isDebug, "LoadTestRunService"))
    );

    @Transient
    private val reportDownloader = ReportDownloader(
        this.apiClient,
        LoggerProxy(this.logger, LoggerOptions(this.testRunOptions.isDebug, "ReportDownloader"))
    );

    var testRun: LoadTestRun? = null
        private set

    @kotlin.jvm.Throws(IOException::class, InterruptedException::class)
    fun run(): LoadTestRun {
        this.loggerProxy.info("Authentication passed.");
        this.loggerProxy.info("Fetching load test #${this.testRunOptions.testId} ...");

        val lt = this.loadTestService.fetch(this.testRunOptions.testId);
        this.loggerProxy.info("Staring load test \"${lt.name}\" ...");

        val runId = this.loadTestService.startTestRun(lt.id, this.testRunOptions.sendEmail);
        this.loggerProxy.info("Test run #${runId} started.");
        val testRun = LoadTestRun(runId, lt);
        this.testRun = testRun;
        this.waitingForTestRunToEnd(testRun);
        this.loggerProxy.info("Test run #${runId} ended with ${testRun.statusEnum.statusName}");

        this.waitingForReportReady(testRun);
        if (testRun.hasReport) {
            this.reportDownloader.download(testRun, arrayOf("csv", "pdf"));
        }

        return testRun;
    }

    private fun waitingForTestRunToEnd(testRun: LoadTestRun) {
        // refresh test run status
        // print status
        // if test run not end, repeat the loop
        val interval = 5000L;
        var loginRetryTimes = 0;
        val maxLoginRetry = 3;
        var retryTimes = 0;
        val maxRetry = 5;
        while (!testRun.testRunCompletelyEnded()) {
            Thread.sleep(interval);
            try {
                this.loadTestRunService.fetchStatus(testRun);
                retryTimes = 0;
                loginRetryTimes = 0;
            } catch (e: IOException) {
                if (e.message == "401") {
                    if (loginRetryTimes < maxLoginRetry) {
                        this.loggerProxy.error("Authentication failed. Session may time out, try login again.");
                        try {
                            this.apiClient.login();
                        } catch (ee: IOException) {
                            this.loggerProxy.error("login failed: ${ee.message}");
                        }
                        loginRetryTimes += 1;
                        continue;
                    } else {
                        this.loggerProxy.error("login retried $maxLoginRetry times, failed.");
                    }
                }

                retryTimes++
                if (retryTimes >= maxRetry) {
                    logger.println("retried $maxRetry times, abort")
                    throw e
                }
                this.loggerProxy.error("Failed to fetch test run status: ${e.message}");
                this.loggerProxy.error("error occurred during test running, retrying ...$retryTimes/10")
            }
            this.printTestRunStatus(testRun);
        }
    }

    private fun waitingForReportReady(testRun: LoadTestRun) {
        val interval = 5000L;
        val maxRetry = 10;
        var retryTimes = 0;
        while (!testRun.hasReport && retryTimes < maxRetry) {
            Thread.sleep(interval);
            this.loadTestRunService.fetchStatus(testRun);
            retryTimes += 1;
        }

        if (!testRun.hasReport) {
            this.loggerProxy.info("Test run #${testRun.id} has no report.");
        }
    }

    private fun printTestRunStatus(testRun: LoadTestRun) {
        this.loggerProxy.info("${testRun.statusEnum.statusName} - ${testRun.status}");
    }

    override fun close() {
        this.apiClient.close();
    }

    fun interruptHandler(): String {
        val testRun = this.testRun;
        if (testRun == null) {
            this.loggerProxy.info("Test run is not started yet, aborting ...")
            this.loggerProxy.info("You may want to go to LoadRunner Cloud website to check if you need to stop the run manually.")
            return TestRunStatus.ABORTED.statusName;
        }

        this.loggerProxy.info("Aborting test run #${testRun.id} ...");
        this.loadTestRunService.abort(testRun);
        this.testRun = testRun;
        return TestRunStatus.ABORTED.statusName;

    }

    fun fetchTrending(testRun: LoadTestRun, benchmark: Int?): TrendingDataWrapper {
        var benchmarkTrending: TrendingDataWrapper? = null;
        if (benchmark != null) {
            val benchmarkRun = this.loadTestRunService.fetch(benchmark.toString());
            if (benchmarkRun != null) {
                benchmarkTrending = this.reportDownloader.fetchTrending(benchmarkRun, null);
            }
        }

        return this.reportDownloader.fetchTrending(testRun, benchmarkTrending);
    }
}
