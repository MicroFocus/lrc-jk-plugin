package com.microfocus.lrc.jenkins;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.microfocus.lrc.core.ApiClient;
import com.microfocus.lrc.core.ApiClientFactory;
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

import javax.servlet.ServletException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;

public final class TestRunBuilder extends Builder implements SimpleBuildStep {
    //#region getter/setter
    public String getTenant() {
        return tenant;
    }

    @DataBoundSetter
    public void setTenant(final String tenant) {
        this.tenant = tenant;
    }

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
            System.out.println(formData.toString());
            this.username = formData.getString("username").trim();
            this.password = Secret.fromString(formData.getString("password").trim()).getEncryptedValue();
            this.url = org.apache.commons.lang.StringUtils.stripEnd(formData.getString("url").trim(), "/");
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

            this.proxyPassword = Secret.fromString(formData.getString("proxyPassword").trim()).getEncryptedValue();
            this.useOAuth = Boolean.valueOf(formData.getString("useOAuth").trim());
            this.clientId = formData.getString("clientId").trim();
            this.clientSecret = formData.getString("clientSecret").trim();
            this.tenantId = formData.getString("tenantId").trim();

            save();
            System.out.println("save done.");
            return super.configure(req, formData);
        }

        private String url;

        public FormValidation doCheckUrl(@QueryParameter final String value)
                throws IOException, ServletException {
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

        public FormValidation doCheckTenantId(@QueryParameter final String value)
                throws IOException, ServletException {
            if (value == null || value.trim().length() == 0) {
                return FormValidation.error("Please input a Tenant");
            }

            return FormValidation.ok();
        }

        private String username;

        @SuppressWarnings("checkstyle:HiddenField")
        public FormValidation doCheckUsername(
                @QueryParameter final String value,
                @QueryParameter final String useOAuth
        )
                throws IOException, ServletException {
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
        )
                throws IOException, ServletException {
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
        ) throws IOException, ServletException {
            if (!Boolean.parseBoolean(useOAuth)) {
                return FormValidation.ok();
            }

            if (!ApiClient.isOAuthClientId(value.trim())) {
                return FormValidation.error("Please input a valid Client Id");
            }

            return FormValidation.ok();
        }

        private String clientSecret;

        @SuppressWarnings("checkstyle:HiddenField")
        public FormValidation doCheckClientSecret(
                @QueryParameter final String value,
                @QueryParameter final String useOAuth
        ) throws IOException, ServletException {
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
        ) throws IOException, ServletException {
            if (!Boolean.parseBoolean(useProxy)) {
                return FormValidation.ok();
            }

            if (value == null || value.trim().length() == 0) {
                return FormValidation.error("Please input a Host");
            }

            return FormValidation.ok();
        }


        private String proxyPort;

        @SuppressWarnings("checkstyle:HiddenField")
        public FormValidation doCheckProxyPort(
                @QueryParameter final String value,
                @QueryParameter final String useProxy
        ) throws IOException, ServletException {
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

        public FormValidation doCheckTenant(@QueryParameter final String value)
                throws IOException, ServletException {
            if (value == null || value.trim().length() == 0) {
                return FormValidation.error("Please input a Tenant");
            }

            return FormValidation.ok();
        }

        public FormValidation doCheckProjectID(@QueryParameter final String value)
                throws IOException, ServletException {
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

        /**
         * test connection to the server.
         *
         * @param username
         * @param password
         * @param url
         * @param proxyHost
         * @param proxyPort
         * @param proxyUsername
         * @param proxyPassword
         * @param clientId
         * @param clientSecret
         * @param tenantId
         * @param useOAuth
         * @param useProxy
         * @return FormValidation
         * @throws IOException
         * @throws ServletException
         */
        @SuppressWarnings({"checkstyle:ParameterNumber", "checkstyle:HiddenField"})
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
        ) throws IOException, ServletException {
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
                        "",
                        0,
                        false,
                        ""
                );
            }
            ProxyConfiguration proxyConfiguration = (
                    ProxyConfigurationFactory.createProxyConfiguration(
                            url,
                            Boolean.valueOf(useProxy),
                            proxyHost,
                            proxyPort,
                            proxyUsername,
                            Secret.fromString(proxyPassword).getPlainText(),
                            System.out
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

    private String tenant;
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

    private transient PrintStream logger = System.out;

    @DataBoundConstructor
    public TestRunBuilder(
            final String tenant,
            final String projectId,
            final String testId,
            final boolean sendEmail
    ) {
        this.setTenant(tenant.trim());
        this.setProjectId(projectId.trim());
        this.setTestId(testId.trim());
        this.setSendEmail(sendEmail);
    }

    /**
     * run the build.
     *
     * @param run       a build this is running as a part of
     * @param workspace a workspace to use for any file operations
     * @param env       environment variables applicable to this step
     * @param launcher  a way to start processes
     * @param listener  a place to send output
     * @throws InterruptedException
     * @throws IOException
     */
    @Override
    public void perform(
            final @NonNull Run<?, ?> run,
            final @NonNull FilePath workspace,
            final @NonNull EnvVars env,
            final @NonNull Launcher launcher,
            final @NonNull TaskListener listener
    ) throws InterruptedException, IOException {
        logger = listener.getLogger();
        printEnvInfo(run);

        TestRunBuilder.DescriptorImpl descriptor = (TestRunBuilder.DescriptorImpl) this.getDescriptor();
        if (isDescriptorEmpty()) {
            logger.println(
                    "failed to read configuration of LoadRunner Cloud Plugin. "
                            + "Please check it in System Configuration and try again."
            );
            run.setResult(Result.FAILURE);
            return;
        }

        ServerConfiguration serverConfiguration = createServerConfiguration(descriptor, run, launcher);

        ProxyConfiguration proxyConfiguration = ProxyConfigurationFactory.createProxyConfiguration(
                serverConfiguration.getUrl(),
                descriptor.useProxy,
                descriptor.proxyHost,
                descriptor.proxyPort,
                descriptor.proxyUsername,
                Secret.fromString(descriptor.proxyPassword).getPlainText(),
                this.logger
        );
        serverConfiguration.setProxyConfiguration(proxyConfiguration);

        JsonObject buildResult = new JsonObject();
        buildResult.addProperty("tenantId", this.getTenant());
        buildResult.addProperty("projectId", this.getProjectIdAtRunTime(run, launcher));
        buildResult.addProperty("sendEmail", this.isSendEmail());
        String resultStr = buildResult.toString();
        FilePath resultFile = workspace.child("build_result_" + run.getId());

        int testIdVal = Integer.parseInt(this.getTestIdAtRunTime(run, launcher));
        TestRunOptions opt = new TestRunOptions(testIdVal, this.sendEmail);
        Map<String, String> envVarsObj = this.readConfigFromEnvVars(run, launcher);

        RunTestCallable callable = new RunTestCallable(
                serverConfiguration,
                listener,
                resultStr,
                resultFile,
                opt,
                envVarsObj
        );
        LoadTestRun testRun = null;
        String interruptionDone = null;

        try {
            VirtualChannel channel = launcher.getChannel();
            if (channel != null) {
                testRun = channel.call(callable);
            }
        } catch (IOException e) {
            logger.println("[ERROR] " + e.getMessage());
        } catch (InterruptedException e) {
            //#region this catch block will only be executed when the job runs on slave

            logger.println("interruption occurred, waiting for execution ending.");
            interruptionDone = "unknown";
            int elapsedWaiting = 0;
            while ("unknown".equals(interruptionDone) && elapsedWaiting < 1000 * 60) {
                Thread.sleep(3000);
                elapsedWaiting += 3000;
                interruptionDone = EnvVars.getRemote(launcher.getChannel()).get(testIdVal + "_INTERRUPTION");
                this.logger.println("still waiting for execution ending...");
            }
            testRun = checkInterruptionDone(run, testRun, resultFile, interruptionDone);
            //#endregion
        }

        if (
                interruptionDone == null
                        && EnvVars.getRemote(launcher.getChannel()).containsKey(testIdVal + "_INTERRUPTION")
        ) {
            //which means the InterruptedException is not caught(running on master)
            interruptionDone = EnvVars.getRemote(launcher.getChannel()).get(testIdVal + "_INTERRUPTION");
            testRun = checkInterruptionDone(run, testRun, resultFile, interruptionDone);
        }

        if (testRun == null) {
            logger.println("run test failed, job end.");
            run.setResult(Result.FAILURE);
            return;
        }

        testRun.getReports().forEach((fileName, content) -> {
            FilePath file = workspace.child(fileName);
            try (OutputStream out = file.write()) {
                out.write(content);
                logger.println("Report file " + file.getRemote() + " created.");
            } catch (IOException | InterruptedException e) {
                logger.println("Error during writing file " + fileName + ", " + e.getMessage());
            }
        });
    }

    private Map<String, String> readConfigFromEnvVars(final Run<?, ?> run, final Launcher launcher) {
        Map<String, String> map = new HashMap<>();
        for (OptionInEnvVars key : OptionInEnvVars.values()) {
            String value = EnvVarsUtil.getEnvVar(run, launcher, key.name());
            if (StringUtils.isNotBlank(value) && !value.equals("0") && !value.equals("false")) {
                this.logger.println("read " + key.name() + " from env vars: " + value);
                map.put(key.name(), "true");
            }
        }
        return map;
    }

    private void printEnvInfo(final Run<?, ?> build) {
        logger.println("=====================================");
        logger.println("Current environment:");
        VersionNumber ver = jenkins.model.Jenkins.getVersion();
        String verStr = "N/A";
        if (ver != null) {
            verStr = ver.toString();
        }
        logger.println("  Jenkins version: " + verStr);
        logger.println("  Java version: " + System.getProperty("java.version"));
        Jenkins instance = Jenkins.getInstanceOrNull();
        String pluginVerStr = "N/A";
        if (instance != null) {
            PluginWrapper plugin = instance.pluginManager.getPlugin("jenkinsStormPlugin");
            if (plugin != null) {
                pluginVerStr = plugin.getVersion();
            }
        }
        logger.println("  LoadRunner Cloud plugin version: " + pluginVerStr);
        logger.println("  Currently running on Jenkins node: " + build.getDisplayName());
        logger.println("=====================================");
    }

    private boolean isDescriptorEmpty() {
        DescriptorImpl descriptor = (DescriptorImpl) this.getDescriptor();
        if (descriptor == null) {
            return true;
        }

        return descriptor.url == null;
    }

    private void printJobParameters(final ServerConfiguration config) {
        JSONObject display = JSONObject.fromObject(config);
        display.remove("password");
        display.remove("proxyConfiguration");
        logger.println("start job with parameters: ");
        logger.println(display);
        logger.println("=====================================");
    }

    private ServerConfiguration createServerConfiguration(
            final DescriptorImpl descriptor,
            final Run<?, ?> run,
            final Launcher launcher) {
        ServerConfiguration config;
        if (descriptor.getUseOAuth()) {
            config = new ServerConfiguration(
                    descriptor.getUrl(),
                    descriptor.getClientId(),
                    Secret.fromString(descriptor.getClientSecret()).getPlainText(),
                    this.tenant,
                    Integer.parseInt(this.getProjectIdAtRunTime(run, launcher)),
                    this.sendEmail,
                    "jenkins-plugin"
            );
        } else {
            config = new ServerConfiguration(
                    descriptor.getUrl(),
                    descriptor.getUsername(),
                    Secret.fromString(descriptor.getPassword()).getPlainText(),
                    this.tenant,
                    Integer.parseInt(this.getProjectIdAtRunTime(run, launcher)),
                    this.sendEmail,
                    "jenkins-plugin"
            );
        }

        printJobParameters(config);
        return config;
    }

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
            logger.println(fieldName + " from parameter: " + fieldValue.toString());
            isLogPrinted.put(key, true);
        }
    }

    private LoadTestRun checkInterruptionDone(
            final Run<?, ?> run,
            final LoadTestRun testRun,
            final FilePath resultFile,
            final String interruptionDone
    ) throws InterruptedException {
        LoadTestRun restored = testRun;
        if ("unknown".equals(interruptionDone)) {
            this.logger.println("Jenkins job interruption handler failed, stop waiting.");
            this.logger.println(
                    "you may need to go to the LoadRunner Cloud website "
                            + "to check if you need to stop the test manually."
            );
        } else {
            restored = restoreTestRunFromFile(resultFile);
            setJenkinsRunResult(run, interruptionDone);
        }
        return restored;
    }

    private LoadTestRun restoreTestRunFromFile(final FilePath resultFile) throws InterruptedException {
        LoadTestRun testRun = null;
        try {
            String result = resultFile.readToString();
            logger.println("execution ended gracefully");

            JsonObject resultObj = new Gson().fromJson(result, JsonObject.class);
            String testRunObj = resultObj.get("testRun").getAsString();
            testRun = new Gson().fromJson(testRunObj, LoadTestRun.class);
        } catch (Exception ex) {
            this.logger.println("failed to get run result after interruption: " + ex.getMessage());
        }
        return testRun;
    }

    private void setJenkinsRunResult(final Run<?, ?> run, final String interruptionDone) {
        if ("ABORTED".equals(interruptionDone)
                || "STOPPED".equals(interruptionDone)
        ) {
            run.setResult(Result.ABORTED);
        } else {
            run.setResult(Result.FAILURE);
        }
    }

    private static class RunTestCallable implements Callable<LoadTestRun, RuntimeException> {

        private final ServerConfiguration serverConfiguration;
        private final TaskListener listener;
        private final String resultStr;
        private final FilePath resultFilePath;

        private final TestRunOptions testRunOptions;
        private final Map<String, String> envVarsOptions;
        private LoadTestRun testRun = null;

        private Runner runner;

        private PrintStream logger() {
            return this.listener.getLogger();
        }

        RunTestCallable(
                final ServerConfiguration serverConfiguration,
                final TaskListener listener,
                final String resultStr,
                final FilePath resultFilePath,
                final TestRunOptions testRunOptions,
                final Map<String, String> envVarsOptions
        ) {
            this.serverConfiguration = serverConfiguration;
            this.listener = listener;
            this.resultStr = resultStr;
            this.resultFilePath = resultFilePath;
            this.testRunOptions = testRunOptions;
            this.envVarsOptions = envVarsOptions;
        }

        @Override
        public LoadTestRun call() {
            String interruptionDoneFlagName = this.testRunOptions.getTestId() + "_INTERRUPTION";
            try {
                EnvVars.masterEnvVars.remove(interruptionDoneFlagName);
                this.runner = new Runner(
                        serverConfiguration,
                        listener.getLogger(),
                        envVarsOptions
                );
                testRun = this.runner.getTestRun();
                testRun = this.runner.run(this.testRunOptions);
                JsonObject buildResult = new Gson().fromJson(this.resultStr, JsonObject.class);
                buildResult.addProperty("testRun", new Gson().toJson(testRun));

                try {
                    resultFilePath.write(buildResult.toString(), "UTF-8");
//                    Utils.getSystemLogger().log(Level.INFO, "result file " + resultFilePath.getRemote());
                } catch (Exception e) {
                    this.listener.getLogger().println(
                            "Writing file [" + resultFilePath.getName() + "] failed, " + e.getMessage()
                    );
                }
                return testRun;
            } catch (InterruptedException | IOException ex) {
                if (ex instanceof IOException && !"thread interrupted".equals(ex.getMessage())) {
                    this.logger().println("error during [call]: " + ex.getMessage());
                    if (runner != null) {
                        return testRun;
                    }
                    return null;
                }

                String abortResult = "unknown";
                EnvVars.masterEnvVars.put(interruptionDoneFlagName, abortResult);
                try {
                    this.listener.getLogger().println("job being interrupted...");
                    abortResult = runner.interruptHandler();
                } catch (IOException | InterruptedException e) {
                    this.listener.getLogger().println("interruption handler failed.");
                    if (e.getMessage() != null) {
                        this.listener.getLogger().println("interruption handler exception: " + e.getMessage());
                    }
                } finally {
                    JsonObject buildResult = new Gson().fromJson(this.resultStr, JsonObject.class);
                    buildResult.addProperty("testRun", new Gson().toJson(testRun));

                    try {
                        resultFilePath.write(buildResult.toString(), "UTF-8");
//                        Utils.getSystemLogger().log(Level.INFO, "result file " + resultFilePath.getRemote());
                    } catch (Exception e) {
                        this.listener.getLogger().println(
                                "Writing file [" + resultFilePath.getName() + "] failed, " + e.getMessage()
                        );
                    }
                    this.listener.getLogger().println("job interruption handler end.");
                    this.listener.getLogger().println(
                            "the final status of LoadRunner Cloud test run is: " + abortResult
                    );
                    EnvVars.masterEnvVars.put(interruptionDoneFlagName, abortResult);
                }
                return testRun;
            } catch (Exception ex) {
                ex.printStackTrace(this.listener.getLogger());
                return null;
            } finally {
                if (runner != null) {
                    runner.close();
                }
            }
        }

        private static final long serialVersionUID = 1L;

        @Override
        public void checkRoles(final RoleChecker roleChecker) throws SecurityException {
            // noop
        }
    }
}
