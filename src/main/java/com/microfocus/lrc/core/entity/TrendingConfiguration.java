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

package com.microfocus.lrc.core.entity;

import java.io.Serializable;


/**
 * Configuration of trending report, used to get trending data
 * and generate report.
 *
 * This class will be persisted by Jenkins,
 * as a member field of StormTestReportBuildAction and StormTestReportProjectAction.
 * Any changes could cause the history data broken.
 */
public final class TrendingConfiguration implements Serializable {
    private final boolean isBenchmarkPrev;
    private final Integer runsCount;
    private final Integer benchmark;
    private final Integer trtPctlTholdImpr;
    private final Integer trtPctlTholdMinorRegr;
    private final Integer trtPctlTholdMajorRegr;

    private final Integer trtAvgTholdImpr;
    private final Integer trtAvgTholdMinorRegr;
    private final Integer trtAvgTholdMajorRegr;

    //#region accessors
    public Integer getRunsCount() {
        return runsCount;
    }

    public Integer getBenchmark() {
        return benchmark;
    }

    public Integer getTrtPctlTholdImpr() {
        return trtPctlTholdImpr;
    }

    public Integer getTrtPctlTholdMinorRegr() {
        return trtPctlTholdMinorRegr;
    }

    public Integer getTrtPctlTholdMajorRegr() {
        return trtPctlTholdMajorRegr;
    }

    public Integer getTrtAvgTholdImpr() {
        return trtAvgTholdImpr;
    }

    public Integer getTrtAvgTholdMinorRegr() {
        return trtAvgTholdMinorRegr;
    }

    public Integer getTrtAvgTholdMajorRegr() {
        return trtAvgTholdMajorRegr;
    }
    //#endregion

    @SuppressWarnings({"checkstyle:ParameterNumber", "java:S107"})
    public TrendingConfiguration(
            final Integer runsCount,
            final Integer benchmark,

            final Integer trtPctlTholdImpr,
            final Integer trtPctlTholdMinorRegr,
            final Integer trtPctlTholdMajorRegr,

            final Integer trtAvgTholdImpr,
            final Integer trtAvgTholdMinorRegr,
            final Integer trtAvgTholdMajorRegr,

            final boolean isBenchmarkPrev
    ) {
        this.runsCount = runsCount;
        //treat 0 as NULL (since a runId could not be 0)

        //this is a workaround for pipeline because
        //the saving mechanism doesn't allow a Nullable integer
        this.benchmark = benchmark == 0 ? null : benchmark;

        this.trtPctlTholdImpr = trtPctlTholdImpr;
        this.trtPctlTholdMinorRegr = trtPctlTholdMinorRegr;
        this.trtPctlTholdMajorRegr = trtPctlTholdMajorRegr;

        this.trtAvgTholdImpr = trtAvgTholdImpr;
        this.trtAvgTholdMinorRegr = trtAvgTholdMinorRegr;
        this.trtAvgTholdMajorRegr = trtAvgTholdMajorRegr;

        this.isBenchmarkPrev = isBenchmarkPrev;

    }

    public boolean isBenchmarkPrev() {
        return isBenchmarkPrev;
    }
}
