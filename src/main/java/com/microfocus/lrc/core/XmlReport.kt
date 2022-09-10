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

import com.microfocus.lrc.core.entity.LoadTestRun
import com.microfocus.lrc.core.entity.TestRunStatus
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.StringWriter
import java.nio.charset.StandardCharsets
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

class XmlReport {
    companion object {
        @Throws(Exception::class)
        fun write(
            testRun: LoadTestRun,
            reportUrl: String,
            dashboardUrl: String,
        ): ByteArray {

            val xml =
                DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument()
            val isFailure: Boolean = TestRunStatus.PASSED.statusName != testRun.detailedStatus

            val testsuite = xml.createElement("testsuite")
            testsuite.setAttribute("name", testRun.loadTest.name)
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
                testRun.loadTest.id.toString(),
                xml,
                false
            )
            properties.appendChild(pTestId)
            val pRunId: Element = generatePropertyElement(
                "runId",
                testRun.id.toString(),
                xml,
                false
            )
            properties.appendChild(pRunId)
            val pStatusDesc: Element = generatePropertyElement(
                "statusDescription",
                testRun.status,
                xml,
                true
            )
            properties.appendChild(pStatusDesc)
            if (testRun.testRunCompletelyEnded()) {
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
            if (testRun.startTime != -1L && testRun.endTime != -1L && testRun.endTime > testRun.startTime) {
                time = (testRun.endTime - testRun.startTime) / 1000.0
            }
            val testcase = xml.createElement("testcase")
            testcase.setAttribute("name", testRun.loadTest.name)
            testcase.setAttribute("status", testRun.detailedStatus)
            testcase.setAttribute("classname", "com.microfocus.lrc.Test")
            testcase.setAttribute("time", String.format("%.2f", time))
            testsuite.appendChild(testcase)

            if (isFailure) {
                val failureEle = xml.createElement("failure")
                failureEle.setAttribute("message", "Test run status is ${testRun.detailedStatus}")
                failureEle.setAttribute("type", testRun.detailedStatus)
                testcase.appendChild(failureEle)
            }

            val source = DOMSource(xml)

            val trans2Str = TransformerFactory.newInstance().newTransformer()
            trans2Str.setOutputProperty(OutputKeys.ENCODING, "UTF-8")
            trans2Str.setOutputProperty(OutputKeys.INDENT, "yes")

            val sw = StringWriter()
            trans2Str.transform(source, StreamResult(sw))

            return sw.buffer.toString().toByteArray(StandardCharsets.UTF_8)
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
