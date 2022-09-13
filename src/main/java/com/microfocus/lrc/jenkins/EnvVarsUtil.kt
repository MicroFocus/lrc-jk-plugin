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
import hudson.model.ParameterValue
import hudson.model.BooleanParameterValue
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

            return try {
                if (action != null) {
                    val param = action.getParameter(/* name = */ key)
                    if (param != null) {
                        var paramValue = ""
                        if (param is StringParameterValue) {
                            paramValue = param.getValue().toString()
                        } else if (param is BooleanParameterValue) {
                            paramValue = param.getValue().toString()
                        }

                        return paramValue
                    }
                 }

                //or try the system env var
                EnvVars.getRemote(launcher.channel)[key]
            } catch (e: Exception) {
                LoggerProxy.sysLogger.log(Level.WARNING, "Failed to get parameters / env variables, " + e.message)
                null
            }
        }

        @JvmStatic
        fun putEnvVar(build: Run<*, *>, key: String?, value: String?) {
            val paramList: MutableList<ParameterValue> = mutableListOf(StringParameterValue(key, value))
            val pAction = ParametersAction(paramList, arrayListOf("LRC_RUN_ID"))
            val existed = build.getAction(ParametersAction::class.java)
            if (existed != null) {
                build.addOrReplaceAction(existed.merge(pAction))
            } else {
                build.addOrReplaceAction(pAction)
            }
        }
    }
}
