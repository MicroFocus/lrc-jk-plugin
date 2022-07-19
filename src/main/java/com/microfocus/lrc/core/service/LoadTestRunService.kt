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
import com.microfocus.lrc.core.ApiClient
import com.microfocus.lrc.core.entity.*
import com.microfocus.lrc.jenkins.LoggerProxy
import java.io.IOException

class LoadTestRunService(
    private val client: ApiClient,
    private val loggerProxy: LoggerProxy,
) {
    fun fetch(runId: String): LoadTestRun? {
        val apiPath = ApiGetTestRun(
            mapOf("runId" to runId)
        ).path
        val response = client.get(apiPath)
        if (response.isSuccessful) {
            val json = response.body?.string() ?: return null
            val jsonObj = Gson().fromJson(json, JsonObject::class.java)
            val lt = LoadTest(client.getServerConfiguration().projectId, jsonObj.get("testId").asInt);
            val testRun = LoadTestRun(
                runId.toInt(),
                lt
            )
            testRun.status = jsonObj.get("status").asString;
            testRun.detailedStatus = jsonObj.get("uiStatus").asString;
            testRun.isTerminated = jsonObj.get("isTerminated").asBoolean;

            return testRun;
        } else {
            loggerProxy.error("Failed to fetch run $runId: ${response.code}")
            return null
        }
    }

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

    fun abort(testRun: LoadTestRun) {
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
            Thread.sleep(10000);
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
