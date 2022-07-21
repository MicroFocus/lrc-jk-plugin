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
import java.net.InetSocketAddress;
import java.net.Proxy;

public final class ProxyConfiguration implements Serializable {
    private static final int MAX_PORT_RANGE = 65525;

    private final String host;
    private final int port;
    private String username;
    private String password;
    private transient Proxy proxy;

    // #region getter/setter

    // #endregion

    /**
     * constructor.
     *
     * @param host
     * @param port
     * @param username
     * @param password
     */
    public ProxyConfiguration(final String host, final int port, final String username, final String password) {
        if (host == null || host.length() == 0) {
            throw new IllegalArgumentException("host must not be empty.");
        }
        if (port <= 0 || port > MAX_PORT_RANGE) {
            throw new IllegalArgumentException("port must be an integer between 0 to 65525");
        }

        this.host = host;
        this.port = port;

        if (username != null && password != null && username.length() > 0 && password.length() > 0) {
            this.username = username;
            this.password = password;
        }
    }

    /**
     * constructor.
     *
     * @param host
     * @param port
     * @param username
     * @param password
     * @throws IllegalArgumentException
     */
    public ProxyConfiguration(final String host, final String port, final String username, final String password)
            throws IllegalArgumentException {
        if (host == null || host.length() == 0) {
            throw new IllegalArgumentException("host must not be empty.");
        }
        int portNum;
        try {
            portNum = Integer.parseInt(port);
        } catch (NumberFormatException ex) {
            portNum = -1;
        }
        if (portNum <= 0 || portNum > MAX_PORT_RANGE) {
            throw new IllegalArgumentException("port must be an integer between 0 to 65525");
        }

        this.host = host;
        this.port = portNum;

        if (username != null && password != null && username.length() > 0 && password.length() > 0) {
            this.username = username;
            this.password = password;
        }
    }

    /**
     * get proxy, if null, construct a new one.
     *
     * @return proxy
     */
    public Proxy getProxy() {
        if (this.proxy != null) {
            return this.proxy;
        }

        InetSocketAddress sktAddr = new InetSocketAddress(this.host, this.port);
        this.proxy = new Proxy(Proxy.Type.HTTP, sktAddr);
        return proxy;
    }

    public String getHost() {
        return this.host;
    }

    public int getPort() {
        return this.port;
    }

    public String getUsername() {
        return this.username;
    }

    public String getPassword() {
        return this.password;
    }
}
