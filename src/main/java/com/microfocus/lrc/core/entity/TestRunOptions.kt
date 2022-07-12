package com.microfocus.lrc.core.entity

import java.io.Serializable

class TestRunOptions(
    val testId: Int,
    val sendEmail: Boolean
): Serializable {
}