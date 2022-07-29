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
import java.net.InetSocketAddress;
import java.net.Proxy;

public final class ProxyConfiguration implements Serializable {

    static final long serialVersionUID = 1L;
    private static final int MAX_PORT_RANGE = 65525;

    private final String host;
    private final int port;
    private String username;
    private String password;
    private transient Proxy proxy;

    // #region getter/setter

    // #endregion

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
