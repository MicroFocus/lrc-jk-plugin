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

import org.apache.commons.lang.StringUtils;

public final class Utils {
    public static boolean isPositiveInteger(final String str) {
        int val;
        try {
            val = Integer.parseInt(str);
        } catch (NumberFormatException ex) {
            return false;
        }

        return (val > 0);
    }

    public static boolean isEmpty(final String str) {
        return StringUtils.isEmpty(str) || StringUtils.isBlank(str);
    }

    private Utils() {
        throw new IllegalStateException("Utility class");
    }
}
