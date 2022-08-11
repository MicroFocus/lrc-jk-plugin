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
import com.microfocus.lrc.core.ApiClient;
import com.microfocus.lrc.core.ApiClientFactory;
import com.microfocus.lrc.core.Constants;
import com.microfocus.lrc.core.entity.ProxyConfiguration;
import com.microfocus.lrc.core.entity.*;
import com.microfocus.lrc.core.service.Runner;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.*;
import hudson.model.*;
import hudson.remoting.Callable;
import hudson.remoting.VirtualChannel;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import hudson.util.Secret;
import hudson.util.VersionNumber;
import io.jenkins.cli.shaded.org.apache.commons.lang.StringUtils;
import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildStep;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.jenkinsci.remoting.RoleChecker;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class TestRunBuilder extends Builder implements SimpleBuildStep {

    public String getTestId() {
        return testId;
    }

    @DataBoundSetter
    public void setTestId(final String testId) {
        this.testId = testId;
    }

    public boolean isSendEmail() {
        return sendEmail;
    }

    @DataBoundSetter
    public void setSendEmail(final boolean sendEmail) {
        this.sendEmail = sendEmail;
    }

    public String getProjectId() {
        return projectId;
    }

    @DataBoundSetter
    public void setProjectId(final String projectId) {
        this.projectId = projectId;
    }
    //#endregion

    @Symbol("srlRunTest")
    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

        public DescriptorImpl() {
            load();
        }

        @Override
        public boolean isApplicable(final Class<? extends AbstractProject> jobType) {
            return true;
        }

        @NonNull
        @Override
        public String getDisplayName() {
            return "LoadRunner Cloud";
        }

        @Override
        public boolean configure(final StaplerRequest req, final JSONObject formData) throws FormException {
            // set all properties from formData
            // validate all properties, throw FormException if invalid
            this.username = formData.getString(Constants.USERNAME).trim();
            this.password = formData.getString(Constants.PASSWORD).trim();
            this.url = org.apache.commons.lang.StringUtils.stripEnd(formData.getString(Constants.URL).trim(), "/");
            this.useProxy = Boolean.valueOf(formData.getString("useProxy").trim());
            this.proxyHost = formData.getString("proxyHost").trim();

            this.proxyPort = formData.getString("proxyPort").trim();
            if (this.proxyPort.length() == 0) {
                this.proxyPort = "80";
            }

            this.proxyUsername = formData.getString("proxyUsername");
            if (this.proxyUsername != null && this.proxyUsername.trim().length() != 0) {
                this.proxyUsername = this.proxyUsername.trim();
            } else {
                this.proxyUsername = null;
            }

            this.proxyPassword = formData.getString("proxyPassword").trim();
            this.useOAuth = Boolean.valueOf(formData.getString(Constants.USE_OAUTH).trim());
            this.clientId = formData.getString(Constants.CLIENT_ID).trim();
            this.clientSecret = formData.getString(Constants.CLIENT_SECRET).trim();
            this.tenantId = formData.getString(Constants.TENANTID).trim();

            save();
            return super.configure(req, formData);
        }

        private String url;

        public FormValidation doCheckUrl(@QueryParameter final String value) {
            String errorMsg = "Please input a valid URL";
            if (value == null || value.trim().length() == 0) {
                return FormValidation.error(errorMsg);
            }

            if (!value.matches("\\b(https?)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]")) {
                return FormValidation.error(errorMsg);
            }

            return FormValidation.ok();
        }

        private String tenantId;

        public FormValidation doCheckTenantId(@QueryParameter final String value) {
            if (value == null || value.trim().length() == 0) {
                return FormValidation.error("Please input a Tenant ID");
            }

            return FormValidation.ok();
        }

        private String username;

        @SuppressWarnings("checkstyle:HiddenField")
        public FormValidation doCheckUsername(
                @QueryParameter final String value,
                @QueryParameter final String useOAuth
        ) {
            if (Boolean.parseBoolean(useOAuth)) {
                return FormValidation.ok();
            }

            if (value == null || value.trim().length() == 0) {
                return FormValidation.error("Please input a Username");
            }

            return FormValidation.ok();
        }

        private String password;

        @SuppressWarnings("checkstyle:HiddenField")
        public FormValidation doCheckPassword(
                @QueryParameter final String value,
                final @QueryParameter String useOAuth
        ) {
            if (Boolean.parseBoolean(useOAuth)) {
                return FormValidation.ok();
            }

            if (value == null || value.trim().length() == 0) {
                return FormValidation.error("Please input a Password");
            }

            return FormValidation.ok();
        }

        private Boolean useOAuth;
        private String clientId;

        @SuppressWarnings("checkstyle:HiddenField")
        public FormValidation doCheckClientId(
                @QueryParameter final String value,
                @QueryParameter final String useOAuth
        ) {
            if (!Boolean.parseBoolean(useOAuth)) {
                return FormValidation.ok();
            }

            if (!ApiClient.isOAuthClientId(value.trim())) {
                return FormValidation.error("Please input a valid Client ID");
            }

            return FormValidation.ok();
        }

        private String clientSecret;

        @SuppressWarnings("checkstyle:HiddenField")
        public FormValidation doCheckClientSecret(
                @QueryParameter final String value,
                @QueryParameter final String useOAuth
        ) {
            if (!Boolean.parseBoolean(useOAuth)) {
                return FormValidation.ok();
            }

            if (value == null || value.trim().length() == 0) {
                return FormValidation.error("Please input a valid Client Secret");
            }

            return FormValidation.ok();
        }

        private Boolean useProxy;
        private String proxyHost;

        @SuppressWarnings("checkstyle:HiddenField")
        public FormValidation doCheckProxyHost(
                @QueryParameter final String value,
                @QueryParameter final String useProxy
        ) {
            if (!Boolean.parseBoolean(useProxy)) {
                return FormValidation.ok();
            }

            if (value == null || value.trim().length() == 0) {
                return FormValidation.error("Please input a Host");
            }

            return FormValidation.ok();
        }


        private String proxyPort;

        @SuppressWarnings({"checkstyle:HiddenField", "checkstyle:MagicNumber"})
        public FormValidation doCheckProxyPort(
                @QueryParameter final String value,
                @QueryParameter final String useProxy
        ) {
            if (!Boolean.parseBoolean(useProxy)) {
                return FormValidation.ok();
            }

            if (value == null || value.trim().length() == 0) {
                return FormValidation.ok();
            }

            if (!StringUtils.isNumeric(value)) {
                return FormValidation.error("Please input a valid port number.");
            }

            int portVal = Integer.parseInt(value);

            if (portVal < 0 || portVal > 65535) {
                return FormValidation.error("Please input a valid port number.");
            }

            return FormValidation.ok();
        }

        private String proxyUsername;
        private String proxyPassword;

        public FormValidation doCheckProjectID(@QueryParameter final String value) {
            if (value == null || value.trim().length() == 0) {
                return FormValidation.error("Please input a ProjectID");
            }

            if (!value.matches("^\\d+$")) {
                return FormValidation.error("Invalid ProjectID");
            }
            return FormValidation.ok();
        }

        //#region getter/setter
        public String getUrl() {
            return url;
        }

        public void setUrl(final String url) {
            this.url = url;
        }

        public String getTenantId() {
            return tenantId;
        }

        public void setTenantId(final String tenantId) {
            this.tenantId = tenantId;
        }

        public String getClientId() {
            return clientId;
        }

        public void setClientId(final String clientId) {
            this.clientId = clientId;
        }

        public String getClientSecret() {
            return clientSecret;
        }

        public void setClientSecret(final String clientSecret) {
            this.clientSecret = clientSecret;
        }

        public Boolean getUseProxy() {
            return useProxy;
        }

        public void setUseProxy(final Boolean useProxy) {
            this.useProxy = useProxy;
        }

        public String getProxyHost() {
            return proxyHost;
        }

        public void setProxyHost(final String proxyHost) {
            this.proxyHost = proxyHost;
        }

        public String getProxyPort() {
            return proxyPort;
        }

        public void setProxyPort(final String proxyPort) {
            this.proxyPort = proxyPort;
        }

        public String getProxyUsername() {
            return proxyUsername;
        }

        public void setProxyUsername(final String proxyUsername) {
            this.proxyUsername = proxyUsername;
        }

        public String getProxyPassword() {
            return proxyPassword;
        }

        public void setProxyPassword(final String proxyPassword) {
            this.proxyPassword = proxyPassword;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(final String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(final String password) {
            this.password = password;
        }

        public Boolean getUseOAuth() {
            return useOAuth;
        }

        public void setUseOAuth(final Boolean useOAuth) {
            this.useOAuth = useOAuth;
        }

        //#endregion

        @SuppressWarnings({"java:S107", "checkstyle:ParameterNumber", "checkstyle:HiddenField"})
        public FormValidation doTestConnection(
                @QueryParameter("username") final String username,
                @QueryParameter("password") final String password,
                @QueryParameter("url") final String url,
                @QueryParameter("proxyHost") final String proxyHost,
                @QueryParameter("proxyPort") final String proxyPort,
                @QueryParameter("proxyUsername") final String proxyUsername,
                @QueryParameter("proxyPassword") final String proxyPassword,
                @QueryParameter("clientId") final String clientId,
                @QueryParameter("clientSecret") final String clientSecret,
                @QueryParameter("tenantId") final String tenantId,
                @QueryParameter("useOAuth") final String useOAuth,
                @QueryParameter("useProxy") final String useProxy
        ) {
            Jenkins.get().checkPermission(Jenkins.ADMINISTER);
            ServerConfiguration config;
            if (Boolean.parseBoolean(useOAuth)) {
                config = new ServerConfiguration(
                        url,
                        clientId,
                        Secret.fromString(clientSecret).getPlainText(),
                        tenantId,
                        0,
                        false,
                        ""
                );
            } else {
                config = new ServerConfiguration(
                        url,
                        username,
                        Secret.fromString(password).getPlainText(),
                        tenantId,
                        0,
                        false,
                        ""
                );
            }
            ProxyConfiguration proxyConfiguration = (
                    ConfigurationFactory.createProxyConfiguration(
                            url,
                            Boolean.valueOf(useProxy),
                            proxyHost,
                            proxyPort,
                            proxyUsername,
                            Secret.fromString(proxyPassword).getPlainText(),
                            new LoggerProxy()
                    )
            );
            config.setProxyConfiguration(proxyConfiguration);
            try (ApiClient c = ApiClientFactory.getClient(config, new LoggerProxy())) {
                c.login();
                return FormValidation.ok("Ping connection successfully!");
            } catch (Exception e) {
                return FormValidation.error("Ping connection failed, error: " + e.getMessage());
            }
        }
    }

    private String testId;
    private boolean sendEmail;
    private String projectId;

    private String getProjectIdAtRunTime(final Run<?, ?> run, final Launcher launcher) {
        // check if the job is a pipeline (`WorkflowRun`)
        // if not, use env vars (run parameters) to override job configurations
        if (run instanceof AbstractBuild) {
            String projectIDFromParam = EnvVarsUtil.getEnvVar(run, launcher, "LRC_PROJECT_ID");
            if (StringUtils.isNotBlank(projectIDFromParam)) {
                logFieldReadFromParam("project id", projectIDFromParam, run.getId());
                return projectIDFromParam.trim();
            }
        }

        return this.projectId;
    }

    private String getTestIdAtRunTime(final Run<?, ?> run, final Launcher launcher) {
        if (run instanceof AbstractBuild) {
            String testIDFromParam = EnvVarsUtil.getEnvVar(run, launcher, "LRC_TEST_ID");
            if (StringUtils.isNotBlank(testIDFromParam)) {
                logFieldReadFromParam("test id", testIDFromParam, run.getId());
                return testIDFromParam.trim();
            }
        }

        return this.testId;
    }

    @SuppressWarnings("java:S2065")
    private transient LoggerProxy loggerProxy = new LoggerProxy();

    @DataBoundConstructor
    public TestRunBuilder(
            final String projectId,
            final String testId,
            final boolean sendEmail
    ) {
        this.setProjectId(projectId.trim());
        this.setTestId(testId.trim());
        this.setSendEmail(sendEmail);
    }

    @Override
    public void perform(
            final @NonNull Run<?, ?> run,
            final @NonNull FilePath workspace,
            final @NonNull EnvVars env,
            final @NonNull Launcher launcher,
            final @NonNull TaskListener listener
    ) throws InterruptedException, IOException {
        PrintStream logger = listener.getLogger();
        this.loggerProxy = new LoggerProxy(logger, new LoggerOptions(false, ""));

        printEnvInfo(env);

        TestRunBuilder.DescriptorImpl descriptor = (TestRunBuilder.DescriptorImpl) this.getDescriptor();
        if (isDescriptorEmpty()) {
            this.loggerProxy.error(
                    "Failed to read configuration of LoadRunner Cloud plugin. "
                            + "Please check configuration and try again."
            );
            run.setResult(Result.FAILURE);
            return;
        }

        ServerConfiguration serverConfiguration = createServerConfiguration(descriptor, run, launcher);
        ProxyConfiguration proxyConfiguration = ConfigurationFactory.createProxyConfiguration(
                serverConfiguration.getUrl(),
                descriptor.useProxy,
                descriptor.proxyHost,
                descriptor.proxyPort,
                descriptor.proxyUsername,
                Secret.fromString(descriptor.proxyPassword).getPlainText(),
                this.loggerProxy
        );
        serverConfiguration.setProxyConfiguration(proxyConfiguration);

        int testIdVal = Integer.parseInt(this.getTestIdAtRunTime(run, launcher));
        Map<String, String> envVarsObj = this.readConfigFromEnvVars(run, launcher);
        TestRunOptions opt = new TestRunOptions(
                testIdVal,
                this.sendEmail,
                Boolean.parseBoolean(envVarsObj.get(OptionInEnvVars.LRC_CLI_SKIP_LOGIN.name())),
                Boolean.parseBoolean(envVarsObj.get(OptionInEnvVars.LRC_SKIP_PDF_REPORT.name())),
                Boolean.parseBoolean(envVarsObj.get(OptionInEnvVars.LRC_DEBUG_LOG.name()))
        );

        RunTestCallable callable = new RunTestCallable(
                listener,
                serverConfiguration,
                opt
        );

        LoadTestRun testRun = null;

        try {
            VirtualChannel channel = launcher.getChannel();
            if (channel != null) {
                testRun = channel.call(callable);
            }
        } catch (InterruptedException e) {
            loggerProxy.info("Test run interrupted");
            throw e;
        } catch (Exception e) {
            loggerProxy.error(e.getMessage());
        }

        if (testRun == null) {
            this.loggerProxy.info("Test run failed.");
            run.setResult(Result.FAILURE);
            return;
        }

        List<String> fileNames = new ArrayList<>();
        testRun.getReports().forEach((fileName, content) -> fileNames.add(fileName));

        for (String fileName : fileNames) {
            byte[] content = testRun.getReports().get(fileName);
            FilePath file = workspace.child(fileName);
            try (OutputStream out = file.write()) {
                out.write(content);
                this.loggerProxy.info("Report file " + file.getRemote() + " created.");
            } catch (IOException e) {
                this.loggerProxy.error("Failed to create report file " + file.getRemote());
                this.loggerProxy.error(e.getMessage());
            }
        }

        // output vars to jenkins env
        EnvVarsUtil.putEnvVar(run, "LRC_RUN_ID", String.valueOf(testRun.getId()));

        if (testRun.getHasReport()) {
            // remove reports data to write a smaller json
            testRun.getReports().clear();
            JsonObject buildResult = new JsonObject();
            buildResult.addProperty("testOptions", new Gson().toJson(opt));
            buildResult.addProperty("testRun", new Gson().toJson(testRun));

            workspace.child(String.format("lrc_run_result_%s", run.getId())).write(buildResult.toString(), "UTF-8");
        }

        if (testRun.getStatusEnum().isSuccess()) {
            run.setResult(Result.SUCCESS);
        } else {
            run.setResult(Result.FAILURE);
        }
    }

    private Map<String, String> readConfigFromEnvVars(final Run<?, ?> run, final Launcher launcher) {
        Map<String, String> map = new HashMap<>();
        for (OptionInEnvVars key : OptionInEnvVars.values()) {
            String value = EnvVarsUtil.getEnvVar(run, launcher, key.name());
            if (StringUtils.isNotBlank(value) && !value.equals("0") && !value.equals("false") && !value.equals("no")) {
                this.loggerProxy.info("Read " + key.name() + " from parameters / env variables: " + value);
                map.put(key.name(), "true");
            }
        }
        return map;
    }

    private void printEnvInfo(final EnvVars env) {
        this.loggerProxy.info(Constants.SEPARATOR_LINE);
        this.loggerProxy.info("Environment information:");
        VersionNumber ver = jenkins.model.Jenkins.getVersion();
        String verStr = "N/A";
        if (ver != null) {
            verStr = ver.toString();
        }
        this.loggerProxy.info("  Jenkins version: " + verStr);
        this.loggerProxy.info("  Java version: " + System.getProperty("java.version"));
        Jenkins instance = Jenkins.getInstanceOrNull();
        String pluginVerStr = "N/A";
        if (instance != null) {
            PluginWrapper plugin = instance.pluginManager.getPlugin("loadrunner_cloud");
            if (plugin != null) {
                pluginVerStr = plugin.getVersion();
            }
        }

        this.loggerProxy.info("  Running on Jenkins node: " + env.get("NODE_NAME"));
        this.loggerProxy.info("  LoadRunner Cloud plugin version: " + pluginVerStr);
        this.loggerProxy.info(Constants.SEPARATOR_LINE);
    }

    private boolean isDescriptorEmpty() {
        DescriptorImpl descriptor = (DescriptorImpl) this.getDescriptor();
        if (descriptor == null) {
            return true;
        }

        return descriptor.url == null;
    }

    private void printJobParameters(final ServerConfiguration config) {
        JsonObject display = new Gson().toJsonTree(config).getAsJsonObject();

        display.remove("initiator");
        display.remove("password");
        display.remove("proxyConfiguration");

        this.loggerProxy.info("Job started with parameters: ");
        this.loggerProxy.info(display.toString());
        this.loggerProxy.info(Constants.SEPARATOR_LINE);
    }

    private ServerConfiguration createServerConfiguration(
            final DescriptorImpl descriptor,
            final Run<?, ?> run,
            final Launcher launcher) {
        String usr = descriptor.getUsername();
        String pwd = Secret.fromString(descriptor.getPassword()).getPlainText();
        if (Boolean.TRUE.equals(descriptor.getUseOAuth())) {
            usr = descriptor.getClientId();
            pwd = Secret.fromString(descriptor.getClientSecret()).getPlainText();
        }
        ServerConfiguration config = new ServerConfiguration(
                descriptor.getUrl(),
                usr,
                pwd,
                descriptor.getTenantId(),
                Integer.parseInt(this.getProjectIdAtRunTime(run, launcher)),
                this.sendEmail,
                Constants.INITIATOR
        );
        printJobParameters(config);
        return config;
    }

    @SuppressWarnings("java:S2065")
    private transient HashMap<String, Boolean> isLogPrinted;

    private void logFieldReadFromParam(
            final String fieldName,
            final Object fieldValue,
            final String jenkinsRunId
    ) {
        String key = jenkinsRunId + ":" + fieldName;
        if (isLogPrinted == null) {
            isLogPrinted = new HashMap<>();
        }
        boolean isFieldLoggedInCurrentBuild = isLogPrinted.containsKey(key) && isLogPrinted.get(key);
        if (!isFieldLoggedInCurrentBuild) {
            this.loggerProxy.info(fieldName + " from parameter: " + fieldValue.toString());
            isLogPrinted.put(key, true);
        }
    }

    private static class RunTestCallable implements Callable<LoadTestRun, Exception> {

        private final ServerConfiguration serverConfiguration;
        private final TestRunOptions testRunOptions;
        private final TaskListener listener;

        RunTestCallable(
                final TaskListener listener,
                final ServerConfiguration serverConfiguration,
                final TestRunOptions testRunOptions
        ) {
            this.listener = listener;
            this.serverConfiguration = serverConfiguration;
            this.testRunOptions = testRunOptions;
        }

        @Override
        @SuppressWarnings("java:S2093")
        public LoadTestRun call() throws Exception {
            PrintStream logger = this.listener.getLogger();
            LoggerProxy loggerProxy = new LoggerProxy(
                    logger,
                    new LoggerOptions(this.testRunOptions.isDebug(), "")
            );

            Runner runner = new Runner(
                    this.serverConfiguration,
                    logger,
                    this.testRunOptions
            );
            try {
                return runner.run();
            } catch (IOException e) {
                if (Thread.interrupted()) {
                    throw new InterruptedException("Jenkins job is interrupted.");
                }
                throw e;
            } catch (InterruptedException e) {
                runner.interruptHandler();
                throw e;
            } finally {
                runner.close();
            }
        }

        @Override
        public void checkRoles(final RoleChecker roleChecker) throws SecurityException {
            // noop
        }
    }
}
