package com.microfocus.lrc.core.service

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.microfocus.lrc.core.ApiClient
import com.microfocus.lrc.core.entity.ApiChangeTestRunStatus
import com.microfocus.lrc.core.entity.ApiGetRunStatus
import com.microfocus.lrc.core.entity.LoadTestRun
import com.microfocus.lrc.jenkins.LoggerProxy
import java.io.IOException

class LoadTestRunService(
    private val client: ApiClient,
    private val loggerProxy: LoggerProxy,
) {

    fun fetchStatus(testRun: LoadTestRun) {
        val apiPath = ApiGetRunStatus(
            mapOf(
                "projectId" to "${this.client.getServerConfiguration().projectId}",
                "loadTestId" to "${testRun.loadTest.id}",
                "runId" to "${testRun.id}",
            )
        ).path;
        val res = this.client.get(apiPath);
        val code = res.code;
        val body = res.body?.string();
        this.loggerProxy.debug("fetch status got $code, $body");
        val obj = Gson().fromJson(body, JsonObject::class.java);
        testRun.update(obj);
    }

    private fun abort(testRun: LoadTestRun) {
        val apiPath = ApiChangeTestRunStatus(
            mapOf(
                "runId" to "${testRun.id}",
            )
        ).path;

        val res = this.client.put(apiPath, mapOf("action" to "STOP"), JsonObject());
        val code = res.code;
        val body = res.body?.string();
        this.loggerProxy.debug("abort got $code, $body");
        if (code != 200) {
            this.loggerProxy.info("abort failed: $code, $body");
            throw IOException("abort test run [${testRun.id}] failed");
        }

        this.loggerProxy.info("aborting test run successfully.");
    }

    fun stop(testRun: LoadTestRun) {
        this.abort(testRun);
        this.loggerProxy.info("waiting for test run [${testRun.id}] to stop...");

        val maxRetry = 5;
        var retryTimes = 0;
        while (retryTimes < maxRetry && !testRun.statusEnum.isEnded) {
            Thread.sleep(3000);
            this.fetchStatus(testRun);
            retryTimes += 1;
        }

        if (testRun.statusEnum.isEnded) {
            this.loggerProxy.info("test run [${testRun.id}] stopped.");
        } else {
            this.loggerProxy.info("test run [${testRun.id}] failed to stop.");
        }
    }
}