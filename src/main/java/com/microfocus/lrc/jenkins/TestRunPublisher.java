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

package com.microfocus.lrc.jenkins;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.microfocus.lrc.core.entity.*;
import com.microfocus.lrc.core.service.Runner;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.util.FormValidation;
import jenkins.model.Jenkins;
import jenkins.security.MasterToSlaveCallable;
import jenkins.tasks.SimpleBuildStep;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import java.io.IOException;
import java.io.PrintStream;

public final class TestRunPublisher extends Recorder implements SimpleBuildStep {

    private final Integer runsCount;
    private final Integer benchmark;

    private final Integer trtPercentileThresholdImprovement;
    private final Integer trtPercentileThresholdMinorRegression;
    private Integer trtPercentileThresholdMajorRegression;

    private final Integer trtAvgThresholdImprovement;
    private final Integer trtAvgThresholdMinorRegression;
    private Integer trtAvgThresholdMajorRegression;

    private TrendingConfiguration trendingConfig;

    private TrendingConfiguration getTrendingConfig() {
        if (this.trendingConfig == null) {
            this.trendingConfig = new TrendingConfiguration(
                    this.getRunsCount(),
                    this.getBenchmark(),
                    this.getTrtPercentileThresholdImprovement(),
                    this.getTrtPercentileThresholdMinorRegression(),
                    this.getTrtPercentileThresholdMajorRegression(),
                    this.getTrtAvgThresholdImprovement(),
                    this.getTrtAvgThresholdMinorRegression(),
                    this.getTrtAvgThresholdMajorRegression(),
                    this.getBenchmark() == null
            );
        }
        return this.trendingConfig;
    }

    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    private static class PublishReportCallable extends MasterToSlaveCallable<TrendingDataWrapper, RuntimeException> {

        private final ServerConfiguration serverConfiguration;
        private final TrendingConfiguration trendingConfiguration;
        private final LoadTestRun testRun;
        private final TestRunOptions options;
        private final TaskListener listener;

        private PrintStream logger() {
            return this.listener.getLogger();
        }


        PublishReportCallable(
                final ServerConfiguration serverConfiguration,
                final TrendingConfiguration trendingConfiguration,
                final LoadTestRun testRun,
                final TestRunOptions options,
                final TaskListener listener
        ) {
            this.serverConfiguration = serverConfiguration;
            this.trendingConfiguration = trendingConfiguration;
            this.testRun = testRun;
            this.options = options;
            this.listener = listener;
        }

        @Override
        public TrendingDataWrapper call() throws RuntimeException {
            try {
                Runner runner = new Runner(
                        serverConfiguration,
                        this.listener.getLogger(),
                        options
                );
                return runner.fetchTrending(testRun, trendingConfiguration.getBenchmark());
            } catch (Exception e) {
                logger().println("Error while publishing report: " + e.getMessage());
                return null;
            }
        }
    }

    private TestRunReportBuildAction saveTrendingDataToJenkinsAction(
            final Run<?, ?> build,
            final int runId,
            final String tenantId,
            final String uiStatus,
            final TrendingDataWrapper trendingDataWrapper,
            final TrendingConfiguration trendingCfg
    ) {
        if (trendingCfg == null) {
            return null;
        }

        TrendingDataWrapper trendingData = trendingDataWrapper;
        if (trendingDataWrapper == null) {
            trendingData = new TrendingDataWrapper(runId, tenantId, trendingCfg.getBenchmark(), uiStatus);
        }

        TestRunReportBuildAction a = new TestRunReportBuildAction(build, trendingData, trendingCfg);
        build.replaceAction(a);

        return a;
    }

    @Override
    public void perform(
            @NonNull final Run<?, ?> build,
            @NonNull final FilePath workspace,
            @NonNull final EnvVars env,
            @NonNull final Launcher launcher,
            @NonNull final TaskListener listener
    ) throws InterruptedException, IOException {
        PrintStream logger = listener.getLogger();
        LoggerProxy loggerProxy = new LoggerProxy(logger, new LoggerOptions(false, ""));
        loggerProxy.info("TestPublisher started for build #" + build.getNumber());
        loggerProxy.info("Workspace: " + workspace);

        FilePath buildResultPath = workspace.child(String.format("lrc_run_result_%s", build.getId()));
        if (!buildResultPath.exists()) {
            loggerProxy.error(
                    "Build result file not found: " + buildResultPath + ", make sure run LRC build step first."
            );
            build.setResult(Result.FAILURE);
            return;
        }

        TestRunOptions opt;
        LoadTestRun testRun;

        try {
            JsonObject buildResult = new Gson().fromJson(buildResultPath.readToString(), JsonObject.class);
            opt = new Gson().fromJson(buildResult.get("testOptions").getAsString(), TestRunOptions.class);
            testRun = new Gson().fromJson(buildResult.get("testRun").getAsString(), LoadTestRun.class);
        } catch (InterruptedException e) {
            throw e;
        } catch (Exception e) {
            loggerProxy.error("Error while parsing build result file: " + e.getMessage());
            build.setResult(Result.FAILURE);
            return;
        }

        Jenkins instance = Jenkins.getInstanceOrNull();
        if (instance == null) {
            loggerProxy.error("Failed to get Jenkins instance");
            build.setResult(Result.FAILURE);
            return;
        }

        TestRunBuilder.DescriptorImpl descriptor = instance.getDescriptorByType(
                TestRunBuilder.DescriptorImpl.class
        );
        ServerConfiguration serverConfiguration = readServerConfiguration(opt, testRun, descriptor);
        ProxyConfiguration proxyConfig = ConfigurationFactory.createProxyConfiguration(
                serverConfiguration.getUrl(),
                descriptor.getUseProxy(),
                descriptor.getProxyHost(),
                descriptor.getProxyPort(),
                descriptor.getProxyUsername(),
                (descriptor.getProxyPassword() != null) ? descriptor.getProxyPassword().getPlainText() : "",
                loggerProxy
        );
        serverConfiguration.setProxyConfiguration(proxyConfig);

        String uiStatus = testRun.getDetailedStatus();

        TrendingConfiguration trendingCfg = this.getTrendingConfig();

        TrendingDataWrapper wrapper = null;
        try {
            PublishReportCallable callable = new PublishReportCallable(
                    serverConfiguration,
                    trendingCfg,
                    testRun,
                    opt,
                    listener);
            VirtualChannel channel = launcher.getChannel();
            if (channel != null) {
                wrapper = channel.call(callable);
            }
        } catch (IOException e) {
            if (e.getMessage() != null) {
                loggerProxy.error("PublishReport failed. " + e.getMessage());
            } else {
                loggerProxy.error("PublishReport failed.");
            }
        }

        if (wrapper == null) {
            loggerProxy.error("Failed to get trending data.");
            build.setResult(Result.FAILURE);
            return;
        }

        TestRunReportBuildAction buildAction = saveTrendingDataToJenkinsAction(
                build,
                testRun.getId(),
                serverConfiguration.getTenantId(),
                uiStatus,
                wrapper,
                trendingConfig
        );

        if (buildAction == null) {
            loggerProxy.error("Failed to save build result into Jenkins.");
            build.setResult(Result.FAILURE);
            return;
        }

        try {
            String filename = "lrc_report_trend_"
                    + serverConfiguration.getTenantId()
                    + "-" + testRun.getId()
                    + "(build_" + build.getId() + ")"
                    + ".html";
            FilePath filePath = workspace.child(filename);
            buildAction.setTrendingReportHTML(
                    TrendingReport.generateReport(
                            build.getParent(),
                            trendingConfig,
                            false,
                            false
                    )
            );
            filePath.write(buildAction.getTrendingReportHTML(), "UTF-8");
            loggerProxy.info("Trending report file generated: " + filePath.getRemote());
            build.setResult(Result.SUCCESS);
        } catch (IOException ex) {
            loggerProxy.error("Failed to write trending report file, " + ex.getMessage());
        }
    }

    @NonNull
    private ServerConfiguration readServerConfiguration(
            final TestRunOptions opt,
            final LoadTestRun testRun,
            final TestRunBuilder.DescriptorImpl descriptor
    ) {
        ServerConfiguration serverConfiguration;
        String usr = descriptor.getUsername();
        String pwd = (descriptor.getPassword() != null) ? descriptor.getPassword().getPlainText() : "";
        if (Boolean.TRUE.equals(descriptor.getUseOAuth())) {
            usr = descriptor.getClientId();
            pwd = (descriptor.getClientSecret() != null) ? descriptor.getClientSecret().getPlainText() : "";
        }

        serverConfiguration = new ServerConfiguration(
                descriptor.getUrl(),
                usr,
                pwd,
                descriptor.getTenantId(),
                testRun.getLoadTest().getProjectId(),
                opt.getSendEmail()
        );

        return serverConfiguration;
    }

    static final int RUN_COUNT_MIN = 5;
    static final int RUN_COUNT_MAX = 10;
    static final int PERCENTAGE_MAX = 100;
    static final int PERCENTAGE_DEFAULT_MIN = 5;
    static final int PERCENTAGE_DEFAULT_MAX = 10;

    @SuppressWarnings({"checkstyle:MissingJavadocMethod", "checkstyle:ParameterNumber", "java:S107"})
    @DataBoundConstructor
    public TestRunPublisher(
            final Integer runsCount,
            final Integer benchmark,
            final Integer trtAvgThresholdImprovement,
            final Integer trtAvgThresholdMinorRegression,
            final Integer trtAvgThresholdMajorRegression,
            final Integer trtPercentileThresholdImprovement,
            final Integer trtPercentileThresholdMinorRegression,
            final Integer trtPercentileThresholdMajorRegression
    ) {
        this.benchmark = (benchmark == null) ? 0 : benchmark;
        this.runsCount = this.checkRunsCount(runsCount, RUN_COUNT_MIN, RUN_COUNT_MAX);

        this.trtAvgThresholdImprovement = setDefaultValue(
                trtAvgThresholdImprovement,
                1,
                PERCENTAGE_MAX,
                PERCENTAGE_DEFAULT_MIN
        );

        this.trtAvgThresholdMinorRegression = setDefaultValue(
                trtAvgThresholdMinorRegression,
                1,
                PERCENTAGE_MAX,
                PERCENTAGE_DEFAULT_MIN
        );

        this.trtAvgThresholdMajorRegression = setDefaultValue(
                trtAvgThresholdMajorRegression,
                1,
                PERCENTAGE_MAX,
                PERCENTAGE_DEFAULT_MAX
        );

        if (this.trtAvgThresholdMajorRegression <= this.trtAvgThresholdMinorRegression) {
            this.trtAvgThresholdMajorRegression = this.trtAvgThresholdMinorRegression + 1;
        }

        this.trtPercentileThresholdImprovement = setDefaultValue(
                trtPercentileThresholdImprovement,
                1,
                PERCENTAGE_MAX,
                PERCENTAGE_DEFAULT_MIN
        );

        this.trtPercentileThresholdMinorRegression = setDefaultValue(
                trtPercentileThresholdMinorRegression,
                1,
                PERCENTAGE_MAX,
                PERCENTAGE_DEFAULT_MIN
        );

        this.trtPercentileThresholdMajorRegression = setDefaultValue(
                trtPercentileThresholdMajorRegression,
                1,
                PERCENTAGE_MAX,
                PERCENTAGE_DEFAULT_MAX
        );

        if (this.trtPercentileThresholdMajorRegression <= this.trtPercentileThresholdMinorRegression) {
            this.trtPercentileThresholdMajorRegression = this.trtPercentileThresholdMinorRegression + 1;
        }
    }

    private Integer setDefaultValue(
            final Integer val,
            final Integer min,
            final Integer max,
            final Integer defaultValue
    ) {
        if (val == null || val < min || val > max) {
            return defaultValue;
        }

        return val;
    }

    private Integer checkRunsCount(
            final Integer val,
            final Integer min,
            final Integer max
    ) {
        if (val == null) {
            return min;
        } else if (val < min) {
            return min;
        } else if (val > max) {
            return max;
        }

        return val;
    }

    //#region accessors
    public Integer getRunsCount() {
        return runsCount;
    }

    public Integer getBenchmark() {
        return benchmark;
    }

    public Integer getTrtPercentileThresholdImprovement() {
        return trtPercentileThresholdImprovement;
    }

    public Integer getTrtPercentileThresholdMinorRegression() {
        return trtPercentileThresholdMinorRegression;
    }

    public Integer getTrtPercentileThresholdMajorRegression() {
        return trtPercentileThresholdMajorRegression;
    }

    public Integer getTrtAvgThresholdImprovement() {
        return trtAvgThresholdImprovement;
    }

    public Integer getTrtAvgThresholdMinorRegression() {
        return trtAvgThresholdMinorRegression;
    }

    public Integer getTrtAvgThresholdMajorRegression() {
        return trtAvgThresholdMajorRegression;
    }

    //#endregion

    @Symbol("lrcGenTrendingReport")
    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {
        @Override
        public String getDisplayName() {
            return "Generate LoadRunner Cloud trending report";
        }

        public boolean isApplicable(final Class<? extends AbstractProject> jobType) {
            return true;
        }

        private static Integer getIntegerSafely(final String str) {
            Integer result = null;
            try {
                result = Integer.parseInt(str);
            } catch (NumberFormatException e) {
                // ignore
            }

            return result;
        }

        //#region formValidation
        @SuppressWarnings({"checkstyle:MagicNumber"})
        public FormValidation doCheckRunsCount(final @QueryParameter String value) {
            Integer val = getIntegerSafely(value);
            if (val == null || val < 5 || val > 10) {
                return FormValidation.error("Please input an integer from 5 to 10.");
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckBenchmark(final @QueryParameter String value) {
            if (StringUtils.isBlank(value) || StringUtils.isEmpty(value)) {
                return FormValidation.ok();
            }
            Integer val = getIntegerSafely(value);
            if (val == null || val < 0) {
                return FormValidation.error("Please input a valid run id or leave it blank or 0.");
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckTrtPercentileThresholdImprovement(final @QueryParameter String value) {
            Integer val = getIntegerSafely(value);
            return checkThreshold(val);
        }

        public FormValidation doCheckTrtPercentileThresholdMinorRegression(final @QueryParameter String value) {
            return doCheckTrtPercentileThresholdImprovement(value);
        }

        public FormValidation doCheckTrtPercentileThresholdMajorRegression(final @QueryParameter String value) {
            return doCheckTrtPercentileThresholdImprovement(value);
        }

        public FormValidation doCheckTrtAvgThresholdImprovement(final @QueryParameter String value) {
            return doCheckTrtPercentileThresholdImprovement(value);
        }

        public FormValidation doCheckTrtAvgThresholdMinorRegression(final @QueryParameter String value) {
            return doCheckTrtPercentileThresholdImprovement(value);
        }

        public FormValidation doCheckTrtAvgThresholdMajorRegression(final @QueryParameter String value) {
            return doCheckTrtPercentileThresholdImprovement(value);
        }

        @SuppressWarnings({"checkstyle:MagicNumber"})
        private FormValidation checkThreshold(final Integer value) {
            if (value == null || value < 1 || value > 100) {
                return FormValidation.error("Threshold value should be an integer from 1 to 100.");
            }

            return FormValidation.ok();
        }

        //#endregion
    }
}
