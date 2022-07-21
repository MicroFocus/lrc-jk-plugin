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
import java.io.PrintStream
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
            logger: PrintStream
        ): ProxyConfiguration? {
            var proxyConfiguration: ProxyConfiguration? = null
            var serverHost = ""
            try {
                val serverUrlObj = URL(serverUrl)
                serverHost = serverUrlObj.host
            } catch (e: MalformedURLException) {
                logger.println("Failed to parse server URL, you may need to check it again.")
            }
            logger.println("********** Proxy Settings ***********")
            //check JVM properties
            try {
                proxyConfiguration = ProxyConfiguration(
                    System.getProperty("http.proxyHost"),
                    System.getProperty("http.proxyPort"),
                    System.getProperty("http.proxyUser"),
                    System.getProperty("http.proxyPassword")
                )
                logger.println(
                    "Proxy Setting found in JVM System Property: ${proxyConfiguration.proxy.address()}"
                )
            } catch (ex: IllegalArgumentException) {
                //ignore, try next proxy settings
                logger.println("No proxy setting found in JVM System Property.")
            }

            //check Jenkins Global Settings
            val jenkinsProxyConfig = Jenkins.getInstanceOrNull()?.proxy
            if (jenkinsProxyConfig != null) {
                val pickedProxy: Proxy = jenkinsProxyConfig.createProxy(serverHost)
                val isNoProxy = pickedProxy == Proxy.NO_PROXY
                if (isNoProxy) {
                    logger.println("Server host: $serverUrl match the No Proxy setting.")
                } else {
                    try {
                        proxyConfiguration = ProxyConfiguration(
                            jenkinsProxyConfig.name,
                            jenkinsProxyConfig.port,
                            jenkinsProxyConfig.userName,
                            jenkinsProxyConfig.secretPassword.plainText
                        )
                        logger.println(
                            "Proxy Setting found in Jenkins Global Setting: ${proxyConfiguration.proxy.address()}"
                        )
                    } catch (ex: IllegalArgumentException) {
                        //ignore, try next proxy settings
                        logger.println("No proxy setting found in Jenkins Global Setting.")
                    }
                }
            } else {
                logger.println("No proxy setting found in Jenkins Global Setting.")
            }
            if (useProxy != null && useProxy) {
                try {
                    proxyConfiguration = ProxyConfiguration(
                        proxyHost,
                        proxyPort,
                        proxyUsername,
                        proxyPassword
                    )
                    logger.println(
                        "Proxy Setting found in Plugin Setting: ${proxyConfiguration.proxy.address()}"
                    )
                } catch (ex: IllegalArgumentException) {
                    logger.println("No proxy setting found in Plugin Setting.")
                }
            } else {
                logger.println("No proxy setting found in Plugin Setting.")
            }
            if (proxyConfiguration == null) {
                logger.println("Will connect to server directly.")
            } else {
                logger.println("Will connect to server via: ${proxyConfiguration.proxy.address()}")
            }
            logger.println("*************************************")
            return proxyConfiguration
        }
    }
}
