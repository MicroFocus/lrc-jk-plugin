package com.microfocus.lrc.core.entity

import org.junit.Test
import kotlin.math.pow

class TestRunResultsResponseTest {
    @Test
    fun rmThoughputUnit() {
        var withoutUnit = 123456789.toDouble();
        var withUnit = "$withoutUnit bytes/s";
        var rmThroughputUnit = TestRunResultsResponse.rmThroughputUnit(withUnit);
        assert(rmThroughputUnit == withoutUnit);

        withUnit = "123456789 KB/s";
        rmThroughputUnit = TestRunResultsResponse.rmThroughputUnit(withUnit);
        assert(rmThroughputUnit == withoutUnit * 1024);

        withUnit = "123456789 MB/s";
        rmThroughputUnit = TestRunResultsResponse.rmThroughputUnit(withUnit);
        assert(rmThroughputUnit == withoutUnit * 1024.0.pow(2));
    }

}