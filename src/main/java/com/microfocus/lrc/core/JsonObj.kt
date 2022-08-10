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

package com.microfocus.lrc.core

import com.google.gson.Gson
import com.google.gson.JsonObject

class JsonObj {

    private val jsonObject: JsonObject

    constructor(jsonStr: String) {
        this.jsonObject = Gson().fromJson(jsonStr, JsonObject::class.java)
    }

    constructor(jsonObject: JsonObject) {
        this.jsonObject = jsonObject
    }

    fun getJsonObject(): JsonObject {
        return this.jsonObject
    }

    fun optInt(key: String, default: Int): Int {
        val value = this.jsonObject.get(key)
        val result = try {
            if (value.isJsonPrimitive) {
                value.asInt
            } else {
                default
            }
        } catch (e: Exception) {
            default
        }

        return result
    }

    fun optDouble(key: String, default: Double): Double {
        val value = this.jsonObject.get(key)
        val result = try {
            if (value.isJsonPrimitive) {
                value.asDouble
            } else {
                default
            }
        } catch (e: Exception) {
            default
        }

        return result
    }

    fun optString(key: String): String? {
        return this.optString(key, null)
    }

    fun optString(key: String, default: String?): String? {
        val value = this.jsonObject.get(key)
        val result = try {
            if (value.isJsonPrimitive) {
                value.asString
            } else {
                default
            }
        } catch (e: Exception) {
            default
        }

        return result
    }
}
