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

package com.microfocus.lrc.core

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.microfocus.lrc.core.entity.ServerConfiguration
import com.microfocus.lrc.jenkins.LoggerProxy
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.Closeable
import java.io.IOException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLHandshakeException

class ApiClient internal constructor(
    private val serverConfiguration: ServerConfiguration,
    private val loggerProxy: LoggerProxy
): Closeable {
    private companion object {
        val MEDIA_TYPE_JSON = Constants.APPLICATION_JSON.toMediaType();

        @JvmStatic
        fun isOAuthClientId(username: String?): Boolean {
            return username != null
                    && username.length >= 42
                    && username.startsWith("oauth2-")
                    && username.endsWith("@microfocus.com");
        }
    }

    private var tokenAuth: String? = null;
    private var csrfCookieStr: String? = null;
    private var okHttpClient: OkHttpClient? = null;

    private fun prepareUrlBuilder(apiPath: String): HttpUrl.Builder {
        val urlBuilder: HttpUrl.Builder = this.parseURL(this.serverConfiguration.url)
            .newBuilder()
            .addPathSegments(apiPath);

        if (!this.serverConfiguration.tenantId.equals("")) {
            urlBuilder.addQueryParameter("projectId", this.serverConfiguration.projectId.toString());
            urlBuilder.addQueryParameter("TENANTID", this.serverConfiguration.tenantId);
        }

        return urlBuilder;
    }

    private fun prepareRequestBuilder(
        url: HttpUrl,
        contentType: String = Constants.APPLICATION_JSON
    ): Request.Builder {
        val reqBuilder: Request.Builder = Request.Builder()
            .url(url)
            .addHeader("Content-Type", contentType)
            .addHeader("cache-control", "no-cache");

        if (this.csrfCookieStr != null) {
            val csrf = "LWSSO_COOKIE_KEY=${this.csrfCookieStr}";
            reqBuilder.addHeader("Cookie", csrf);
        }

        if (this.tokenAuth != null) {
            reqBuilder.addHeader("Authorization", "Bearer ${this.tokenAuth}");
        }

        return reqBuilder;
    }

    fun get(
        apiPath: String,
        queryParams: Map<String, String>? = null,
        encodedQueryParams: Map<String, String>? = null,
        contentType: String = Constants.APPLICATION_JSON
    ): Response {
        val urlBuilder = this.prepareUrlBuilder(apiPath);

        if (!queryParams.isNullOrEmpty()) {
            queryParams.forEach { (k, v) -> run { urlBuilder.addQueryParameter(k, v); } }
        }

        if (!encodedQueryParams.isNullOrEmpty()) {
            encodedQueryParams.forEach { (k, v) -> run { urlBuilder.addEncodedQueryParameter(k, v) } };
        }

        val reqBuilder = this.prepareRequestBuilder(urlBuilder.build(), contentType).get();

        try {
            return this.getOkhttpClient().newCall(reqBuilder.build()).execute();
        } catch (ex: UnknownHostException) {
            throw IOException("Cannot resolve hostname: ${ex.message}, please check your configuration");
        } catch (ex: SSLHandshakeException) {
            throw IOException("Cannot access server, please check if you are behind a proxy server");
        }
    }

    fun post(apiPath: String, queryParams: Map<String, String>? = null, payload: JsonObject): Response {
        val urlBuilder = this.prepareUrlBuilder(apiPath);
        if (!queryParams.isNullOrEmpty()) {
            queryParams.forEach { (k, v) -> run { urlBuilder.addQueryParameter(k, v); } }
        }
        val reqBuilder = this.prepareRequestBuilder(urlBuilder.build());

        reqBuilder.post(payload.toString().toRequestBody(MEDIA_TYPE_JSON));

        try {
            return this.getOkhttpClient().newCall(reqBuilder.build()).execute();
        } catch (ex: UnknownHostException) {
            throw IOException("Cannot resolve hostname: ${ex.message}, please check your configuration");
        } catch (ex: SSLHandshakeException) {
            throw IOException("Cannot access server, please check if you are behind a proxy server");
        }
    }

    fun put(apiPath: String, queryParams: Map<String, String>? = null, payload: JsonObject): Response {
        val urlBuilder = this.prepareUrlBuilder(apiPath);

        if (!queryParams.isNullOrEmpty()) {
            queryParams.forEach { (k, v) -> run { urlBuilder.addQueryParameter(k, v); } }
        }

        val reqBuilder = this.prepareRequestBuilder(urlBuilder.build());

        reqBuilder.put(payload.toString().toRequestBody(MEDIA_TYPE_JSON));

        try {
            return this.getOkhttpClient().newCall(reqBuilder.build()).execute();
        } catch (ex: UnknownHostException) {
            throw IOException("Cannot resolve hostname: ${ex.message}, please check your configuration");
        } catch (ex: SSLHandshakeException) {
            throw IOException("Cannot access server, please check if you are behind a proxy server");
        }
    }

    fun login() {
        if (isOAuthClientId(this.serverConfiguration.username)) {
            return this.loginOAuth()
        }

        val payload = JsonObject();
        payload.addProperty("user", this.serverConfiguration.username);
        payload.addProperty("password", this.serverConfiguration.password);

        val res = this.post("v1/auth", payload = payload);
        if (res.code != 200) {
            throw IOException("login to ${this.serverConfiguration.url} failed: ${res.code}, ${res.body?.string()}, ${res.message}");
        }
        val resObj = Gson().fromJson(res.body?.string(), JsonObject::class.java);
        if (resObj.has("token")) {
            this.csrfCookieStr = resObj["token"].asString;
        } else {
            throw IOException("login to ${this.serverConfiguration.url} failed, invalid response ${res.body?.string()}");
        }
    }

    private fun loginOAuth() {
        val payload = JsonObject();
        payload.addProperty("client_id", this.serverConfiguration.username);
        payload.addProperty("client_secret", this.serverConfiguration.password);

        val res = this.post("v1/auth-client", payload = payload);
        if (res.code != 200) {
            throw IOException("login to ${this.serverConfiguration.url} failed: ${res.code}, ${res.body?.string()}, ${res.message}");
        }
        val body = res.body?.string();
        val resObj = Gson().fromJson(body, JsonObject::class.java);
        if (resObj.has("token")) {
            this.tokenAuth = resObj["token"].asString;
        } else {
            throw IOException("login to ${this.serverConfiguration.url} failed, invalid response ${res.body?.string()}");
        }
    }

    fun getServerConfiguration(): ServerConfiguration {
        return this.serverConfiguration;
    }

    private fun getOkhttpClient(): OkHttpClient {
        var c = this.okHttpClient;
        if (c != null) {
            return c;
        }

        val builder = OkHttpClient.Builder();
        val proxyConfiguration = this.serverConfiguration.proxyConfiguration;
        if (this.serverConfiguration.proxyConfiguration != null) {
            builder.proxy(proxyConfiguration.proxy);
            if (proxyConfiguration.username != null && proxyConfiguration.password != null) {
                val auth = Authenticator { _, response ->
                    val cred: String = Credentials.basic(proxyConfiguration.username, proxyConfiguration.password);
                    response.request.newBuilder().header("Proxy-Authorization", cred).build();
                };

                builder.proxyAuthenticator(auth);
            }
        }



        c = builder
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();
        this.okHttpClient = c;

        return c;
    }

    private fun parseURL(urlStr: String): HttpUrl {
        val urlObj = urlStr.toHttpUrlOrNull() ?: throw Exception("invalid url $urlStr");
        return urlObj;
    }

    override fun close() {
        val c = this.okHttpClient;
        if (c != null) {
            c.dispatcher.executorService.shutdown()
            c.connectionPool.evictAll()
            this.okHttpClient = null
        }
    }
}

class ApiClientFactory {
    companion object {
        @JvmStatic
        fun getClient(
            serverConfiguration: ServerConfiguration,
            loggerProxy: LoggerProxy = LoggerProxy()
        ): ApiClient {
            val client = ApiClient(serverConfiguration, loggerProxy);
            client.login();

            return client;
        }
    }
}
