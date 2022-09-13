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

import com.google.gson.JsonObject
import com.microfocus.lrc.core.ApiClient
import com.microfocus.lrc.core.Constants
import com.microfocus.lrc.core.entity.ApiGetLoadTest
import com.microfocus.lrc.core.entity.ApiStartTestRun
import com.microfocus.lrc.core.entity.LoadTest
import com.microfocus.lrc.jenkins.LoggerProxy
import com.microfocus.lrc.core.Utils
import java.io.IOException

class LoadTestService(
    private val client: ApiClient,
    private val loggerProxy: LoggerProxy
) {
    fun fetch(id: Int): LoadTest {
        val apiPath = ApiGetLoadTest(
            mapOf(
                "projectId" to "${this.client.getServerConfiguration().projectId}",
                "loadTestId" to "$id"
            )
        ).path
        val res = this.client.get(apiPath)
        val code = res.code
        val bodyString = res.body?.string()
        this.loggerProxy.debug("Fetching load test got response: $code, $bodyString")

        if (res.code == 200) {
            val resObj = Utils.parseJsonString(bodyString, "Failed to parse load test data for #$id")
            val lt = LoadTest(id, this.client.getServerConfiguration().projectId)
            lt.name = resObj.get("name").asString

            return lt
        } else {
            throw IOException("Failed to fetch load test #$id. $code, $bodyString")
        }
    }

    fun startTestRun(id: Int, sendEmail: Boolean): Int {
        val payload = JsonObject()
        val apiPath = ApiStartTestRun(
            mapOf(
                "projectId" to "${this.client.getServerConfiguration().projectId}",
                "loadTestId" to "$id"
            )
        ).path
        val queryParams = mapOf(
            "sendEmail" to sendEmail.toString(),
            "initiator" to Constants.INITIATOR
        )
        val res = this.client.post(apiPath, queryParams, payload)
        val bodyString = res.body?.string()
        if (res.code == 200) {
            val resObj = Utils.parseJsonString(bodyString, "Failed to parse test run data")
            return resObj.get("runId").asInt
        } else {
            throw IOException("Failed to start test run, load test #$id. error: $bodyString")
        }
    }
}
