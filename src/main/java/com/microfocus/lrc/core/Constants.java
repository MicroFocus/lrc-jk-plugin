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

package com.microfocus.lrc.core;

public final class Constants {
    public static final String URL = "url";
    public static final String USERNAME = "username";
    public static final String PASSWORD = "password";
    public static final String USE_OAUTH = "useOAuth";
    public static final String CLIENT_ID = "clientId";
    public static final String CLIENT_SECRET = "clientSecret";
    public static final String TENANTID = "tenantId";
    public static final String PROJECTID = "projectId";
    public static final String SENDEMAIL = "sendEmail";
    public static final String APPLICATION_JSON = "application/json";
    public static final String BENCHMARK = "benchmark";
    public static final String UNKNOWN = "unknown";
    public static final String TESTRUN = "testRun";

    private Constants() {
        throw new IllegalStateException("Utility class");
    }
}
