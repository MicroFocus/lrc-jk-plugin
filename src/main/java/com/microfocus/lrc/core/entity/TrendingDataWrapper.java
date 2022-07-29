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

package com.microfocus.lrc.core.entity;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.microfocus.lrc.core.Constants;
import com.microfocus.lrc.core.JsonObj;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Trending data, including the current run's and the benchmark's
 * <p>
 * Will be persisted by Jenkins as a member field of StormTestReportBuildAction.
 */

public final class TrendingDataWrapper implements Serializable {

    static final long serialVersionUID = 1L;

    private TrendingData trendingData;
    private TrendingData benchmark;
    private Integer benchmarkId;
    private String tenantId;

    public TrendingDataWrapper(
            final JsonObject json,
            final String tenantId,
            final Integer benchmarkId
    ) {
        this.trendingData = new TrendingData(json);
        this.benchmarkId = benchmarkId;
        this.tenantId = tenantId;
        if (json.has(Constants.BENCHMARK) && json.get(Constants.BENCHMARK) != null) {
            try {
                this.benchmark = new TrendingData(json.get(Constants.BENCHMARK).getAsJsonObject());
            } catch (Exception e) {
                //ignore
            }
        }
    }

    public TrendingDataWrapper(
            final int runId,
            final String tenantId,
            final Integer benchmarkId,
            final String runStatus
    ) {
        this.trendingData = new TrendingData(runId, runStatus);
        this.benchmarkId = benchmarkId;
        this.tenantId = tenantId;
    }

    public TrendingDataWrapper(
            final LoadTestRun testRun,
            final TestRunResultsResponse results,
            final TestRunTransactionsResponse[] tx,
            final String tenantId,
            final TrendingDataWrapper benchmark
    ) {
        this.tenantId = tenantId;
        this.trendingData = new TrendingData(testRun, results, tx);
        if (benchmark != null) {
            this.benchmark = benchmark.getTrendingData();
            this.benchmarkId = benchmark.getBenchmarkId();
        }
    }

    public TrendingData getTrendingData() {
        return trendingData;
    }

    public TrendingData getBenchmark() {
        return benchmark;
    }

    public Integer getBenchmarkId() {
        return benchmarkId;
    }

    public String getTenantId() {
        return tenantId;
    }


    public final class TransactionData implements Serializable {
        static final long serialVersionUID = 1L;
        private String name;
        private String script;
        private double min;
        private double max;
        private double avg;
        private double nintieth;
        private double breakers;
        private double thresholds;

        //#region accessors
        public String getName() {
            return name;
        }

        public void setName(final String name) {
            this.name = name;
        }

        public String getScript() {
            return script;
        }

        public void setScript(final String script) {
            this.script = script;
        }

        public double getMin() {
            return min;
        }

        public void setMin(final double min) {
            this.min = min;
        }

        public double getMax() {
            return max;
        }

        public void setMax(final double max) {
            this.max = max;
        }

        public double getAvg() {
            return avg;
        }

        public void setAvg(final double avg) {
            this.avg = avg;
        }

        public double getNintieth() {
            return nintieth;
        }

        public void setNintieth(final double nintieth) {
            this.nintieth = nintieth;
        }

        public double getBreakers() {
            return breakers;
        }

        public void setBreakers(final double breakers) {
            this.breakers = breakers;
        }

        public double getThresholds() {
            return thresholds;
        }

        public void setThresholds(final double thresholds) {
            this.thresholds = thresholds;
        }
        //#endregion

        public TransactionData(final JsonObject json) {
            JsonObj obj = new JsonObj(json);
            this.name = json.get("transaction").getAsString();
            this.script = json.get("script").getAsString();
            this.min = obj.optDouble("min", 0);
            this.max = obj.optDouble("max", 0);
            this.avg = obj.optDouble("avg", 0);
            this.nintieth = obj.optDouble("nintieth", 0);
            this.breakers = obj.optDouble("breakers", 0);
            this.thresholds = obj.optDouble("thresholds", 0);
        }

        public TransactionData(final TestRunTransactionsResponse tx) {
            this.name = tx.getName();
            this.script = tx.getScriptName();
            this.min = tx.getMinTRT();
            this.max = tx.getMaxTRT();
            this.avg = tx.getAvgTRT();
            this.nintieth = tx.getPercentileTRT();
            this.breakers = tx.getBreakers();
            this.thresholds = tx.getSlaThreshold();
        }
    }

    public final class TrendingData implements Serializable {
        static final long serialVersionUID = 1L;
        private int initDuration = -1;
        private int runId;
        private int testId;
        private String testName;
        private String status;
        private int vusers;
        private double duration;
        private int percentile;
        private double avgThroughput;
        private double totalThroughput;
        private double avgHits;
        private double totalHits;
        private int totalTxPassed;
        private int totalTxFailed;
        private double errorsPerSec;
        private String startTime;
        private List<TransactionData> transactions;

        //#region accessors
        public int getRunId() {
            return runId;
        }
        public double getDuration() {
            return duration;
        }

        public int getPercentile() {
            return percentile;
        }

        public double getAvgThroughput() {
            return avgThroughput;
        }

        public double getTotalThroughput() {
            return totalThroughput;
        }

        public double getAvgHits() {
            return avgHits;
        }

        public double getTotalHits() {
            return totalHits;
        }

        public int getTotalTxPassed() {
            return totalTxPassed;
        }

        public int getTotalTxFailed() {
            return totalTxFailed;
        }

        public double getErrorsPerSec() {
            return errorsPerSec;
        }

        public List<TransactionData> getTransactions() {
            return transactions;
        }
        //#endregion

        public TrendingData(final JsonObject json) {
            this.runId = json.get("runId").getAsInt();
            JsonObject rpt = json.get("rpt").getAsJsonObject();
            JsonObj obj = new JsonObj(rpt);
            JsonObj data = new JsonObj(json);
            this.duration = obj.optDouble("duration", -1);
            this.initDuration = obj.optInt("initDuration", -1);
            this.percentile = obj.optInt("percentile", 0);
            this.avgThroughput = obj.optDouble("avgThroughput", 0);
            this.totalThroughput = obj.optDouble("totalThroughput", 0);
            this.avgHits = obj.optDouble("avgHits", 0);
            this.totalHits = obj.optDouble("totalHits", 0);
            this.totalTxPassed = obj.optInt("totalTxPassed", 0);
            this.totalTxFailed = obj.optInt("totalTxFailed", 0);
            this.errorsPerSec = obj.optDouble("errorsPerSec", 0);
            this.status = obj.optString("status");

            this.testId = data.optInt("testId", 0);
            this.testName = data.optString("testName");
            this.startTime = data.optString("startTime");
            this.vusers = data.optInt("vusers", 0);
            JsonArray transactionsArr = json.get("slaData").getAsJsonArray();
            this.transactions = new ArrayList<>();
            for (int i = 0; i < transactionsArr.size(); i++) {
                JsonObject t = transactionsArr.get(i).getAsJsonObject();
                this.transactions.add(new TransactionData(t));
            }

            //TODO: trending calculation
        }

        public TrendingData(final int runId, final String runStatus) {
            this.runId = runId;
            this.status = runStatus;
        }

        public TrendingData(
                final LoadTestRun testRun,
                final TestRunResultsResponse results,
                final TestRunTransactionsResponse[] tx
        ) {
            this.runId = testRun.getId();
            this.avgHits = results.getAvgHitsNum();
            this.avgThroughput = results.getAvgThroughputWithOutUnit();
            this.totalThroughput = results.getTotalThroughputWithOutUnit();
            this.duration = results.getDurationInSec();
            this.errorsPerSec = results.getErrorsPerSec();
            this.percentile = results.getPercentileValue();
            this.status = results.getStatus();
            this.startTime = String.valueOf(testRun.getStartTime());
            this.testId = testRun.getLoadTest().getId();
            this.testName = testRun.getLoadTest().getName();
            this.totalHits = results.getTotalHits();
            this.totalTxFailed = results.getTotalTransactionsFailed();
            this.totalTxPassed = results.getTotalTransactionsPassed();
            this.vusers = results.getTotalVusers();
            this.transactions = new ArrayList<>();
            for (TestRunTransactionsResponse txItem : tx) {
                this.transactions.add(new TransactionData(txItem));
            }
        }

        public int getTestId() {
            return testId;
        }

        public String getTestName() {
            return testName;
        }

        public String getStatus() {
            return status;
        }

        public int getVusers() {
            return vusers;
        }

        public String getStartTime() {
            return startTime;
        }

        public int getInitDuration() {
            return initDuration;
        }

        public void setInitDuration(final int initDuration) {
            this.initDuration = initDuration;
        }
    }
}
