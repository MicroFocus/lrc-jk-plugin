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

import hudson.Extension;
import hudson.model.Action;
import hudson.model.Job;
import jenkins.model.TransientActionFactory;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.logging.Level;

@Extension
public final class WorkflowActionFactory extends TransientActionFactory<Job> {

    @Override
    public Class<Job> type() {
        return Job.class;
    }

    @NotNull
    @Override
    public Collection<? extends Action> createFor(final Job job) {
        TestRunReportBuildAction buildAction = TestRunReportBuildAction.getLastBuildActionHasTrendingData(job);
        if (buildAction != null) {
            try {
                LoggerProxy.getSysLogger().log(
                        Level.FINE,
                        "*******valid build action found*******\n"
                                + "build#"
                                + buildAction.getRun().getId() + "\n"
                                + "trendingConfig "
                                + buildAction.getTrendingConfig().toString() + "\n"
                                + "trendingReport "
                                + (buildAction.getTrendingReportHTML() == null
                                ? "NULL"
                                : buildAction.getTrendingReportHTML().length())
                );
            } catch (Exception ignored) {

            }
            return Collections.singletonList(new TestRunReportProjectAction(job, buildAction.getTrendingConfig()));
        }
        return Collections.emptyList();
    }

}
