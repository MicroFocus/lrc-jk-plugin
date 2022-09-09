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

import org.junit.Test;

import static org.junit.Assert.*;

public class UtilsTest {

    @Test
    public void maskString() {
        assertEquals("no change on string <= 6", "abcdef", Utils.maskString("abcdef"));
        assertEquals("string > 6", "abc**def", Utils.maskString("abc12def"));
        assertEquals("email address", "abc************com", Utils.maskString("abc@microfocus.com"));
    }
}
