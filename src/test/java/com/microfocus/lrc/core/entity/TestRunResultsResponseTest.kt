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

package com.microfocus.lrc.core.entity

import org.junit.Test
import kotlin.math.pow

class TestRunResultsResponseTest {
    @Test
    fun rmThoughputUnit() {
        val withoutUnit = 123456789.toDouble();
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
