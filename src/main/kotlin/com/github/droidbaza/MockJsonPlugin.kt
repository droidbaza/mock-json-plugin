package com.github.droidbaza

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.Logger
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.math.RoundingMode
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL

open class MockJsonPlugin : Plugin<Project> {
    val RED = "\u001B[31m"
    val GREEN = "\u001B[32m"
    val RESET = "\u001B[0m"
    private lateinit var configPath: String
    private lateinit var filesPath: String
    private lateinit var baseUrl: String

    private var filesDir: String? = null
    private var reset = true
    private var totalCount = 0
    private var currentStep = 0

    private lateinit var project: Project
    private lateinit var projectDir: String
    private lateinit var logger: Logger

    override fun apply(project: Project) {
        currentStep = 0
        this.project = project
        projectDir = project.projectDir.path
        logger = project.logger
        log("start task loadMocks")
        project.tasks.create("loadMocks") { task ->
            val ext: AutoMockExtension = project.extensions.create(
                "mockJsonConfig",
                AutoMockExtension::class.java
            )
            task.doLast {
                configPath = ext.configPath
                filesPath = ext.filesPath
                baseUrl = ext.baseUrl
                totalCount = ext.requests.size
                log("init configs")
                if (baseUrl.isEmpty() || totalCount == 0) {
                    log("Error config params not found. Task stopped")
                    throw Exception("EXCEPTION CONFIG PARAMETERS NOT FOUND")
                }
                createDirIfNotExist()
                ext.requests.forEach { (name, path) ->
                    callRequest(name, path)
                }
            }
        }.apply {
            group = "autoMocks"
        }
    }

    private fun callRequest(name: String, path: String) {
        val fileName = "response_${name.toLowerCase()}.json"
        val urlPath = "$baseUrl$path"
        try {
            updateProgress()
            val startTime = System.currentTimeMillis()
            val url = URL(urlPath)
            val uc: HttpURLConnection = url.openConnection() as HttpURLConnection
            uc.doOutput = true

            if (uc.responseCode == HttpURLConnection.HTTP_OK) {
                val buff = BufferedReader(InputStreamReader(uc.inputStream))
                var body: String?
                val endTime = (System.currentTimeMillis() - startTime) / 1000.000
                while (buff.readLine().also { body = it } != null) {
                    createJsonFile(fileName, body!!)
                    refreshConfigs(
                        name = name,
                        fileName = fileName,
                        path = path,
                        "$endTime sec"
                    )
                    updateProgress()
                }
                buff.close()

            } else {
                log("ERROR CALL $name $path ${uc.responseCode}", true)
                throw Exception("error call request $path with code ${uc.responseCode}")
            }
        } catch (e: MalformedURLException) {
            log("ERROR CALL $name - $path with exception ${e.message}", true)
            throw Exception("error call request $path with exception ${e.message}")
        } catch (e: IOException) {
            log("ERROR CALL $name - $path with exception ${e.message}", true)
            throw Exception("error call request $path with exception ${e.message}")
        }
    }

    private fun updateProgress() {
        currentStep += 1
        val progress = (currentStep * 100.00) / (totalCount * 2)
        val currentProgress = progress.toBigDecimal().setScale(2, RoundingMode.UP)
        log("$currentProgress % completed")
    }

    fun log(msg: String, isError: Boolean = false) {
        val coloredMsg: String
        val logLevel: LogLevel
        if (isError) {
            logLevel = LogLevel.ERROR
            coloredMsg = "$RED $msg $RESET"
        } else {
            logLevel = LogLevel.LIFECYCLE
            coloredMsg = "$GREEN $msg $RESET"
        }
        logger.log(logLevel, coloredMsg)
    }

    private fun refreshConfigs(name: String, fileName: String, path: String, time: String? = null) {
        val param = "const val ${name.toUpperCase()} = \"$fileName\"\n//endpoint $path request time $time\n"
        try {
            val configName = "MocksConfig"
            val absolutePath = "$projectDir/$configPath/$configName.kt"
            val config = project.file(absolutePath)

            if (!config.exists() || reset) {
                reset = false
                File(absolutePath).also {
                    it.writeText(
                        "/**\n" +
                                " * Automatically generated file. DO NOT MODIFY\n" +
                                " */" +
                                "\nobject $configName {\n//totalCount requests $totalCount\n$param}"
                    )
                }.mkdir()
            } else {
                val oldParams = config
                    .readLines()
                    .toList().joinToString("", "") { "$it\n" }
                    .substringBeforeLast("}")

                config.writeText("$oldParams$param}")
            }
        } catch (e: Exception) {
            log("ERROR UPDATING CONFIG ${e.message}")
            throw Exception("ERROR UPDATING CONFIG ${e.message}")
        }
    }

    private fun createJsonFile(fileName: String, body: String) {
        val absolutePath = "$filesDir/$fileName"
        File(absolutePath).also { it.writeText(body) }.mkdir()
    }

    private fun createDirIfNotExist() {
        val absolutePath = "$projectDir/$filesPath"
        val dir = project.mkdir(absolutePath)
        if (!dir.exists()) {
            File(absolutePath).mkdir()
        }
        filesDir = absolutePath
    }

    open class AutoMockExtension(
        var configPath: String = "src/test/java",
        var filesPath: String = "src/test/resources",
        var baseUrl: String = "",
        var requests: Map<String, String> = mapOf()
    )
}