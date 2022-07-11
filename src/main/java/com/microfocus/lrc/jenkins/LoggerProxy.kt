package com.microfocus.lrc.jenkins

import java.io.PrintStream
import java.util.logging.Logger


class LoggerOptions(val isDebugEnabled: Boolean, val moduleName: String)

class LoggerProxy(
    private val logger: PrintStream = System.out,
    private val options: LoggerOptions = LoggerOptions(true, "")
) {
    companion object {
        @JvmStatic
        val sysLogger: Logger = Logger.getLogger(LoggerProxy::class.java.`package`.name);
    }

    private val moduleNameStr: String = if (this.options.isDebugEnabled) {
        this.options.moduleName
    } else {
        ""
    };

    private var lastMsg: String = "";

    private fun buildMsg(level: String, msg: String): String {
        return "[LRC][$level]${this.moduleNameStr} $msg";
    }

    private fun print(msg: String) {
        if (lastMsg == msg) {
            println();
            return;
        }
        lastMsg = msg;
        this.logger.println(msg);
    }

    fun info(message: String) {
        this.print(this.buildMsg("INFO", message));
        sysLogger.log(java.util.logging.Level.INFO, "[LRC][INFO] $message");
    }

    fun error(message: String) {
        this.print(this.buildMsg("ERROR", message));
        sysLogger.log(java.util.logging.Level.INFO, "[LRC][ERROR] $message");
    }

    fun debug(message: String) {
        if (this.options.isDebugEnabled) {
            this.print(this.buildMsg("DEBUG", message));
        }

        sysLogger.log(java.util.logging.Level.INFO, "[LRC][DEBUG] $message");
    }
}