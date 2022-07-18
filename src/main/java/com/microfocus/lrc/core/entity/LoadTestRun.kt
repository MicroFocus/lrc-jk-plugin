package com.microfocus.lrc.core.entity

import com.google.gson.JsonObject
import java.io.Serializable

class LoadTestRun(
    val id: Int,
    val loadTest: LoadTest
): Serializable {
    val statusCode: Int = 200;
    var hasReport: Boolean = false;
    var detailedStatus: String = "NA";
    var status: String = "NA";
    var isTerminated: Boolean = false;
    val reports: MutableMap<String, ByteArray> = mutableMapOf();
    var startTime: Int = -1;
    var endTime: Int = -1;

    var statusEnum: TestRunStatus = TestRunStatus.NA
        set(value) {
            field = value;
            detailedStatus = value.name;
        };

    fun testRunCompletelyEnded(): Boolean {
        return this.statusEnum.isEnded;
    }

    fun update(json: JsonObject) {
        this.status = json.get("status")?.asString ?: "NA";
        this.detailedStatus = json.get("detailedStatus")?.asString ?: "NA";
        this.isTerminated = json.get("isTerminated")?.asBoolean ?: false;
        this.hasReport = json.get("hasReport")?.asBoolean ?: false;
        try {
            this.statusEnum = TestRunStatus.valueOf(this.detailedStatus);
        } catch (e: IllegalArgumentException) {
            this.statusEnum = TestRunStatus.NA;
        }
    }
}