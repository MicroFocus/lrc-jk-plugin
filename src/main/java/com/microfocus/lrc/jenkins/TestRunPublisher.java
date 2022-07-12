/*
 * #© Copyright 2019 - Micro Focus or one of its affiliates
 * #
 * # The only warranties for products and services of Micro Focus and its affiliates and licensors (“Micro Focus”)
 * # are as may be set forth in the express warranty statements accompanying such products and services.
 * # Nothing herein should be construed as constituting an additional warranty.
 * # Micro Focus shall not be liable for technical or editorial errors or omissions contained herein.
 * # The information contained herein is subject to change without notice.
 *
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
import hudson.remoting.Callable;
import hudson.remoting.VirtualChannel;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.util.FormValidation;
import hudson.util.Secret;
import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildStep;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.jenkinsci.remoting.RoleChecker;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import java.io.IOException;
import java.io.PrintStream;
import java.util.HashMap;

public final class TestRunPublisher extends Recorder implements SimpleBuildStep {

    private Integer runsCount;
    private Integer benchmark;

    private Integer trtPercentileThresholdImprovement;
    private Integer trtPercentileThresholdMinorRegression;
    private Integer trtPercentileThresholdMajorRegression;

    private Integer trtAvgThresholdImprovement;
    private Integer trtAvgThresholdMinorRegression;
    private Integer trtAvgThresholdMajorRegression;

    private transient TrendingConfiguration trendingConfig;

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

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    private static Integer getIntegerSafely(final String str) {
        Integer result = null;
        try {
            result = Integer.valueOf(str);
        } finally {
            return result;
        }
    }

    private static class PublishReportCallable implements Callable<TrendingDataWrapper, RuntimeException> {

        private final ServerConfiguration serverConfiguration;
        private final TrendingConfiguration trendingConfiguration;
        private final LoadTestRun testRun;
        private final boolean skipLogin;
        private final TaskListener listener;

        private PrintStream logger() {
            return this.listener.getLogger();
        }


        PublishReportCallable(
                final ServerConfiguration serverConfiguration,
                final TrendingConfiguration trendingConfiguration,
                final LoadTestRun testRun,
                final boolean skipLogin,
                final TaskListener listener
        ) {
            this.serverConfiguration = serverConfiguration;
            this.trendingConfiguration = trendingConfiguration;
            this.testRun = testRun;
            this.skipLogin = skipLogin;
            this.listener = listener;
        }

        @Override
        public TrendingDataWrapper call() throws RuntimeException {
            try {
                Runner runner = new Runner(serverConfiguration, this.listener.getLogger(), new HashMap<>());
                return runner.fetchTrending(testRun, trendingConfiguration.getBenchmark());
            } catch (Exception e) {
                logger().println("Error while publishing report: " + e.getMessage());
                return null;
            }
        }

        @Override
        public void checkRoles(final RoleChecker roleChecker) throws SecurityException {

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

        logger.println("StormTestPublisher started for build " + build.getNumber());
        logger.println("Workspace: " + workspace);

        FilePath buildResultPath = workspace.child("build_result_" + build.getId());
        if (!buildResultPath.exists()) {
            logger.println("Build result file not found: " + buildResultPath + ", make sure run LRC build step first.");
            build.setResult(Result.FAILURE);
            return;
        }


        JsonObject buildResult = new Gson().fromJson(buildResultPath.readToString(), JsonObject.class);
        Jenkins instance = Jenkins.getInstanceOrNull();
        if (instance == null) {
            logger.println("Failed to get Jenkins instance");
            build.setResult(Result.FAILURE);
            return;
        }

        TestRunBuilder.DescriptorImpl descriptor = instance.getDescriptorByType(
                TestRunBuilder.DescriptorImpl.class
        );
        ServerConfiguration serverConfiguration = readServerConfiguration(buildResult, descriptor);
        ProxyConfiguration proxyConfig = ProxyConfigurationFactory.createProxyConfiguration(
                serverConfiguration.getUrl(),
                descriptor.getUseProxy(),
                descriptor.getProxyHost(),
                descriptor.getProxyPort(),
                descriptor.getProxyUsername(),
                Secret.fromString(descriptor.getProxyPassword()).getPlainText(),
                logger
        );
        serverConfiguration.setProxyConfiguration(proxyConfig);

        LoadTestRun testRun = new Gson().fromJson(buildResult.get("testRun").getAsString(), LoadTestRun.class);
        if (testRun == null) {
            logger.println("Test run not found in build result file. Make sure the test run ended successfully.");
            build.setResult(Result.FAILURE);
            return;
        }
        String uiStatus = testRun.getDetailedStatus();

        TrendingConfiguration trendingCfg = this.getTrendingConfig();

        boolean skipLogin = StringUtils.isNotEmpty(
                EnvVarsUtil.getEnvVar(build, launcher, OptionInEnvVars.SRL_CLI_SKIP_LOGIN.name())
        );
        boolean extraContent = StringUtils.isNotEmpty(
                EnvVarsUtil.getEnvVar(build, launcher, "SRL_INTERNAL_EXTRA_CONTENT")
        );

        TrendingDataWrapper wrapper = null;
        try {
            PublishReportCallable callable = new PublishReportCallable(
                    serverConfiguration,
                    trendingCfg,
                    testRun,
                    skipLogin,
                    listener);
            VirtualChannel channel = launcher.getChannel();
            if (channel != null) {
                wrapper = channel.call(callable);
            }
        } catch (IOException e) {
            if (e.getMessage() != null) {
                logger.println("[ERROR] PublishReport failed. " + e.getMessage());
            } else {
                logger.println("PublishReport failed.");
            }
        }

        if (wrapper == null) {
            logger.println("failed to get trending data.");
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
            logger.println("failed to save build result into Jenkins.");
            build.setResult(Result.FAILURE);
            return;
        }

        try {
            String filename = "srl_report_trend_"
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
                            extraContent
                    )
            );
            filePath.write(buildAction.getTrendingReportHTML(), "UTF-8");
            logger.println("trending report html file generated: " + filePath.getRemote());
            build.setResult(Result.SUCCESS);
        } catch (Exception ex) {
            logger.println("failed to write trending report html, " + ex.getMessage());
        }
    }

    @NonNull
    private ServerConfiguration readServerConfiguration(
            final JsonObject buildResult,
            final TestRunBuilder.DescriptorImpl descriptor
    ) {
        JsonObject serverConfigJSON = new JsonObject();

        serverConfigJSON.addProperty("url", descriptor.getUrl());
        serverConfigJSON.addProperty("username", descriptor.getUsername());
        serverConfigJSON.addProperty("password", Secret.fromString(descriptor.getPassword()).getPlainText());
        serverConfigJSON.addProperty("tenantId", buildResult.get("tenantId").getAsString());
        serverConfigJSON.addProperty("projectId", buildResult.get("projectId").getAsInt());
        serverConfigJSON.addProperty("sendEmail", buildResult.get("sendEmail").getAsBoolean());
        serverConfigJSON.addProperty("useOAuth", descriptor.getUseOAuth());
        serverConfigJSON.addProperty("clientId", descriptor.getClientId());
        if (StringUtils.isNotEmpty(descriptor.getClientSecret())) {
            serverConfigJSON.addProperty(
                    "clientSecret",
                    Secret.fromString(descriptor.getClientSecret()).getPlainText()
            );
        } else {
            serverConfigJSON.addProperty("clientSecret", "");
        }

        ServerConfiguration serverConfiguration;
        if (serverConfigJSON.get("useOAuth").getAsBoolean()) {
            serverConfiguration = new ServerConfiguration(
                    serverConfigJSON.get("url").getAsString(),
                    serverConfigJSON.get("clientId").getAsString(),
                    serverConfigJSON.get("clientSecret").getAsString(),
                    serverConfigJSON.get("tenantId").getAsString(),
                    serverConfigJSON.get("projectId").getAsInt(),
                    serverConfigJSON.get("sendEmail").getAsBoolean(),
                    "jenkins-plugin"
            );
        } else {
            serverConfiguration = new ServerConfiguration(
                    serverConfigJSON.get("url").getAsString(),
                    serverConfigJSON.get("username").getAsString(),
                    serverConfigJSON.get("password").getAsString(),
                    serverConfigJSON.get("tenantId").getAsString(),
                    serverConfigJSON.get("projectId").getAsInt(),
                    serverConfigJSON.get("sendEmail").getAsBoolean(),
                    "jenkins-plugin"
            );
        }
        return serverConfiguration;
    }

    @SuppressWarnings({"checkstyle:MissingJavadocMethod", "checkstyle:ParameterNumber"})
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
        this.runsCount = runsCount;
        if (this.runsCount == null) {
            this.runsCount = 5;
        }

        this.benchmark = benchmark;
        if (this.benchmark == null) {
            this.benchmark = 0;
        }

        this.trtAvgThresholdImprovement = trtAvgThresholdImprovement;
        this.trtAvgThresholdMinorRegression = trtAvgThresholdMinorRegression;
        this.trtAvgThresholdMajorRegression = trtAvgThresholdMajorRegression;
        this.trtPercentileThresholdImprovement = trtPercentileThresholdImprovement;
        this.trtPercentileThresholdMinorRegression = trtPercentileThresholdMinorRegression;
        this.trtPercentileThresholdMajorRegression = trtPercentileThresholdMajorRegression;

        if (this.runsCount < 5) {
            this.runsCount = 5;
        }
        if (this.runsCount > 10) {
            this.runsCount = 10;
        }

        if (this.trtAvgThresholdImprovement == null
                || this.trtAvgThresholdImprovement <= 0
                || this.trtAvgThresholdImprovement >= 100
        ) {
            this.trtAvgThresholdImprovement = 5;
        }

        if (this.trtAvgThresholdMinorRegression == null
                || this.trtAvgThresholdMinorRegression <= 0
                || this.trtAvgThresholdMinorRegression >= 99
        ) {
            this.trtAvgThresholdMinorRegression = 5;
        }

        if ((this.trtAvgThresholdMajorRegression == null)
                || (this.trtAvgThresholdMajorRegression <= 0)
                || (this.trtAvgThresholdMajorRegression >= 100)
        ) {
            this.trtAvgThresholdMajorRegression = 10;
        }
        if (this.trtAvgThresholdMajorRegression <= this.trtAvgThresholdMinorRegression) {
            this.trtAvgThresholdMajorRegression = this.trtAvgThresholdMinorRegression + 1;
        }

        if (this.trtPercentileThresholdImprovement == null
                || this.trtPercentileThresholdImprovement <= 0
                || this.trtPercentileThresholdImprovement >= 100
        ) {
            this.trtPercentileThresholdImprovement = 5;
        }

        if (this.trtPercentileThresholdMinorRegression == null
                || this.trtPercentileThresholdMinorRegression <= 0
                || this.trtPercentileThresholdMinorRegression >= 99
        ) {
            this.trtPercentileThresholdMinorRegression = 5;
        }

        if (this.trtPercentileThresholdMajorRegression == null
                || this.trtPercentileThresholdMajorRegression <= 0
                || this.trtPercentileThresholdMinorRegression >= 100
        ) {
            this.trtPercentileThresholdMajorRegression = 10;
        }
        if (this.trtPercentileThresholdMajorRegression <= this.trtPercentileThresholdMinorRegression) {
            this.trtPercentileThresholdMajorRegression = this.trtPercentileThresholdMinorRegression + 1;
        }
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

    @Symbol("srlGetTrendingReport")
    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {
        @Override
        public String getDisplayName() {
            return "Generate LoadRunner Cloud Trending";
        }

        public boolean isApplicable(final Class<? extends AbstractProject> jobType) {
            return true;
        }

        //#region formValidation

        public FormValidation doCheckRunsCount(final @QueryParameter String value) {
            Integer val = getIntegerSafely(value);
            if (val == null || val < 5 || val > 10) {
                return FormValidation.error("Please input a valid number range from 5 to 10.");
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckBenchmark(final @QueryParameter String value) {
            Integer val = getIntegerSafely(value);
            if (val != null && val < 0) {
                return FormValidation.error("Please input a valid RunId or leave it blank or 0.");
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckTrtPercentileThresholdImprovement(final @QueryParameter String value) {
            Integer val = getIntegerSafely(value);
            return checkThreshold(val);
        }

        public FormValidation doCheckTrtPercentileThresholdMinorRegression(final @QueryParameter String value) {
            Integer val = getIntegerSafely(value);
            return checkThreshold(val);
        }

        public FormValidation doCheckTrtPercentileThresholdMajorRegression(final @QueryParameter String value) {
            Integer val = getIntegerSafely(value);
            return checkThreshold(val);
        }

        public FormValidation doCheckTrtAvgThresholdImprovement(final @QueryParameter String value) {
            Integer val = getIntegerSafely(value);
            return checkThreshold(val);
        }

        public FormValidation doCheckTrtAvgThresholdMinorRegression(final @QueryParameter String value) {
            Integer val = getIntegerSafely(value);
            return checkThreshold(val);
        }

        public FormValidation doCheckTrtAvgThresholdMajorRegression(final @QueryParameter String value) {
            Integer val = getIntegerSafely(value);
            return checkThreshold(val);
        }

        private FormValidation checkThreshold(final Integer value) {
            if (value == null) {
                return FormValidation.ok();
            }

            if (value <= 0 || value >= 100) {
                return FormValidation.error("Threshold should be between 0 to 100.");
            }

            return FormValidation.ok();
        }

        //#endregion
    }
}
