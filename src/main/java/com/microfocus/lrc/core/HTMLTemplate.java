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
        PebbleEngine engine = new PebbleEngine.Builder().build();
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
