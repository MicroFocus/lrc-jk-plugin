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
import com.microfocus.lrc.core.entity.*
import org.junit.Test

class ReportDownloaderTest {

    @Test
    fun buildTrendingObj() {
        val resultsStr =
            "{\"dateTime\":\"06/22/2022 12:54 GMT\",\"status\":\"PASSED\",\"duration\":\"00:06:31\",\"delayDuration\":null,\"apiVusers\":2,\"uiVusers\":0,\"devVusers\":0,\"erpVusers\":0,\"legacyVusers\":0,\"mobileVusers\":0,\"runMode\":\"Duration\",\"excludeThinkTime\":false,\"schedulingPauseDuration\":0,\"percentileValue\":90,\"totalVusers\":2,\"averageThroughput\":\"N/A/s\",\"totalThroughput\":\"N/A\",\"averageHits\":\"0 hits/s\",\"totalHits\":null,\"averageBytesSent\":null,\"totalBytesSent\":null,\"totalTransactionsPassed\":5,\"totalTransactionsFailed\":0,\"failedVusers\":0,\"trtBreakersPassed\":0,\"trtBreakersFailed\":0,\"scriptErrors\":0,\"lgAlerts\":0,\"totalVU\":0,\"totalVUH\":0,\"apiVUH\":0,\"uiVUH\":0,\"devVUH\":0,\"erpVUH\":0,\"legacyVUH\":0,\"mobileVUH\":0,\"allVUH\":0,\"percentileAlgorithm\":\"Absolute\"}"
        val resultObj = Gson().fromJson(resultsStr, TestRunResultsResponse::class.java)

        val transactionStr =
            "[{\"name\":\"Actions_Transaction\",\"loadTestScriptId\":4403,\"scriptName\":\"Kafka3_updated (1)\",\"breakers\":0,\"slaStatus\":\"N/A\",\"slaThreshold\":null,\"slaTrend\":0.08174046321674466,\"passed\":2,\"failed\":0,\"avgTRT\":10.030542016029358,\"minTRT\":10.023746013641357,\"maxTRT\":10.037338018417358,\"percentileTRT\":10.037338018417358,\"stdDeviation\":0.006796002388000488},{\"name\":\"vuser_end_Transaction\",\"loadTestScriptId\":4403,\"scriptName\":\"Kafka3_updated (1)\",\"breakers\":0,\"slaStatus\":\"N/A\",\"slaThreshold\":null,\"slaTrend\":-2303.2204978038067,\"passed\":1,\"failed\":0,\"avgTRT\":30.017520904541016,\"minTRT\":30.017520904541016,\"maxTRT\":30.017520904541016,\"percentileTRT\":30.017520904541016,\"stdDeviation\":0},{\"name\":\"vuser_init_Transaction\",\"loadTestScriptId\":4404,\"scriptName\":\"Kafka2\",\"breakers\":0,\"slaStatus\":\"N/A\",\"slaThreshold\":null,\"slaTrend\":-0.012488121159375871,\"passed\":1,\"failed\":0,\"avgTRT\":9.407335042953491,\"minTRT\":9.407335042953491,\"maxTRT\":9.407335042953491,\"percentileTRT\":9.407335042953491,\"stdDeviation\":0},{\"name\":\"vuser_init_Transaction\",\"loadTestScriptId\":4403,\"scriptName\":\"Kafka3_updated (1)\",\"breakers\":0,\"slaStatus\":\"N/A\",\"slaThreshold\":null,\"slaTrend\":-0.023571880733480634,\"passed\":1,\"failed\":0,\"avgTRT\":9.402602910995483,\"minTRT\":9.402602910995483,\"maxTRT\":9.402602910995483,\"percentileTRT\":9.402602910995483,\"stdDeviation\":0}]"
        val transactionObj = Gson().fromJson(transactionStr, Array<TestRunTransactionsResponse>::class.java)

        val loadTest = LoadTest(2238, 2)
        val testRun = LoadTestRun(2835, loadTest)

        val trending = TrendingDataWrapper(
            testRun,
            resultObj,
            transactionObj,
            "FAKE_TENANT_ID",
            null
        )

        assert(trending.trendingData.percentile == resultObj.percentileValue)
    }

    @Test
    fun jsonTransform() {
        val loadTest = LoadTest(113, 1)
        val jsonStr = Gson().toJson(loadTest)
        val loadTest2 = Gson().fromJson(jsonStr, LoadTest::class.java)
        assert(loadTest2.id == loadTest.id)

        val str = "{\"trendingData\":{\"initDuration\":-1,\"runId\":781,\"testId\":113,\"testName\":\"TEST for TFS\",\"status\":\"PASSED\",\"vusers\":1,\"duration\":85.0,\"percentile\":90,\"avgThroughput\":136151.04,\"totalThroughput\":8849981.44,\"avgHits\":2.123,\"totalHits\":138.0,\"totalTxPassed\":35,\"totalTxFailed\":0,\"errorsPerSec\":0.0,\"startTime\":\"-1\",\"transactions\":[{\"name\":\"Peacefull_FF\",\"script\":\"TC_peacefull_12.56_FF_20_pacing\",\"min\":0.4470002353191376,\"max\":0.5929999351501465,\"avg\":0.5087713599205017,\"nintieth\":0.5849998593330383,\"breakers\":0.0,\"thresholds\":3.0}]},\"tenantId\":\"516042910\"}"
        val trendingDataWrapper = Gson().fromJson(str, TrendingDataWrapper::class.java)
        assert(trendingDataWrapper.trendingData.runId == 781)
    }

    @Test
    fun writeTxToCsvBytes() {
        val tx = TestRunTransactionsResponse(
            "Peacefull_FF",
            -1,
            "TC_peacefull_12.56_FF_20_pacing",
            0.44700024F,
            0.59299994F,
            0.50877136F,
            0.58499986F,
            0.0F,
            "N/A",
            3,
            0.11051371F,
            90,
            0,
            -0.013767751F
        )
        val csvBytes = ReportDownloader.writeCsvBytesArray(arrayOf(tx))
        println(csvBytes.toString(Charsets.UTF_8))
    }
}
