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

package com.microfocus.lrc.jenkins

import hudson.EnvVars
import hudson.Launcher
import hudson.model.ParametersAction
import hudson.model.Run
import hudson.model.StringParameterValue
import java.util.logging.Level

class EnvVarsUtil {
    companion object{
        @JvmStatic
        fun getEnvVar(build: Run<*, *>, launcher: Launcher, key: String): String? {
            // getAction(Class<T> type) is not deprecated
            @SuppressWarnings("kotlin:S1874")
            val action = build.getAction(ParametersAction::class.java)
            if (action != null) {
                val paramValue = action.getParameter(key) as StringParameterValue?
                return if (paramValue?.getValue() != null) {
                    paramValue.getValue().toString()
                } else ""
            }

            //or try the system env var
            return try {
                EnvVars.getRemote(launcher.channel)[key]
            } catch (e: Exception) {
                LoggerProxy.sysLogger.log(Level.WARNING, "failed to get env, " + e.message)
                null
            }
        }
    }
}
