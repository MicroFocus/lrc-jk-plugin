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

package com.microfocus.lrc.jenkins

import com.microfocus.lrc.core.entity.ProxyConfiguration
import jenkins.model.Jenkins
import java.net.MalformedURLException
import java.net.Proxy
import java.net.URL

class ProxyConfigurationFactory {
    companion object {
        @JvmStatic
        fun createProxyConfiguration(
            serverUrl: String,
            useProxy: Boolean?,
            proxyHost: String?,
            proxyPort: String?,
            proxyUsername: String?,
            proxyPassword: String?,
            loggerProxy: LoggerProxy
        ): ProxyConfiguration? {
            var proxyConfiguration: ProxyConfiguration? = null
            var serverHost = ""
            try {
                val serverUrlObj = URL(serverUrl)
                serverHost = serverUrlObj.host
            } catch (e: MalformedURLException) {
                loggerProxy.info("Failed to parse server URL, you may need to check it again.")
            }
            loggerProxy.info("********** Proxy Settings ***********")
            //check JVM properties
            try {
                proxyConfiguration = ProxyConfiguration(
                    System.getProperty("http.proxyHost"),
                    System.getProperty("http.proxyPort"),
                    System.getProperty("http.proxyUser"),
                    System.getProperty("http.proxyPassword")
                )
                loggerProxy.info(
                    "Proxy Setting found in JVM System Property: ${proxyConfiguration.proxy.address()}"
                )
            } catch (ex: IllegalArgumentException) {
                //ignore, try next proxy settings
                loggerProxy.info("No proxy setting found in JVM System Property.")
            }

            //check Jenkins Global Settings
            val jenkinsProxy = readProxyFromJenkins(serverHost);
            if (jenkinsProxy != null) {
                loggerProxy.info("Proxy Setting found in Jenkins Global Settings: ${jenkinsProxy.proxy.address()}")
                proxyConfiguration = jenkinsProxy;
            } else {
                loggerProxy.info("No proxy setting found in Jenkins Global Settings.")
            }

            if (useProxy != null && useProxy) {
                try {
                    proxyConfiguration = ProxyConfiguration(
                        proxyHost,
                        proxyPort,
                        proxyUsername,
                        proxyPassword
                    )
                    loggerProxy.info(
                        "Proxy Setting found in Plugin Setting: ${proxyConfiguration.proxy.address()}"
                    )
                } catch (ex: IllegalArgumentException) {
                    loggerProxy.info("No proxy setting found in Plugin Setting.")
                }
            } else {
                loggerProxy.info("No proxy setting found in Plugin Setting.")
            }

            if (proxyConfiguration == null) {
                loggerProxy.info("Will connect to server directly.")
            } else {
                loggerProxy.info("Will connect to server via: ${proxyConfiguration.proxy.address()}")
            }

            loggerProxy.info("*************************************")
            return proxyConfiguration
        }

        private fun readProxyFromJenkins(serverHost: String): ProxyConfiguration? {
            val jenkinsProxyConfig = Jenkins.getInstanceOrNull()?.proxy ?: return null

            val pickedProxy: Proxy = jenkinsProxyConfig.createProxy(serverHost);
            if (pickedProxy.type() == Proxy.Type.DIRECT) {
                return null
            }

            return try {
                ProxyConfiguration(
                    jenkinsProxyConfig.name,
                    jenkinsProxyConfig.port,
                    jenkinsProxyConfig.userName,
                    jenkinsProxyConfig.secretPassword.plainText
                );
            } catch (_: IllegalArgumentException) {
                null;
            }
        }
    }
}
