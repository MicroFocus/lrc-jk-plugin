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

import io.jenkins.plugins.casc.ConfigurationContext;
import io.jenkins.plugins.casc.ConfiguratorRegistry;
import io.jenkins.plugins.casc.misc.ConfiguredWithCode;
import io.jenkins.plugins.casc.misc.JenkinsConfiguredWithCodeRule;
import io.jenkins.plugins.casc.model.CNode;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;

import static io.jenkins.plugins.casc.misc.Util.*;
import static org.junit.Assert.*;

public class ConfigurationAsCodeTest {
    @Rule
    public JenkinsConfiguredWithCodeRule r = new JenkinsConfiguredWithCodeRule();

    @Test
    @ConfiguredWithCode("configuration-as-code.yml")
    public void shouldSupportCACS() throws Exception {
        TestRunBuilder.DescriptorImpl descriptor = new TestRunBuilder.DescriptorImpl();

        assertEquals(USERNAME, descriptor.getUsername());
        assertNotNull(descriptor.getPassword());

        assertEquals(TENANTID, descriptor.getTenantId());
        assertEquals(URL, descriptor.getUrl());

        assertTrue(descriptor.getUseOAuth());
        assertEquals(CLIENT_ID, descriptor.getClientId());
        assertNotNull(descriptor.getClientSecret());

        assertFalse(descriptor.getUseProxy());
        assertEquals(PROXYHOST, descriptor.getProxyHost());
        assertEquals(PROXYPORT, descriptor.getProxyPort());
        assertNull(descriptor.getProxyUsername());
        assertNull(descriptor.getProxyPassword());
    }

    @Test
    @ConfiguredWithCode("configuration-as-code.yml")
    public void shouldSupportCACSExport() throws Exception {
        ConfiguratorRegistry registry = ConfiguratorRegistry.get();
        ConfigurationContext context = new ConfigurationContext(registry);
        CNode yourAttribute = getUnclassifiedRoot(context).get("lrcRunTest");

        JSONObject jsonObject = JSONObject.fromObject(convertToJson(toYamlString(yourAttribute)));

        assertEquals(URL, jsonObject.getString("url"));
        assertEquals(TENANTID, jsonObject.getString("tenantId"));

        assertEquals(USERNAME, jsonObject.getString("username"));
        assertNotNull(jsonObject.getString("password"));

        assertTrue(jsonObject.getBoolean("useOAuth"));
        assertEquals(CLIENT_ID, jsonObject.getString("clientId"));
        assertNotNull(jsonObject.getString("clientSecret"));

        assertFalse(jsonObject.getBoolean("useProxy"));

        assertEquals(PROXYHOST, jsonObject.getString("proxyHost"));
        assertEquals(PROXYPORT, jsonObject.getString("proxyPort"));
        // not existent
        assertThrows(JSONException.class, () -> {
            jsonObject.getString("proxyUsername");
        });
        assertThrows(JSONException.class, () -> {
            jsonObject.getString("proxyPassword");
        });
    }

    private static final String USERNAME = "lrc@microfocus.com";
    private static final String TENANTID = "123456789";
    private static final String URL = "https://loadrunner-cloud.saas.microfocus.com";
    private static final String PROXYHOST = "172.31.128.1";
    private static final String PROXYPORT = "8080";
    private static final String CLIENT_ID = "oauth2-XXXXXXXXXXXXXXXXXXXX@microfocus.com";
}
