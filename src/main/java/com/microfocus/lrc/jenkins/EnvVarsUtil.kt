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