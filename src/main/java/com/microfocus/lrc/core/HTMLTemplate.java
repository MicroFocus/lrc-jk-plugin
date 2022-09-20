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

package com.microfocus.lrc.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mitchellbosecke.pebble.PebbleEngine;
import com.mitchellbosecke.pebble.template.PebbleTemplate;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;

public final class HTMLTemplate {
    private HTMLTemplate() {
    }

    public static String generateByPebble(final String template, final JsonObject data) throws IOException {
        PebbleEngine engine = new PebbleEngine.Builder().methodAccessValidator(
                (object, method) -> false
        ).build();
        PebbleTemplate compiledTemplate = engine.getLiteralTemplate(template);

        Map<String, Object> context = new HashMap<>();
        for (String key : data.keySet()) {
            JsonElement val = data.get(key);
            if (val.isJsonPrimitive()) {
                JsonPrimitive prim = val.getAsJsonPrimitive();
                if (prim.isBoolean()) {
                    context.put(key, prim.getAsBoolean());
                } else if (prim.isNumber()) {
                    context.put(key, prim.getAsNumber());
                } else if (prim.isString()) {
                    context.put(key, prim.getAsString());
                }
            } else {
                context.put(key, val.toString());
            }
        }

        Writer writer = new StringWriter();
        compiledTemplate.evaluate(writer, context);

        return writer.toString();
    }
}
