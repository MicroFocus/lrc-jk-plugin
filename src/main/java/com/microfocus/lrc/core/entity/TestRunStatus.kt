package com.microfocus.lrc.core.entity

interface ITestRunStatus {
    val statusName: String;
    val isEnded: Boolean;
    val isError: Boolean;
    val isSuccess: Boolean;
}

enum class TestRunStatus(val value: String): ITestRunStatus, java.io.Serializable {
    INITIALIZING("INITIALIZING") {
        override val statusName = "INITIALIZING";
        override val isEnded = false;
        override val isError = false;
        override val isSuccess = false;
    },

    RUNNING("RUNNING") {
        override val statusName = "RUNNING";
        override val isEnded = false;
        override val isError = false;
        override val isSuccess = false;
    },

    STOPPING("STOPPING") {
        override val statusName = "STOPPING";
        override val isEnded = false;
        override val isError = false;
        override val isSuccess = false;
    },

    SYSTEM_ERROR("SYSTEM_ERROR") {
        override val statusName = "SYSTEM_ERROR";
        override val isEnded = true;
        override val isError = true;
        override val isSuccess = false;
    },

    ABORTED("ABORTED") {
        override val statusName = "ABORTED";
        override val isEnded = true;
        override val isError = false;
        override val isSuccess = false;
    },

    FAILED("FAILED") {
        override val statusName = "FAILED";
        override val isEnded = true;
        override val isError = false;
        override val isSuccess = false;
    },

    PASSED("PASSED") {
        override val statusName = "PASSED";
        override val isEnded = true;
        override val isError = false;
        override val isSuccess = true;
    },

    STOPPED("STOPPED") {
        override val statusName = "STOPPED";
        override val isEnded = true;
        override val isError = false;
        override val isSuccess = false;
    },

    HALTED("HALTED") {
        override val statusName = "HALTED";
        override val isEnded = true;
        override val isError = false;
        override val isSuccess = false;
    },

    NA("NA") {
        override val statusName = "NA";
        override val isEnded = false;
        override val isError = false;
        override val isSuccess = false;
    };
}