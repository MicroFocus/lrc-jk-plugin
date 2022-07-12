package com.microfocus.lrc.core.entity

import java.io.Serializable

class LoadTest(
    val id: Int,
    val projectId: Int,
): Serializable {
    var name: String = "";
}