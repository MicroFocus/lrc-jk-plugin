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

package com.microfocus.lrc.jenkins

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.microfocus.lrc.core.Constants
import com.microfocus.lrc.core.HTMLTemplate
import com.microfocus.lrc.core.entity.TrendingConfiguration
import com.microfocus.lrc.core.entity.TrendingDataWrapper
import hudson.model.Job
import hudson.model.Run
import jenkins.model.Jenkins
import org.apache.commons.io.IOUtils
import java.io.IOException
import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.function.BiPredicate
import java.util.logging.Level
import java.util.stream.Collectors

class TrendingReport {
    companion object {
        @JvmStatic
        @SuppressWarnings("kotlin:S3776")
        fun generateReport(
            project: Job<*, *>?,
            trendingConfig: TrendingConfiguration?,
            forceUpdate: Boolean,
            extraContent: Boolean
        ): String? {
            //#region parameter validate
            if (project == null) {
                LoggerProxy.sysLogger.log(Level.SEVERE, "'project' is null, failed to generate trending report.")
                return null
            }

            if (trendingConfig == null) {
                LoggerProxy.sysLogger.log(Level.SEVERE, "'trendingConfig' is null, failed to generate trending report.")
                return null
            }
            //#endregion
            LoggerProxy.sysLogger.log(Level.INFO, "Trending report generation starts for project \"${project.name}\"")

            var latestBuild: Run<*, *>? = null

            //#region get the latest build has trending data
            val latestBuilds: List<Run<*, *>> =
                this.findValidBuilds(project.lastBuild, 1) { build, _ ->
                    this.isHavingTrendingData(build)
                }
            if (latestBuilds.size == 1) {
                latestBuild = latestBuilds[0]
            }

            if (latestBuild == null) {
                LoggerProxy.sysLogger.log(Level.INFO, "No valid build found, failed to generate trending report.")
                return null
            }

            @SuppressWarnings("kotlin:S1874")
            val latestBuildAction: TestRunReportBuildAction? =
                latestBuild.getAction(TestRunReportBuildAction::class.java)
            if (latestBuildAction == null) {
                LoggerProxy.sysLogger.log(
                    Level.SEVERE,
                    "No buildAction attached on the latest build, failed to generate trending report."
                )
                return null
            }

            LoggerProxy.sysLogger.log(Level.FINE, "Latest valid build found: build#" + latestBuild.getNumber())
            //#endregion
            val generatorLogs = StringBuilder()

            //#region check if report has been generated for the latestBuild
            // and if trendingConfig is changed since last generating.
            val cachedHTML = this.findCachedHTML(latestBuildAction, trendingConfig, forceUpdate)
            if (cachedHTML != null) {
                return cachedHTML
            }
            //#endregion


            //this list will at least have ONE item, the "latestBuild" itself
            val latestBuildsHasTrendingDataAndSameTestId: List<Run<*, *>> =
                this.findValidBuilds(
                    latestBuild,
                    trendingConfig.runsCount
                ) { build: Run<*, *>?, baseBuild: Run<*, *>? ->
                    this.isHavingTrendingDataAndSameTestId(
                        build,
                        baseBuild
                    )
                }

            LoggerProxy.sysLogger.log(
                Level.FINE,
                buildString {
                    append("Totally ${latestBuildsHasTrendingDataAndSameTestId.size} builds found: ")
                    append(latestBuildsHasTrendingDataAndSameTestId.stream().map { build: Run<*, *> ->
                        "#${build.getNumber()}"
                    }.reduce("") { a: String, b: String -> "$a, $b" })
                }
            )

            var latestBenchmark: TrendingDataWrapper.TrendingData? = latestBuildAction.trendingDataWrapper.benchmark
            if (latestBenchmark == null) {
                LoggerProxy.sysLogger.log(
                    Level.INFO, "Latest benchmark is null, choose the run itself as benchmark."
                )
                latestBenchmark = latestBuildAction.trendingDataWrapper.trendingData!!
            }
            val benchmark: TrendingDataWrapper.TrendingData = latestBenchmark
            LoggerProxy.sysLogger.log(
                Level.INFO, "Benchmark is: run #${benchmark.runId}"
            )
            val dateFormat = SimpleDateFormat("yyyy.MM.dd HH:mm:ss")

            //{buildId: number, data: TrendingDataWrapper}[]

            val trendingDataWrapperList: List<JsonObject> =
                latestBuildsHasTrendingDataAndSameTestId.stream().map { b: Run<*, *> ->
                    val tempTrd = JsonObject()
                    @SuppressWarnings("kotlin:S1874")
                    val trendingDataWrapper: TrendingDataWrapper =
                        b.getAction(TestRunReportBuildAction::class.java).trendingDataWrapper
                    tempTrd.addProperty("data", Gson().toJson(trendingDataWrapper))
                    tempTrd.addProperty("buildId", b.getNumber())
                    tempTrd.addProperty("buildDate", dateFormat.format(b.time))
                    tempTrd
                }.collect(Collectors.toList())

            val overviews = JsonArray()
            trendingDataWrapperList.stream().map { jsonObject: JsonObject ->
                getOverviewFromTrendingData(
                    jsonObject
                )
            }.collect(Collectors.toList()).forEach { overview: JsonObject ->
                overviews.add(overview)
            }

            val trts = JsonArray()
            val transactionsGroup: Map<Pair<String, String>, List<JsonObject>> =
                trendingDataWrapperList.stream().flatMap { t: JsonObject ->
                    (Gson().fromJson(
                        t.get("data").asString,
                        TrendingDataWrapper::class.java
                    )).trendingData.transactions.stream()
                        .map { trans ->
                            val tempTrans = JsonObject()
                            val trendingDataWrapper: TrendingDataWrapper =
                                Gson().fromJson(t.get("data").asString, TrendingDataWrapper::class.java)
                            tempTrans.addProperty("data", Gson().toJson(trans))
                            tempTrans.addProperty("buildId", t.get("buildId").asInt)
                            tempTrans.addProperty("runId", trendingDataWrapper.trendingData.runId)
                            tempTrans.addProperty("percentile", trendingDataWrapper.trendingData.percentile)
                            tempTrans
                        }
                }.collect(
                    Collectors.groupingBy { transJSON ->
                        val trans: TrendingDataWrapper.TransactionData =
                            Gson().fromJson(
                                transJSON.get("data").asString,
                                TrendingDataWrapper.TransactionData::class.java
                            )
                        return@groupingBy Pair(trans.name, trans.script)
                    }
                )

            transactionsGroup.forEach { (transScriptName: Pair<String?, String?>, transJSONList: List<JsonObject>) ->
                generatorLogs.append(
                    String.format(
                        "processing transaction group: %1\$s - %2\$s%n",
                        transScriptName.first,
                        transScriptName.second
                    )
                )
                val trtGroup = JsonObject()
                trtGroup.addProperty("transactionName", transScriptName.first)
                trtGroup.addProperty("scriptName", transScriptName.second)
                trtGroup.add("trtDataArr", JsonArray())
                //transJSONList: {buildId, runId, percentile, data: TransactionData}[]
                transJSONList.stream()
                    .map { transJSON: JsonObject ->
                        generatorLogs.append(
                            java.lang.String.format(
                                "\t\tprocessing test run: %d%n",
                                transJSON.get("runId").asInt
                            )
                        )
                        //here is the default benchmark transaction
                        var benchmarkTrans: TrendingDataWrapper.TransactionData? = benchmark.transactions.stream()
                            .filter { t -> (transScriptName.first == t.name) && (transScriptName.second == t.script) }
                            .findAny().orElse(null)
                        var benchmarkRunId: Int = benchmark.runId
                        if (latestBuildAction.trendingDataWrapper.benchmarkId == null) {
                            generatorLogs.append("\t\t\t\tbenchmark is set to 'Previous'\n")
                            //benchmarkId == null means that the "Benchmark" is set to "Previous"
                            //so the benchmark for each transactionData should be the "trending data of previous build"
                            val prevData: Pair<Int, TrendingDataWrapper.TransactionData?>? =
                                this.getPreviousRunTrans(
                                    transJSONList,
                                    transJSON.get("runId").asInt
                                )
                            if ((prevData != null) && (prevData.second != null)) {
                                generatorLogs.append(
                                    "\t\t\t\tbenchmark found: testrun#${prevData.first}",
                                )
                                benchmarkTrans = prevData.second
                                benchmarkRunId = prevData.first
                            } else {
                                generatorLogs.append("\t\t\t\tbenchmark not found: use the run itself.\n")
                                benchmarkTrans = Gson().fromJson(
                                    transJSON.get("data").asString,
                                    TrendingDataWrapper.TransactionData::class.java
                                )
                                benchmarkRunId = transJSON.get("runId").asInt
                            }
                        } else {
                            generatorLogs.append(
                                ("\t\t\t\tbenchmark is " + latestBuildAction.trendingDataWrapper
                                    .benchmarkId) + "\n"
                            )
                            if (benchmarkTrans == null) {
                                generatorLogs.append(
                                    String.format(
                                        "\t\t\t\tcannot find benchmark for %1\$s - %2\$s%n",
                                        transScriptName.first,
                                        transScriptName.second
                                    )
                                )
                            }
                        }
                        val trt = calculateTRT(
                            transJSON.get("runId").asInt,
                            benchmarkRunId,
                            transJSON.get("buildId").asInt,
                            transJSON.get("percentile").asInt,
                            Gson().fromJson(
                                transJSON.get("data").asString,
                                TrendingDataWrapper.TransactionData::class.java
                            ),
                            benchmarkTrans,
                            trendingConfig
                        )
                        trt
                    }.forEach { trtJSON ->
                        trtGroup.getAsJsonArray("trtDataArr").add(trtJSON)
                    }
                trts.add(trtGroup)
            }

            val data = JsonObject()
            data.addProperty("baseURL", Jenkins.getInstanceOrNull()?.rootUrl + "plugin/jenkinsStormPlugin/")

            data.addProperty("trtAvgTholdImpr", trendingConfig.trtAvgTholdImpr)
            data.addProperty("trtAvgTholdMinorRegr", trendingConfig.trtAvgTholdMinorRegr)
            data.addProperty("trtAvgTholdMajorRegr", trendingConfig.trtAvgTholdMajorRegr)
            data.addProperty("trtPctlTholdImpr", trendingConfig.trtPctlTholdImpr)
            data.addProperty("trtPctlTholdMinorRegr", trendingConfig.trtPctlTholdMinorRegr)
            data.addProperty("trtPctlTholdMajorRegr", trendingConfig.trtPctlTholdMajorRegr)

            data.addProperty("pageTitle", "LoadRunner Cloud Test Runs Trending Report")

            data.add("data", JsonObject())
            data.get("data").asJsonObject.add("metrics", overviews)
            data.get("data").asJsonObject.add("trt", trts)
            data.addProperty("testId", latestBuildAction.trendingDataWrapper.trendingData.testId)
            data.addProperty("testName", latestBuildAction.trendingDataWrapper.trendingData.testName)
            data.addProperty(Constants.BENCHMARK, latestBuildAction.trendingDataWrapper.benchmarkId)
            data.addProperty("generatorLogs", generatorLogs.toString())
            data.addProperty("extraContent", extraContent)
            val slotContent: Map<String, String>
            val htmlTemplate: String
            try {
                slotContent = mapOf(
                    "pureCss" to IOUtils.toString(
                        TestRunBuilder::class.java.classLoader
                            .getResourceAsStream("trending_report/pure.min.css"), StandardCharsets.UTF_8
                    ),
                    "lodashjs" to IOUtils.toString(
                        TestRunBuilder::class.java.classLoader
                            .getResourceAsStream("trending_report/lodash.min.js"), StandardCharsets.UTF_8
                    ),
                    "momentjs" to IOUtils.toString(
                        TestRunBuilder::class.java.classLoader
                            .getResourceAsStream("trending_report/moment.min.js"), StandardCharsets.UTF_8
                    )
                )
                slotContent.forEach(data::addProperty)
                htmlTemplate = IOUtils.toString(
                    TestRunBuilder::class.java.classLoader.getResourceAsStream("trending_report/run_report.twig"),
                    StandardCharsets.UTF_8
                )
            } catch (e: IOException) {
                LoggerProxy.sysLogger
                    .log(Level.SEVERE, "Failed to load resource files for trending report, " + e.message)
                return null
            }

            return try {
                HTMLTemplate.generateByPebble(htmlTemplate, data)
            } catch (e: IOException) {
                LoggerProxy.sysLogger.log(Level.SEVERE, "Failed to generate html, " + e.message)
                "failed to generate"
            }
        }

        private fun findCachedHTML(
            latestBuildAction: TestRunReportBuildAction,
            trendingConfig: TrendingConfiguration,
            forceUpdate: Boolean
        ): String? {
            if (latestBuildAction.trendingReportHTML == null) {
                LoggerProxy.sysLogger.log(Level.INFO, "Cached trending report not found, generating.")
                return null
            }

            val lastTrendingConfig: TrendingConfiguration = latestBuildAction.trendingConfig
            return if (this.isSameTrendingConfig(lastTrendingConfig, trendingConfig) && !forceUpdate) {
                LoggerProxy.sysLogger.log(
                    Level.INFO,
                    "Cached trending report found and trending config is not changed"
                )
                latestBuildAction.trendingReportHTML
            } else {
                LoggerProxy.sysLogger.log(
                    Level.INFO,
                    "Cached trending report found but trending config is changed, re-generating."
                )
                null
            }
        }

        private fun findValidBuilds(
            start: Run<*, *>?,
            buildCount: Int,
            isValid: BiPredicate<Run<*, *>?, Run<*, *>?>
        ): List<Run<*, *>> {
            val list: MutableList<Run<*, *>> = ArrayList()
            var lastBuild = start
            while (list.size < buildCount && lastBuild != null) {
                if (isValid.test(lastBuild, start)) {
                    list.add(lastBuild)
                }
                lastBuild = lastBuild.previousBuild
            }
            return list
        }

        private fun isHavingTrendingData(build: Run<*, *>?): Boolean {
            @SuppressWarnings("kotlin:S1874")
            val trendingAction: TestRunReportBuildAction? = build?.getAction(TestRunReportBuildAction::class.java)
            val size = trendingAction?.trendingDataWrapper?.trendingData?.transactions?.size ?: 0
            return size > 0
        }

        private fun isSameTrendingConfig(configA: TrendingConfiguration, configB: TrendingConfiguration): Boolean {
            return configA.runsCount.equals(configB.runsCount) &&
                    configA.trtAvgTholdImpr.equals(configB.trtAvgTholdImpr) &&
                    configA.trtAvgTholdMajorRegr.equals(configB.trtAvgTholdMajorRegr) &&
                    configA.trtAvgTholdMinorRegr.equals(configB.trtAvgTholdMinorRegr) &&
                    configA.trtPctlTholdImpr.equals(configB.trtPctlTholdImpr) &&
                    configA.trtPctlTholdMajorRegr.equals(configB.trtPctlTholdMajorRegr) &&
                    configA.trtPctlTholdMinorRegr.equals(configB.trtPctlTholdMinorRegr)
        }

        private fun isHavingTrendingDataAndSameTestId(build: Run<*, *>?, baseBuild: Run<*, *>?): Boolean {
            if (!this.isHavingTrendingData(build)) {
                return false
            }
            @SuppressWarnings("kotlin:S1874")
            val trendingAction = build?.getAction(TestRunReportBuildAction::class.java)
                ?: return false

            @SuppressWarnings("kotlin:S1874")
            val baseTrendingAction: TestRunReportBuildAction =
                baseBuild?.getAction(TestRunReportBuildAction::class.java)
                    ?: return false
            val testId: Int = trendingAction.trendingDataWrapper.trendingData.testId
            val baseTestId: Int = baseTrendingAction.trendingDataWrapper.trendingData.testId
            val tenantId: String = trendingAction.trendingDataWrapper.tenantId
            val baseTenantId: String = baseTrendingAction.trendingDataWrapper.tenantId

            return (testId == baseTestId) && (tenantId == baseTenantId)
        }

        private fun getOverviewFromTrendingData(jsonObject: JsonObject): JsonObject {
            val trendingData: TrendingDataWrapper.TrendingData =
                Gson().fromJson(jsonObject.get("data").asString, TrendingDataWrapper::class.java).trendingData
            val overview = JsonObject()
            overview.addProperty("runId", trendingData.runId)
            overview.addProperty("buildNo", jsonObject.get("buildId").asInt)
            this.putNumberSafely(overview, "avgHits", trendingData.avgHits)
            this.putNumberSafely(
                overview,
                "avgThroughput",
                trendingData.avgThroughput
            )
            this.putNumberSafely(
                overview,
                "errorsPerSec",
                trendingData.errorsPerSec
            )
            this.putNumberSafely(overview, "duration", trendingData.duration)
            this.putNumberSafely(
                overview,
                "initDuration",
                trendingData.initDuration.toDouble()
            )
            overview.addProperty("vusers", trendingData.vusers)
            this.putNumberSafely(
                overview,
                "tps",
                (trendingData.totalTxPassed + trendingData.totalTxFailed).toDouble() / trendingData.duration
            )
            overview.addProperty("totalTxPassed", trendingData.totalTxPassed)
            overview.addProperty("totalTxFailed", trendingData.totalTxFailed)

            overview.addProperty("status", trendingData.status)
            val startTime = jsonObject.get("buildDate").asString
            overview.addProperty("date", startTime)
            return overview
        }

        private fun putNumberSafely(json: JsonObject, key: String, value: Double) {
            var safeVal = value
            if (java.lang.Double.isInfinite(safeVal) || java.lang.Double.isNaN(safeVal)) {
                safeVal = 0.0
            }
            json.addProperty(key, safeVal)
        }

        private fun getPreviousRunTrans( //JSONObject: {buildId, runId, percentile, data: TransactionData}
            transJSONList: List<JsonObject>,
            currentRunId: Int
        ): Pair<Int, TrendingDataWrapper.TransactionData?>? {
            //the "previous" is the max run id smaller than the current one.
            val prevRunId = transJSONList.stream()
                .map { trans: JsonObject -> trans.get("runId").asInt }
                .filter { x -> x < currentRunId }
                .max(Comparator.naturalOrder())
                .orElse(null) ?: return null

            val prevTrans: JsonObject = transJSONList.stream()
                .filter { x: JsonObject -> x.get("runId").asInt == prevRunId }
                .findFirst()
                .get()
            val prevTransactionData: TrendingDataWrapper.TransactionData = Gson().fromJson(
                prevTrans.get("data").asString, TrendingDataWrapper.TransactionData::class.java
            )
            return Pair(prevRunId, prevTransactionData)
        }

        private fun calculateTRT(
            runId: Int,
            benchmarkRunId: Int,
            buildId: Int,
            percentile: Int,
            transaction: TrendingDataWrapper.TransactionData,
            benchmarkTrans: TrendingDataWrapper.TransactionData?,
            trendingConfig: TrendingConfiguration
        ): JsonObject {
            var benchmarkTx = benchmarkTrans
            val trt = JsonObject()
            if (benchmarkTx == null) {
                //use itself as benchmark if no match (same script/transaction) benchmark is found.
                benchmarkTx = transaction
            }
            trt.addProperty("runId", runId)
            trt.addProperty("buildNo", buildId)
            trt.addProperty("percentile", percentile)
            putNumberSafely(trt, "avg", transaction.avg)
            var avgD = BigDecimal(trt.get("avg").asDouble)
            avgD = avgD.setScale(3, RoundingMode.HALF_UP)
            trt.addProperty("avg", avgD)
            putNumberSafely(trt, "ninetieth", transaction.nintieth)
            var ninetiethD = BigDecimal(trt.get("ninetieth").asDouble)
            ninetiethD = ninetiethD.setScale(3, RoundingMode.HALF_UP)
            trt.addProperty("ninetieth", ninetiethD)
            trt.addProperty("avgTrend", 0)
            trt.addProperty("isAvgImpr", false)
            trt.addProperty("isAvgMinorRegr", false)
            trt.addProperty("isAvgMajorRegr", false)
            trt.addProperty("ninetiethTrend", 0)
            trt.addProperty("is90thImpr", false)
            trt.addProperty("is90thMinorRegr", false)
            trt.addProperty("is90thMajorRegr", false)
            val avgTrend: Double = calculateTrend(transaction.avg, benchmarkTx.avg)
            val avgTrendCalc = java.lang.String.format(
                "(%1\$f - %3\$f) / %3\$f",
                transaction.avg,  //%1
                runId,  //%2
                benchmarkTx.avg,  //%3
                benchmarkRunId //%4
            )
            putNumberSafely(trt, "avgTrend", avgTrend)
            var avgTrendD = BigDecimal(trt.get("avgTrend").asDouble)
            avgTrendD = avgTrendD.setScale(0, RoundingMode.HALF_UP)
            trt.addProperty("avgTrend", avgTrendD)
            trt.addProperty("avgTrendCalc", avgTrendCalc)
            trt.addProperty("isAvgImpr", avgTrend < trendingConfig.trtAvgTholdImpr * -1)
            trt.addProperty(
                "isAvgMinorRegr",
                avgTrend < trendingConfig.trtAvgTholdMajorRegr
                        && avgTrend > trendingConfig.trtAvgTholdMinorRegr
            )
            trt.addProperty("isAvgMajorRegr", avgTrend > trendingConfig.trtAvgTholdMajorRegr)
            val ninetiethTrend: Double = calculateTrend(
                transaction.nintieth,
                benchmarkTx.nintieth
            )
            val ninetiethTrendCalc = java.lang.String.format(
                "(%1\$f - %3\$f) / %3\$f",
                transaction.nintieth,  //%1
                runId,  //%2
                benchmarkTx.nintieth,  //%3
                benchmarkRunId //%4
            )
            putNumberSafely(trt, "ninetiethTrend", ninetiethTrend)
            var ninetiethTrendD = BigDecimal(trt.get("ninetiethTrend").asDouble)
            ninetiethTrendD = ninetiethTrendD.setScale(0, RoundingMode.HALF_UP)
            trt.addProperty("ninetiethTrend", ninetiethTrendD)
            trt.addProperty("ninetiethTrendCalc", ninetiethTrendCalc)
            trt.addProperty("is90thImpr", ninetiethTrend < trendingConfig.trtPctlTholdImpr * -1)
            trt.addProperty(
                "is90thMinorRegr",
                ninetiethTrend < trendingConfig.trtPctlTholdMajorRegr && ninetiethTrend > trendingConfig.trtPctlTholdMinorRegr
            )
            trt.addProperty("is90thMajorRegr", ninetiethTrend > trendingConfig.trtPctlTholdMajorRegr)
            return trt
        }

        private fun calculateTrend(a: Double, b: Double): Double {
            var res = (a - b) / b * 100
            if (java.lang.Double.isInfinite(res) || java.lang.Double.isNaN(res)) {
                res = 0.0
            }
            return res
        }
    }
}
