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
import com.microfocus.lrc.core.entity.TrendingConfiguration;
import com.microfocus.lrc.core.entity.TrendingDataWrapper;
import hudson.model.Job;
import hudson.model.Run;
import hudson.util.HttpResponses;
import jenkins.model.RunAction2;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.IOException;

public final class TestRunReportBuildAction implements RunAction2 {
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
        return "srl_build_report";
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
        HttpResponses.html(jsonStr).generateResponse(req, response, this);
    }

    public Run getRun() {
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

    public static TestRunReportBuildAction getLastBuildActionHasTrendingData(final Job job) {
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
