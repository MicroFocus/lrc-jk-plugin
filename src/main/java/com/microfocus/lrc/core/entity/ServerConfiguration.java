/*
 * #© Copyright 2019 - Micro Focus or one of its affiliates
 * #
 * # The only warranties for products and services of Micro Focus and its affiliates and licensors (“Micro Focus”)
 * # are as may be set forth in the express warranty statements accompanying such products and services.
 * # Nothing herein should be construed as constituting an additional warranty.
 * # Micro Focus shall not be liable for technical or editorial errors or omissions contained herein.
 * # The information contained herein is subject to change without notice.
 *
 */
package com.microfocus.lrc.core.entity;

import java.io.Serializable;

public final class ServerConfiguration implements Serializable {

    private String url;
    private String username;
    private String password;
    private String tenantId;
    private int projectId;
    private ProxyConfiguration proxyConfiguration;
    private boolean sendEmail;
    private String initiator;

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

    public String getInitiator() {
        return initiator;
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
     * @param initiator
     */
    public ServerConfiguration(final String url, final String username, final String password, final String tenantId,
            final int projectId, final boolean sendEmail, final String initiator) {
        this.url = url;
        this.username = username;
        this.password = password;
        this.tenantId = tenantId;
        this.projectId = projectId;
        this.sendEmail = sendEmail;
        this.initiator = initiator;
    }

    public void setProxyConfiguration(final ProxyConfiguration proxyConfiguration) {
        this.proxyConfiguration = proxyConfiguration;
    }

}
