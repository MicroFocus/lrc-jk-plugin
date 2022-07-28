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

import com.microfocus.lrc.core.entity.TrendingConfiguration;
import hudson.model.Action;
import hudson.model.Job;
import hudson.model.Run;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.logging.Level;

public final class TestRunReportProjectAction implements Action {

    private Job<?, ?> project;
    private TrendingConfiguration trendingConfig;

    public String getIconFileName() {
        return "notepad.gif";
    }

    public boolean isVisible() {
        for (Run<?, ?> build : this.getProject().getBuilds()) {
            TestRunReportBuildAction buildAction = build.getAction(TestRunReportBuildAction.class);
            if (buildAction != null && buildAction.getTrendingDataWrapper() != null) {
                return true;
            }
        }
        return false;
    }

    public String getDisplayName() {
        return "LoadRunner Cloud Trend";
    }

    public String getUrlName() {
        return "srl_project_report";
    }

    TestRunReportProjectAction(final Job<?, ?> project, final TrendingConfiguration trendingConfig) {
        this.setProject(project);
        this.setTrendingConfig(trendingConfig);
    }


    public void doDynamic(final StaplerRequest req, final StaplerResponse response)
            throws IOException, ServletException {
        String htmlContent = null;
        String queryString = req.getQueryString();
        boolean forceUpdate = false;
        boolean extraContent = false;
        if (queryString != null && queryString.contains("force_update")) {
            forceUpdate = true;

            // extraContent only works when forceUpdate
            // otherwise it will read cache directly
            if (queryString.contains("extra")) {
                extraContent = true;
            }
        }

        TestRunReportBuildAction buildAction =
                TestRunReportBuildAction.getLastBuildActionHasTrendingData(this.project);
        if (buildAction == null) {
            LoggerProxy.getSysLogger().log(
                    Level.INFO,
                    String.format(
                            "no valid build found for project#%s, cannot display trending report.",
                            this.project.getName()
                    )
            );
        } else {
            LoggerProxy.getSysLogger().log(
                    Level.FINE,
                    String.format(
                            "build action found: #%d",
                            buildAction.getRun().getNumber()
                    )
            );
            if (forceUpdate || buildAction.getTrendingReportHTML() == null) {
                htmlContent = TrendingReport.generateReport(
                        this.project,
                        this.trendingConfig,
                        forceUpdate,
                        extraContent
                );
                buildAction.setTrendingReportHTML(htmlContent);
            } else {
                htmlContent = buildAction.getTrendingReportHTML();
            }
        }


        if (htmlContent == null) {
            htmlContent = "<h1>Failed to generate report.</h1>";
        }

        org.kohsuke.stapler.HttpResponses.literalHtml(htmlContent).generateResponse(req, response, this);
    }

    public Job<?, ?> getProject() {
        return project;
    }

    public void setProject(final Job<?, ?> project) {
        this.project = project;
    }

    private TrendingConfiguration getTrendingConfig() {
        return trendingConfig;
    }

    private void setTrendingConfig(final TrendingConfiguration trendingConfig) {
        this.trendingConfig = trendingConfig;
    }
}
