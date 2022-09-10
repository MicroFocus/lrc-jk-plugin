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

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.sf.json.JSONArray;
import org.apache.commons.lang.StringUtils;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;

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

    public static JsonObject parseJsonString(final String str, final String errMsg) throws IOException {
        try {
            return new Gson().fromJson(str, JsonObject.class);
        } catch (Exception ex) {
            throw new IOException(errMsg);
        }
    }

    public static boolean isValidUrl(final String url) {
        try {
            new URL(url);
            return true;
        } catch (MalformedURLException ex) {
            return false;
        }
    }

    public static final int MASK_PREFIX_LEN = 4;
    public static final int MASK_SUFFIX_LEN = 4;
    public static String maskString(final String str, final int prefixLen, final int suffixLen) {
        if (Utils.isEmpty(str)) {
            return str;
        }

        int len = str.length();
        if (len <= prefixLen + suffixLen) {
            return str;
        }

        char[] chars = str.toCharArray();
        Arrays.fill(chars, prefixLen, len - suffixLen, '*');

        return new String(chars);
    }

    public static void logException(final LoggerProxy logger, final String msg, final Exception ex) {
        if (ex.getMessage() != null) {
            logger.error(msg + ex.getMessage());
        } else {
            logger.error(msg);
        }
    }

    public static boolean isValidJsonArray(final String str) {
        try {
            JSONArray.fromObject(str);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Utils() {
        throw new IllegalStateException("Utility class");
    }
}
