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

package com.microfocus.lrc.core.entity;

import java.io.Serializable;

public final class ServerConfiguration implements Serializable {
    private final String url;
    private final String username;
    private final String password;
    private final String tenantId;
    private final int projectId;
    private ProxyConfiguration proxyConfiguration;
    private final boolean sendEmail;

    // #region getter/setter
    public String getUrl() {
        return url;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getTenantId() {
        return tenantId;
    }

    public ProxyConfiguration getProxyConfiguration() {
        return proxyConfiguration;
    }

    public int getProjectId() {
        return projectId;
    }

    public boolean isSendEmail() {
        return sendEmail;
    }
    // #endregion

    /**
     * constructor.
     * @param url
     * @param username
     * @param password
     * @param tenantId
     * @param projectId
     * @param sendEmail
     */
    public ServerConfiguration(final String url, final String username, final String password, final String tenantId,
            final int projectId, final boolean sendEmail) {
        this.url = url;
        this.username = username;
        this.password = password;
        this.tenantId = tenantId;
        this.projectId = projectId;
        this.sendEmail = sendEmail;
    }

    public void setProxyConfiguration(final ProxyConfiguration proxyConfiguration) {
        this.proxyConfiguration = proxyConfiguration;
    }

}
