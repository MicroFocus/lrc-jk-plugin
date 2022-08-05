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
import com.microfocus.lrc.core.entity.TrendingConfiguration;
import com.microfocus.lrc.core.entity.TrendingDataWrapper;
import hudson.model.Job;
import hudson.model.Run;
import jenkins.model.RunAction2;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.IOException;

public final class TestRunReportBuildAction implements RunAction2 {
    @SuppressWarnings("java:S2065")
    private transient Run<?, ?> run;
    private final TrendingDataWrapper trendingDataWrapper;
    private final TrendingConfiguration trendingConfig;
    private String trendingReportHTML;

    public String getIconFileName() {
        return null;
    }

    public String getDisplayName() {
        return "LoadRunner Cloud Build Report";
    }

    public String getUrlName() {
        return "lrc_build_report";
    }

    TestRunReportBuildAction(
            final Run<?, ?> build,
            final TrendingDataWrapper trendingDataWrapper,
            final TrendingConfiguration trendingConfig
    ) {
        this.trendingDataWrapper = trendingDataWrapper;
        this.trendingConfig = trendingConfig;
        this.run = build;
    }

    /**
     * write the trending report to the response.
     *
     * @param req
     * @param response
     * @throws IOException
     * @throws ServletException
     */
    public void doDynamic(final StaplerRequest req, final StaplerResponse response)
            throws IOException, ServletException {
        String jsonStr = new Gson().toJson(this.trendingDataWrapper);
        jsonStr = "<pre>" + jsonStr + "</pre>";
        String trendingConfigStr =
                this.trendingConfig == null
                        ? "NULL"
                        : "<pre>" + new Gson().toJson(this.trendingConfig) + "</pre>";

        jsonStr = jsonStr + "<br>" + trendingConfigStr + "<br>" + this.trendingReportHTML;
        org.kohsuke.stapler.HttpResponses.literalHtml(jsonStr).generateResponse(req, response, this);
    }

    @SuppressWarnings("java:S1452")
    public Run<?, ?> getRun() {
        return run;
    }

    public TrendingDataWrapper getTrendingDataWrapper() {
        return trendingDataWrapper;
    }

    public TrendingConfiguration getTrendingConfig() {
        return trendingConfig;
    }

    @Override
    public void onAttached(final Run<?, ?> r) {
        this.run = r;
    }

    @Override
    public void onLoad(final Run<?, ?> r) {
        this.run = r;
    }

    public String getTrendingReportHTML() {
        return trendingReportHTML;
    }

    public void setTrendingReportHTML(final String trendingReportHTML) {
        this.trendingReportHTML = trendingReportHTML;
    }

    public static TestRunReportBuildAction getLastBuildActionHasTrendingData(final Job<?, ?> job) {
        Run<?, ?> r = (job.getLastBuild());
        while (true) {
            if (r == null) {
                return null;
            }

            TestRunReportBuildAction buildAction = r.getAction(TestRunReportBuildAction.class);
            if (buildAction != null) {
                TrendingDataWrapper trendingDataWrapper = buildAction.getTrendingDataWrapper();
                if (trendingDataWrapper != null) {
                    return buildAction;
                }
            }

            r = r.getPreviousBuild();
        }
    }
}
