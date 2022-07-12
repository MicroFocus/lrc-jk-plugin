package com.microfocus.lrc.core.service

import com.microfocus.lrc.core.ApiClientFactory
import com.microfocus.lrc.core.entity.*
import com.microfocus.lrc.jenkins.LoggerOptions
import com.microfocus.lrc.jenkins.LoggerProxy
import java.io.Closeable
import java.io.IOException
import java.io.PrintStream
import java.io.Serializable

enum class RunStatus(val value: String) {
    RUNNING("RUNNING"),
    STOPPED("STOPPED"),
    COMPLETED("COMPLETED"),
    FAILED("FAILED"),
}

class Runner(
    private val serverConfiguration: ServerConfiguration,
    @Transient private val logger: PrintStream = System.out,
    options: Map<String, String> = mapOf()
) : Serializable, Closeable {

    private val isDebugLoggingEnabled = options.getOrDefault(
        OptionInEnvVars.LRC_DEBUG_LOG.name,
        "false"
    ) == "true";

    @Transient
    private val loggerProxy = LoggerProxy(
        this.logger,
        LoggerOptions(this.isDebugLoggingEnabled, "Runner")
    );

    @Transient
    private val apiClient = ApiClientFactory.getClient(
        this.serverConfiguration,
        LoggerProxy(this.logger, LoggerOptions(this.isDebugLoggingEnabled, "ApiClient"))
    );

    @Transient
    private val loadTestService = LoadTestService(
        this.apiClient,
        LoggerProxy(this.logger, LoggerOptions(this.isDebugLoggingEnabled, "LoadTestService"))
    );

    @Transient
    private val loadTestRunService = LoadTestRunService(
        this.apiClient,
        LoggerProxy(this.logger, LoggerOptions(this.isDebugLoggingEnabled, "LoadTestRunService"))
    );

    @Transient
    private val reportDownloader = ReportDownloader(
        this.apiClient,
        LoggerProxy(this.logger, LoggerOptions(this.isDebugLoggingEnabled, "ReportDownloader"))
    );

    var testRun: LoadTestRun? = null
        private set

    @kotlin.jvm.Throws(IOException::class, InterruptedException::class)
    fun run(options: TestRunOptions): LoadTestRun {
        this.loggerProxy.info("login success.");
        this.loggerProxy.info("fetching load test ${options.testId}...");
        val lt = this.loadTestService.fetch(options.testId);
        this.loggerProxy.info("load test [${lt.name}] is going to start...");
        val runId = this.loadTestService.startTestRun(lt.id, options.sendEmail);
        this.loggerProxy.info("test run [$runId] started.");
        val testRun = LoadTestRun(runId, lt);
        this.testRun = testRun;
        this.waitingForTestRunToEnd(testRun);
        this.loggerProxy.info("test run [$runId] ended with [${testRun.statusEnum.statusName}]");
        // wait for report to be ready
        this.waitingForReportReady(testRun);
        if (testRun.hasReport) {
            this.reportDownloader.download(testRun, arrayOf("csv", "pdf", "docx"));
        }

        return testRun;
    }

    private fun waitingForTestRunToEnd(testRun: LoadTestRun) {
        // refresh test run status
        // print status
        // if test run not end, repeat the loop
        val interval = 5000L;
        while (!testRun.testRunCompletelyEnded()) {
            Thread.sleep(interval);
            this.loadTestRunService.fetchStatus(testRun);
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
            this.loggerProxy.info("test run [${testRun.id}] has no report, skip.");
        }
    }

    private fun printTestRunStatus(testRun: LoadTestRun) {
        this.loggerProxy.info("${testRun.statusEnum.statusName} - ${testRun.status}");
    }

    override fun close() {
        this.apiClient.close();
    }

    @kotlin.jvm.Throws(IOException::class, InterruptedException::class)
    fun interruptHandler(): String {
        val testRun = this.testRun;
        if (testRun == null) {
            this.loggerProxy.info("test run is not started yet, abort...")
            this.loggerProxy.info("you may want to go to the LoadRunner Cloud website to check if you need to stop the run manually.")
            return TestRunStatus.ABORTED.statusName;
        }

        this.loadTestRunService.fetchStatus(testRun);

        if (testRun.statusEnum == TestRunStatus.INITIALIZING) {
            this.loggerProxy.info("test run is initializing, abort...")
            this.loadTestRunService.abort(testRun);
            this.testRun = testRun;
            return TestRunStatus.ABORTED.statusName;
        }

        this.loggerProxy.info("test run is ${testRun.statusEnum.statusName}, stop it and fetch results before abort.")
        this.loadTestRunService.stop(testRun);
        if (!testRun.testRunCompletelyEnded()) {
            this.testRun = testRun;
            return TestRunStatus.ABORTED.statusName;
        }

        this.waitingForReportReady(testRun);
        if (testRun.hasReport) {
            this.reportDownloader.download(testRun, arrayOf("csv", "pdf", "docx"));
        }
        this.testRun = testRun;
        return testRun.statusEnum.statusName;
    }

    fun fetchTrending(testRun: LoadTestRun, benchmark: Int?): TrendingDataWrapper {
        return this.reportDownloader.fetchTrending(testRun, benchmark);
    }
}