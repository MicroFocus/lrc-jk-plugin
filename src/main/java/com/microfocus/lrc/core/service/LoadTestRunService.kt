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
import com.microfocus.lrc.core.Constants
import com.microfocus.lrc.core.entity.*
import com.microfocus.lrc.jenkins.LoggerProxy
import java.io.IOException
import java.lang.Exception

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
            val lt = LoadTest(client.getServerConfiguration().projectId, jsonObj.get("testId").asInt)
            val testRun = LoadTestRun(
                runId.toInt(),
                lt
            )

            testRun.update(jsonObj)

            return testRun
        } else {
            loggerProxy.error("Failed to fetch run $runId: ${response.code}")
            return null
        }
    }

    fun fetch(testRun: LoadTestRun) {
        val apiPath = ApiGetTestRun(
            mapOf("runId" to testRun.id.toString())
        ).path
        val response = client.get(apiPath)
        if (response.isSuccessful) {
            val json = response.body?.string()
            val jsonObj: JsonObject
            try {
                jsonObj = Gson().fromJson(json, JsonObject::class.java)
            } catch (ex: Exception) {
                this.loggerProxy.error("Failed to parse run status")
                this.loggerProxy.debug("Got run status response: $json")
                throw IOException("401")
            }
            testRun.update(jsonObj)
        } else {
            throw IOException("Failed to fetch run ${testRun.id}: ${response.code}")
        }
    }

    fun fetchStatus(testRun: LoadTestRun) {
        val apiPath = ApiGetRunStatus(
            mapOf(
                "projectId" to "${this.client.getServerConfiguration().projectId}",
                "loadTestId" to "${testRun.loadTest.id}",
                "runId" to "${testRun.id}",
            )
        ).path
        val res = this.client.get(apiPath)
        val code = res.code
        if (code != 200) {
            if (code == 401) {
                throw IOException("Unauthorized")
            }

            throw IOException("Failed to fetch status for run ${testRun.id}: $code")
        }
        val body = res.body?.string()
        this.loggerProxy.debug("Fetching test run status got $code, $body")
        val obj = Gson().fromJson(body, JsonObject::class.java)
        testRun.update(obj)
    }

    fun abort(testRun: LoadTestRun) {
        val apiPath = ApiChangeTestRunStatus(
            mapOf(
                "runId" to "${testRun.id}",
            )
        ).path

        val res = this.client.put(apiPath, mapOf("action" to "STOP"), JsonObject())
        val code = res.code
        val body = res.body?.string()
        this.loggerProxy.debug("Aborting test run got $code, $body")
        if (code != 200) {
            this.loggerProxy.info("Aborting test run failed: $code, $body")
            throw IOException("Aborting test run [${testRun.id}] failed")
        }

        this.loggerProxy.info("Aborting test run successfully.")
    }

    fun stop(testRun: LoadTestRun) {
        this.abort(testRun)
        this.loggerProxy.info("Waiting for test run [${testRun.id}] to stop...")

        var retryTimes = 0
        while (retryTimes < Constants.STOP_RUN_POLLING_MAXRETRY && !testRun.statusEnum.isEnded) {
            Thread.sleep(Constants.STOP_RUN_POLLING_INTERVAL)
            this.fetchStatus(testRun)
            retryTimes += 1
        }

        if (testRun.statusEnum.isEnded) {
            this.loggerProxy.info("Test run #${testRun.id} stopped.")
        } else {
            this.loggerProxy.info("Test run #${testRun.id} failed to stop.")
        }
    }
}
