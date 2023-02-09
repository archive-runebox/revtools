package io.runebox.revtools.mapper

import com.google.common.collect.Iterables
import io.runebox.asm.tree.owner
import io.runebox.revtools.mapper.asm.origOwner
import io.runebox.revtools.mapper.config.ProjectConfig
import io.runebox.revtools.mapper.type.ClassEnv
import io.runebox.revtools.mapper.type.ClassEnvironment
import me.tongfei.progressbar.ProgressBar
import me.tongfei.progressbar.ProgressBarBuilder
import me.tongfei.progressbar.ProgressBarStyle
import org.objectweb.asm.Opcodes.INVOKESTATIC
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.tinylog.kotlin.Logger
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream

object MapperLauncher {

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
        //mapper.autoMatchAll(::progress)
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
        /*
         * Check if the deobs had their static nodes consolidated into a single class.
         * Easiest way to do this is check for the resource 'statics.map'.
         */
        if(env.envB.classes.any {it.name == "Statics" }) {
            env.envB.restoreOrigOwners(dstFile)
        }

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

    private fun ClassEnv.restoreOrigOwners(file: File) {
        Logger.info("Restoring original static nodes to their mapped owner classes.")

        val origOwnerMap = hashMapOf<String, String>()
        /*
         * Parse the statics.map resource in the jar files.
         */
        val text = JarFile(file).use { jar ->
            jar.getInputStream(jar.getJarEntry("statics.map")).readAllBytes().toString(Charsets.UTF_8)
        }
        val lines = text.split("\n")
        lines.forEach {
            if(it.isBlank()) return@forEach
            val pair = it.split(":")
            origOwnerMap[pair[0]] = pair[1]
        }

        var methodCount = 0
        var fieldCount = 0
        val movedMethods = hashSetOf<MethodNode>()

        this.classes.forEach { cls ->
            cls.methods.forEach methodLoop@{ method ->
                val origOwner =
                    classes.firstOrNull { it.name == origOwnerMap["${method.owner.name}.${method.name}${method.desc}"] }
                        ?: return@methodLoop
                val exceptions = Iterables.toArray(method.asmNode.exceptions, String::class.java)
                val copy = MethodNode(method.access, method.name, method.desc, method.asmNode.signature, exceptions)
                method.asmNode.accept(copy)
                copy.owner = origOwner.mergedAsmNode
                copy.origOwner = origOwner.mergedAsmNode.name
                movedMethods.add(copy)
                methodCount++
            }
        }
        val movedMethodKeys = movedMethods.map { "${it.name}${it.desc}" }
        classes.forEach { cls ->
            cls.methods.forEach { method ->
                val insns = method.asmNode.instructions
                for(insn in insns) {
                    if(insn is MethodInsnNode && insn.opcode == INVOKESTATIC) {
                        if("${insn.name}${insn.desc}" in movedMethodKeys) {
                            insn.owner = origOwnerMap["${insn.owner}.${insn.name}${insn.desc}"]!!
                        }
                    }
                }
            }
        }

        movedMethods.forEach { method ->
            method.owner.methods.remove(method)
        }

        Logger.info("Restored $methodCount consolidated static methods.")
    }
}