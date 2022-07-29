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

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Action;
import hudson.model.Job;
import jenkins.model.TransientActionFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.logging.Level;

@SuppressWarnings("java:S3740")
@Extension
public final class WorkflowActionFactory extends TransientActionFactory<Job> {

    @Override
    public Class<Job> type() {
        return Job.class;
    }

    @SuppressWarnings("java:S2629")
    @NonNull
    @Override
    public Collection<? extends Action> createFor(@NonNull final Job job) {
        TestRunReportBuildAction buildAction = TestRunReportBuildAction.getLastBuildActionHasTrendingData(job);
        if (buildAction != null) {
            try {
                LoggerProxy.getSysLogger().log(
                        Level.FINE,
                        String.format(
                                "*******valid build action found*******%n"
                                        + "build#%s%n"
                                        + "trendingConfig %s%n"
                                        + "trendingReport %s",
                                buildAction.getRun().getId(),
                                buildAction.getTrendingConfig().toString(),
                                buildAction.getTrendingReportHTML() == null
                                        ? "NULL"
                                        : buildAction.getTrendingReportHTML().length()
                        )
                );
            } catch (Exception ignored) {
                // ignore
            }
            return Collections.singletonList(new TestRunReportProjectAction(job, buildAction.getTrendingConfig()));
        }
        return Collections.emptyList();
    }

}
