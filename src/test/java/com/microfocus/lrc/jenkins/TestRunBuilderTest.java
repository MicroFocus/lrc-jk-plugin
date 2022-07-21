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

import com.gargoylesoftware.htmlunit.html.HtmlElement;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlInput;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.microfocus.lrc.MockServerResponseGenerator;
import com.microfocus.lrc.core.entity.LoadTestRun;
import com.microfocus.lrc.core.entity.OptionInEnvVars;
import com.microfocus.lrc.core.entity.TestRunStatus;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.*;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.BufferedReader;
import java.io.IOException;

public class TestRunBuilderTest {
    public static MockWebServer mockserver = new MockWebServer();
    static int serverPort = 0;
    @Rule
    public JenkinsRule jenkins = new JenkinsRule();

    @BeforeClass
    public static void setUp() throws IOException {
        mockserver.start();
        serverPort = mockserver.getPort();
    }

    @AfterClass
    public static void tearDown() throws IOException {
        mockserver.close();
    }

    @Test
    public void testConfigRoundtrip() throws Exception {
        FreeStyleProject project = jenkins.createFreeStyleProject();
        TestRunBuilder builder = new TestRunBuilder("FAKEPROJECT", "FAKETESTID", false);
        TestRunBuilder.DescriptorImpl descriptor = jenkins.get(TestRunBuilder.DescriptorImpl.class);
        String baseUrl = mockserver.url("/").toString();
        descriptor.setUrl(baseUrl);
        descriptor.save();

        project.getBuildersList().add(builder);
        project = jenkins.configRoundtrip(project);
        jenkins.assertEqualDataBoundBeans(builder, project.getBuildersList().get(0));
        TestRunBuilder.DescriptorImpl d = (TestRunBuilder.DescriptorImpl) (project.getBuildersList().get(0).getDescriptor());
        Assert.assertEquals(d.getUrl(), baseUrl);
    }

    @Test
    public void testGlobalConfigRoundtrip() throws Exception {
        Class<TestRunBuilder.DescriptorImpl> descriptorClass = TestRunBuilder.DescriptorImpl.class;
        Assert.assertNull(jenkins.get(descriptorClass).getUrl());
        Assert.assertNull(jenkins.get(descriptorClass).getUsername());
        try (JenkinsRule.WebClient client = jenkins.createWebClient()) {
            HtmlForm form = client.goTo("configure").getFormByName("config");
            HtmlElement div = form.getElementsByAttribute("div", "name", "com-microfocus-lrc-jenkins-TestRunBuilder").get(0);
            HtmlElement pDiv = (HtmlElement) div.getParentNode();
            HtmlInput urlInput = (HtmlInput) pDiv.getElementsByAttribute("input", "name", "_.url").get(0);
            urlInput.setValueAttribute("FAKE_URL");
            jenkins.submit(form);

            client.goTo("configure").refresh();
        }

        Assert.assertEquals("FAKE_URL", jenkins.get(descriptorClass).getUrl());
        Assert.assertEquals("", jenkins.get(descriptorClass).getUsername());
    }

    private void mockResponseNormal() {
        MockServerResponseGenerator.mockLogin(mockserver);

        MockResponse responseGetLoadTest = new MockResponse();
        JsonObject loadTestResObj = new JsonObject();
        loadTestResObj.addProperty("name", "fake_load_test");
        responseGetLoadTest.setBody(loadTestResObj.toString());
        mockserver.enqueue(responseGetLoadTest);

        MockResponse responseStartTest = new MockResponse();
        JsonObject startTestResObj = new JsonObject();
        startTestResObj.addProperty("runId", -1);
        responseStartTest.setBody(startTestResObj.toString());
        mockserver.enqueue(responseStartTest);

        MockResponse responseRunStatus = new MockResponse();
        JsonObject runStatusResObj = new JsonObject();
        runStatusResObj.addProperty("status", "INIT");
        runStatusResObj.addProperty("detailedStatus", "INITIALIZING");
        runStatusResObj.addProperty("hasReport", false);
        responseRunStatus.setBody(runStatusResObj.toString());
        for (int i = 0; i < 3; i++) {
            mockserver.enqueue(responseRunStatus);
        }

        runStatusResObj.addProperty("status", TestRunStatus.PASSED.getStatusName());
        runStatusResObj.addProperty("hasReport", false);
        runStatusResObj.addProperty("detailedStatus", TestRunStatus.PASSED.getStatusName());
        MockResponse responseRunStatusDone = new MockResponse().setBody(runStatusResObj.toString());
        mockserver.enqueue(responseRunStatusDone);

        runStatusResObj.addProperty("hasReport", true);
        MockResponse responseRunStatusHasReport = new MockResponse().setBody(runStatusResObj.toString());
        mockserver.enqueue(responseRunStatusHasReport);

        // repeat 3 times for csv, docx and pdf download
        // #TODO: how to handle parallel downloading here
        for (int i = 0; i < 3; i += 1) {
            JsonObject genReportResObj = new JsonObject();
            genReportResObj.addProperty("reportId", -999);
            MockResponse responseGenReport = new MockResponse().setBody(genReportResObj.toString());
            mockserver.enqueue(responseGenReport);

            JsonObject reportStatusResObj = new JsonObject();
            reportStatusResObj.addProperty("message", "In progress");
            MockResponse responseReportStatus = new MockResponse().setBody(reportStatusResObj.toString());
            responseReportStatus.setHeader("Content-Type", "application/json");
            mockserver.enqueue(responseReportStatus);

            String fakeReportContent = "FAKE_REPORT_CONTENT";
            MockResponse responseReportContent = new MockResponse().setBody(fakeReportContent);
            responseReportContent.setHeader("Content-Type", "application/octet-stream");
            mockserver.enqueue(responseReportContent);
        }

        MockServerResponseGenerator.mockTransactions(mockserver);
    }

    @Test
    public void testBuild() throws Exception {
        EnvVars.masterEnvVars.put(OptionInEnvVars.LRC_DEBUG_LOG.name(), "false");

        FreeStyleProject project = jenkins.createFreeStyleProject();
        TestRunBuilder builder = new TestRunBuilder("99", "999", false);
        project.getBuildersList().add(builder);
        TestRunBuilder.DescriptorImpl descriptor = jenkins.get(TestRunBuilder.DescriptorImpl.class);
        String baseUrl = mockserver.url("/").toString();
        descriptor.setUrl(baseUrl);
        descriptor.setClientId("FAKE_CLIENT_ID");
        descriptor.setClientSecret("FAKE_CLIENT_SECRET");
        descriptor.setTenantId("FAKE_TENANT_ID");
        descriptor.setUseOAuth(true);

        descriptor.save();

        this.mockResponseNormal();

        FreeStyleBuild b = jenkins.buildAndAssertStatus(Result.SUCCESS, project);
        BufferedReader rd = new BufferedReader(b.getLogReader());
        while (rd.ready()) {
            // print jenkins logs for debugging
            System.out.println(rd.readLine());
        }
        jenkins.assertLogContains("load test [fake_load_test] is going to start...", b);
        // assert workspace has report files
        FilePath workspace = b.getWorkspace();
        assert workspace != null;
        Assert.assertTrue(workspace.child("lrc_report_FAKE_TENANT_ID--1.xml").exists());
        Assert.assertTrue(workspace.child("lrc_report_FAKE_TENANT_ID--1.docx").exists());
        Assert.assertTrue(workspace.child("lrc_report_FAKE_TENANT_ID--1.pdf").exists());
        Assert.assertTrue(workspace.child("lrc_report_FAKE_TENANT_ID--1.csv").exists());
        Assert.assertTrue(workspace.child("lrc_report_trans_FAKE_TENANT_ID--1.csv").exists());
        FilePath buildResultFile = workspace.child("build_result_" + b.getId());

        Assert.assertTrue(buildResultFile.exists());
        // assert build result file contains correct data
        String buildResult = buildResultFile.readToString();
        JsonObject resultObj = new Gson().fromJson(buildResult, JsonObject.class);
        LoadTestRun testRun = new Gson().fromJson(resultObj.get("testRun").getAsString(), LoadTestRun.class);
        System.out.println(resultObj);
        Assert.assertEquals(testRun.getId(), -1);
    }
}
