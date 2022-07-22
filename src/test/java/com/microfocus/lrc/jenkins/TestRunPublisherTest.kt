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
import com.google.gson.JsonObject
import com.microfocus.lrc.MockServerResponseGenerator
import com.microfocus.lrc.core.entity.*
import com.microfocus.lrc.core.Constants
import hudson.EnvVars
import hudson.Launcher
import hudson.model.AbstractBuild
import hudson.model.BuildListener
import okhttp3.mockwebserver.MockWebServer
import org.junit.*
import org.jvnet.hudson.test.JenkinsRule
import org.jvnet.hudson.test.TestBuilder

class TestRunPublisherTest {


    @get:Rule
    var jenkins = JenkinsRule()

    companion object {
        var mockserver = MockWebServer()
        var serverPort = 0

        @BeforeClass
        @JvmStatic
        fun setup() {
            mockserver.start()
            serverPort = mockserver.port
        }

        @AfterClass
        @JvmStatic
        fun teardown() {
            mockserver.shutdown()
        }
    }

    @Test
    @Throws(Exception::class)
    fun testConfigRoundtrip() {
        var project = jenkins.createFreeStyleProject()
        val publisher = TestRunPublisher(
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
        );

        val descriptor = jenkins.get(TestRunBuilder.DescriptorImpl::class.java);
        val baseUrl = TestRunBuilderTest.mockserver.url("/").toString();
        descriptor.url = baseUrl;
        descriptor.save();
        project.publishersList.add(publisher);
        project = jenkins.configRoundtrip(project);
        val p = project.publishersList[0] as TestRunPublisher;
        jenkins.assertEqualDataBoundBeans(publisher, p);
        val defaultRunsCount = 5;
        Assert.assertEquals(defaultRunsCount, p.runsCount);
    }

    @Test
    fun testPerform() {
        EnvVars.masterEnvVars[OptionInEnvVars.LRC_DEBUG_LOG.name] = "true";

        val project = jenkins.createFreeStyleProject();
        val publisher = TestRunPublisher(
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
        );

        project.publishersList.add(publisher);
        val descriptor = jenkins.get(TestRunBuilder.DescriptorImpl::class.java);
        val baseUrl = TestRunBuilderTest.mockserver.url("/").toString();
        descriptor.url = baseUrl;
        descriptor.clientId = "FAKE_CLIENT_ID";
        descriptor.clientSecret = "FAKE_CLIENT_SECRET";
        descriptor.tenantId = "FAKE_TENANT_ID";
        descriptor.useOAuth = true;

        descriptor.save();

        // create a mock "buildResult" for publisher to consume.
        project.buildersList.add(object : TestBuilder() {
            override fun perform(build: AbstractBuild<*, *>, launcher: Launcher, listener: BuildListener): Boolean {
                val buildResultObj = JsonObject();

                val lt = LoadTest(-1, 99);
                val testRun = LoadTestRun(-1, lt);
                testRun.statusEnum = TestRunStatus.PASSED;

                val opt = TestRunOptions(lt.id, false);

                buildResultObj.addProperty("testRun", Gson().toJson(testRun));
                buildResultObj.addProperty("testOptions", Gson().toJson(opt));

                build.workspace!!.child("lrc_run_result_${build.id}").write(
                    buildResultObj.toString(),
                    Charsets.UTF_8.name()
                );

                return true;
            }

        });

        MockServerResponseGenerator.mockLogin(mockserver);
        MockServerResponseGenerator.mockTestRunResults(mockserver);
        MockServerResponseGenerator.mockTransactions(mockserver);

        val build = jenkins.buildAndAssertSuccess(project);
        val action = build.getAction(TestRunReportBuildAction::class.java);
        assert(action != null);
        println(action.trendingDataWrapper.tenantId);
    }
}
