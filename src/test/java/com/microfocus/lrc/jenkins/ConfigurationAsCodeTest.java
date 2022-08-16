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

import io.jenkins.plugins.casc.misc.ConfiguredWithCode;
import io.jenkins.plugins.casc.misc.JenkinsConfiguredWithCodeRule;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.*;

public class ConfigurationAsCodeTest {
    @Rule
    public JenkinsConfiguredWithCodeRule r = new JenkinsConfiguredWithCodeRule();

    @Test
    @ConfiguredWithCode("configuration-as-code.yml")
    public void should_support_configuration_as_code() throws Exception {
        TestRunBuilder.DescriptorImpl descriptor = new TestRunBuilder.DescriptorImpl();

        assertEquals("user name", "lrc@microfocus.com", descriptor.getUsername());
        assertNotNull(descriptor.getPassword());

        assertEquals("tenant id", "336275312", descriptor.getTenantId());
        assertEquals("url", "https://loadrunner-cloud.saas.microfocus.com", descriptor.getUrl());

        assertTrue("useOAuth", descriptor.getUseOAuth());
        assertEquals("oauth2-EHHvbygKQtsorITXg5DZ@microfocus.com", descriptor.getClientId());
        assertNotNull(descriptor.getClientSecret());

        assertFalse("useProxy", descriptor.getUseProxy());
        assertEquals("proxyHost", "172.31.128.1", descriptor.getProxyHost());
        assertEquals("proxyPort", "8080", descriptor.getProxyPort());
        assertNull(descriptor.getProxyUsername());
        assertNull(descriptor.getProxyPassword());
    }
}
