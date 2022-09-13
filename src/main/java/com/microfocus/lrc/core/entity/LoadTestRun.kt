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

package com.microfocus.lrc.core.entity

import com.google.gson.JsonObject
import java.io.InputStream
import java.io.Serializable

class LoadTestRun(
    val id: Int,
    val loadTest: LoadTest
): Serializable {
    val statusCode: Int = 200
    var hasReport: Boolean = false
    var detailedStatus: String = "NA"
    var status: String = "NA"
    var isTerminated: Boolean = false
    val reports: MutableMap<String, Int> = mutableMapOf()
    val reportsByteArray: MutableMap<String, ByteArray> = mutableMapOf()
    var startTime: Long = -1
    var endTime: Long = -1

    var statusEnum: TestRunStatus = TestRunStatus.NA
        set(value) {
            field = value
            detailedStatus = value.name
        }

    fun testRunCompletelyEnded(): Boolean {
        return this.statusEnum.isEnded && this.isTerminated
    }

    fun update(json: JsonObject) {
        this.status = json.get("status")?.asString ?: "NA"
        this.detailedStatus = json.get("uiStatus")?.asString ?: (json.get("detailedStatus")?.asString ?: "NA")

        if (json.has("isTerminated")) {
            this.isTerminated = json.get("isTerminated").asBoolean
        }
        if (json.has("hasReport")) {
            this.hasReport = json.get("hasReport").asBoolean
        }
        if (json.has("startTime")) {
            this.startTime = json.get("startTime").asString.toLong()
        }
        if (json.has("endTime")) {
            this.endTime = json.get("endTime").asString.toLong()
        }

        try {
            this.statusEnum = TestRunStatus.valueOf(this.detailedStatus)
        } catch (e: IllegalArgumentException) {
            this.statusEnum = TestRunStatus.NA
        }
    }
}
