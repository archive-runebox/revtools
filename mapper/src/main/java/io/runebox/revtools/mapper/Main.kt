package io.runebox.revtools.mapper

import io.runebox.revtools.mapper.Mapper.MatchingStatus
import io.runebox.revtools.mapper.bcremap.AsmRemapper
import io.runebox.revtools.mapper.classifier.ClassClassifier
import io.runebox.revtools.mapper.classifier.ClassifierLevel
import io.runebox.revtools.mapper.classifier.ClassifierUtil
import io.runebox.revtools.mapper.classifier.RankResult
import io.runebox.revtools.mapper.config.ProjectConfig
import io.runebox.revtools.mapper.type.ClassEnvironment
import io.runebox.revtools.mapper.type.ClassInstance
import me.tongfei.progressbar.ProgressBar
import me.tongfei.progressbar.ProgressBarBuilder
import me.tongfei.progressbar.ProgressBarStyle
import org.tinylog.kotlin.Logger
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

object Main {

    @JvmStatic
    fun main(args: Array<String>) {
        if(args.size < 3) error("Usage: mapper <src-deob> <dst-deob> <output> [-t, --test] [--export, -e]")

        val srcFile = File(args[0])
        val dstFile = File(args[1])
        val outFile = File(args[2])
        val runTestClient = args.size > 3 && args.drop(2).any { it in listOf("-t", "--test") }
        val exportMappings = args.size > 3 && args.drop(2).any { it in listOf("-e", "--export") }

        this.srcFile = srcFile
        this.dstFile = dstFile
        this.outFile = outFile
        this.runTestClient = runTestClient
        this.exportMappings = exportMappings

        this.init()
        this.run()
        this.save()
    }

    private lateinit var srcFile: File
    private lateinit var dstFile: File
    private lateinit var outFile: File
    private var runTestClient = false
    private var exportMappings = false

    private lateinit var mapper: Mapper
    private lateinit var env: ClassEnvironment

    private lateinit var progressBar: ProgressBar

    private fun init() {
        Logger.info("Initializing mapper.")

        Mapper.init()
        env = ClassEnvironment()
        mapper = Mapper(env)

        /*
         * Create class environment config.
         */
        val config = ProjectConfig(
            mutableListOf<Path>(srcFile.toPath()),
            mutableListOf<Path>(dstFile.toPath()),
            mutableListOf<Path>(),
            mutableListOf<Path>(),
            mutableListOf<Path>(),
            false,
            "",
            "",
            "",
            ""
        )

        Logger.info("Loading classes and info from jar files: ${srcFile.name}, ${dstFile.name}.")

        progressBar = ProgressBarBuilder()
            .setTaskName("Mapping")
            .setInitialMax(100)
            .setUpdateIntervalMillis(1)
            .setStyle(ProgressBarStyle.COLORFUL_UNICODE_BAR)
            .build()

        mapper.init(config, ::progress)

        progressBar.close()
    }

    private fun run() {
        Logger.info("Running mapper.")

        /*
         * Run the automatch all in the mapper.
         */
        mapper.autoMatchAll(::progress)
        Logger.info("Successfully completed automatch.")

        Logger.info("Mapping results are printed below.")
        println("")

        println("Mapping Results:")

        val status = mapper.getStatus(true)
        println("Classes: ${status.matchedClassCount} / ${status.totalClassCount} (${calculateMatchPercent(status.matchedClassCount, status.totalClassCount)})")
        println("Methods: ${status.matchedMethodCount} / ${status.totalMethodCount} (${calculateMatchPercent(status.matchedMethodCount, status.totalMethodCount)})")
        println("Fields: ${status.matchedFieldCount} / ${status.totalFieldCount} (${calculateMatchPercent(status.matchedFieldCount, status.totalFieldCount)})")
        println("Locals: ${status.matchedMethodArgCount} / ${status.totalMethodArgCount} (${calculateMatchPercent(status.matchedMethodArgCount, status.totalMethodArgCount)})")
    }

    private fun save() {
        Logger.info("Saving remapped classes to output jar file: ${outFile.name}.")

        if(outFile.exists()) outFile.deleteRecursively()
        JarOutputStream(FileOutputStream(outFile)).use { jos ->
            env.envB.classes.forEach { cls ->
                val bytes = cls.serialize(NameType.MAPPED_PLAIN)
                jos.putNextEntry(JarEntry("${cls.name.replace(".", "/")}.class"))
                jos.write(bytes)
                jos.closeEntry()
            }
        }

        Logger.info("Successfully saved output jar file.")
    }

    private fun calculateMatchPercent(matchedCount: Int, totalCount: Int): Double {
        return if(totalCount == 0) 0.0 else 100.0 * matchedCount / totalCount
    }

    private fun progress(value: Double) {
        progressBar.stepTo((value * 100.0).toLong())
    }
}