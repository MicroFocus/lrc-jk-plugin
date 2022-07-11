package com.microfocus.lrc.core

import com.google.gson.JsonObject
import com.microfocus.lrc.core.entity.TestRunStatus
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.StringWriter
import java.nio.charset.StandardCharsets
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

class XmlReport {
    companion object {
        @Throws(Exception::class)
        fun write(
            testId: Int,
            testRunId: Int,
            testName: String,
            uiStatus: String,
            statusDesc: String?,
            beginTime: Int,
            endTime: Int,
            isResultAvailable: Boolean,
            reportUrl: String,
            dashboardUrl: String,
            errorObj: JsonObject?,
            statusCode: Int
        ): ByteArray {

            val xml =
                DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
            val isFailure: Boolean = TestRunStatus.PASSED.statusName != uiStatus;

            val testsuite = xml.createElement("testsuite")
            testsuite.setAttribute("name", testName)
            testsuite.setAttribute("tests", "1")
            testsuite.setAttribute("failures", if (isFailure) "1" else "0")
            xml.appendChild(testsuite)

            val properties = xml.createElement("properties")

            val pStormRunnerLoad: Element = generatePropertyElement(
                "generator",
                "LoadRunner Cloud",
                xml,
                false
            )
            properties.appendChild(pStormRunnerLoad)
            val pTestId: Element = generatePropertyElement(
                "testId",
                testId.toString(),
                xml,
                false
            )
            properties.appendChild(pTestId)
            val pRunId: Element = generatePropertyElement(
                "runId",
                testRunId.toString(),
                xml,
                false
            )
            properties.appendChild(pRunId)
            if (statusDesc != null) {
                val pStatusDesc: Element = generatePropertyElement(
                    "statusDescription",
                    statusDesc,
                    xml,
                    true
                )
                properties.appendChild(pStatusDesc)
            }
            if (isResultAvailable) {
                val pReport: Element = generatePropertyElement(
                    "reportUrl",
                    reportUrl,
                    xml,
                    false
                )
                val pDashBoard: Element = generatePropertyElement(
                    "dashboardUrl",
                    dashboardUrl,
                    xml,
                    false
                )
                properties.appendChild(pReport)
                properties.appendChild(pDashBoard)
            }
            testsuite.appendChild(properties)

            var time = 0.0
            if (beginTime != -1 && endTime != -1 && endTime > beginTime) {
                time = (endTime - beginTime) / 1000.0
            }
            val testcase = xml.createElement("testcase")
            testcase.setAttribute("name", testName)
            testcase.setAttribute("status", uiStatus)
            testcase.setAttribute("classname", "com.microfocus.lrc.Test")
            testcase.setAttribute("time", time.toString())
            testsuite.appendChild(testcase)

            if (isFailure) {
                var failureContent = "${statusCode} "
                if (errorObj != null) {
                    failureContent += "$errorObj "
                }
                if (!statusDesc.isNullOrBlank() && uiStatus != TestRunStatus.FAILED.statusName) {
                    failureContent += "$statusDesc"
                }
                val failureEle = xml.createElement("failure")
                failureEle.setAttribute("message", "Test run status is $uiStatus")
                failureEle.textContent = failureContent
                failureEle.setAttribute("type", uiStatus)
                testcase.appendChild(failureEle)
            }

            val source = DOMSource(xml)

            val trans2Str =
                TransformerFactory.newInstance().newTransformer()
            val sw = StringWriter()
            trans2Str.transform(source, StreamResult(sw))

            return sw.buffer.toString().toByteArray(StandardCharsets.UTF_8);
        }

        private fun generatePropertyElement(
            name: String,
            value: String,
            xml: Document,
            valueInContent: Boolean
        ): Element {
            val pReport = xml.createElement("property")
            pReport.setAttribute("name", name)
            if (valueInContent) {
                pReport.textContent = value
            } else {
                pReport.setAttribute("value", value)
            }
            return pReport
        }
    }
}