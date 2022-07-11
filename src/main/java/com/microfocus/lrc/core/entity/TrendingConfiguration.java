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
    private Integer runsCount;
    private Integer benchmark;
    private Integer trtPctlTholdImpr;
    private Integer trtPctlTholdMinorRegr;
    private Integer trtPctlTholdMajorRegr;

    private Integer trtAvgTholdImpr;
    private Integer trtAvgTholdMinorRegr;
    private Integer trtAvgTholdMajorRegr;

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

    @SuppressWarnings("checkstyle:ParameterNumber")
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
