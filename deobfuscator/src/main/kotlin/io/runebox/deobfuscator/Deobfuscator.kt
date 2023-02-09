package io.runebox.deobfuscator

import io.runebox.asm.tree.ClassPool
import io.runebox.asm.tree.identifier
import io.runebox.asm.tree.ignored
import io.runebox.deobfuscator.asm.*
import io.runebox.deobfuscator.transformer.*
import org.tinylog.kotlin.Logger
import java.io.File
import java.util.jar.JarEntry
import kotlin.reflect.full.createInstance

object Deobfuscator {

   private lateinit var inputFile: File
   private lateinit var outputFile: File
   private var runTestClient = false

   private val pool = ClassPool()
   private val transformers = mutableListOf<Transformer>()

   private lateinit var obfNameMapBytes: ByteArray
   private lateinit var nameIdxMapBytes: ByteArray

   /**
    * == BYTECODE TRANSFORMER REGISTRATION ==
    *
    * **!NOTE!** The order of the transformers matter and have
    * to carefully thought about.
    *
    * =========================================
    */
   private fun initTransformers() {
      register<RuntimeExceptionRemover>()
      register<DeadCodeRemover>()
      register<ControlFlowOptimizer>()
      register<ExceptionRangeOptimizer>()
      register<Renamer>()
      register<IllegalStateExceptionRemover>()
      register<UnusedArgRemover>()
      register<GotoOptimizer>()
      register<ConstructorErrorRemover>()
      register<GetPathFixer>()
      register<FieldOrigClassMover>()
      register<UnusedFieldRemover>()
      register<FieldSorter>()
      register<StaticMethodOrigClassMover>()
      register<UnusedMethodRemover>()
      register<MethodSorter>()
      register<ExprOrderNormalizer>()
      register<MultiplierRemover>()
      register<StackFrameFixer>()
      register<DecompilerTrapFixer>()
      register<StaticNodeConsolidator>()
   }

   @JvmStatic
   fun main(args: Array<String>) {
      if(args.size < 2) error("Usage: deobfuscator <input-jar> <output-jar> [-t]")

      val inputFile = File(args[0])
      val outputFile = File(args[1])
      val runTestClient = args.size == 3 && args[2] == "-t"

      this.inputFile = inputFile
      this.outputFile = outputFile
      this.runTestClient = runTestClient

      this.init()
      this.run()
      this.save()

      if(runTestClient) {
         this.test()
      }
   }

   private fun init() {
      Logger.info("Initializing deobfuscator.")

      /*
       * Load classes from jar into pool
       */
      Logger.info("Loading classes from jar file: ${inputFile.name}.")
      pool.addJar(inputFile)
      pool.classes.forEach { cls ->
         if(cls.name.contains("bouncycastle") || cls.name.contains("json")) {
            cls.ignored = true
         }
      }
      pool.sourceFile = inputFile
      pool.loadHierarchy()
      pool.build()
      Logger.info("Loaded ${pool.classes.size} from jar file.")

      /*
       * Register Bytecode transformers.
       */
      this.initTransformers()
   }

   private fun run() {
      Logger.info("Running deobfuscator.")

      val taskStart = System.currentTimeMillis()
      /*
       * Run all the of the registered bytecode transformers
       */
      transformers.forEach { transformer ->
         Logger.info("Running transformer: ${transformer::class.simpleName}.")
         transformer.run(pool)
      }
      val taskDelta = System.currentTimeMillis() - taskStart

      Logger.info("Completed all bytecode transformers in ${(taskDelta / 1000L)} seconds.")
   }

   private fun save() {
      Logger.info("Saving deobfuscated classes to jar file: ${outputFile.name}.")

      /*
       * Save all the classes in the pool to the jar file.
       */
      pool.writeJar(outputFile) { jos ->
         jos.putNextEntry(JarEntry("obf.map"))
         jos.write(obfNameMapBytes)
         jos.closeEntry()
      }

      Logger.info("Successfully saved ${pool.allClasses.size} to jar file.")
   }

   private fun test() {
      Logger.info("Starting test client using jar file: ${outputFile.name}.")
      TestClient(outputFile, inputFile).run()
   }

   @TransformerMarker
   private inline fun <reified T : Transformer> register() {
      transformers.add(T::class.createInstance())
   }

   internal fun ClassPool.encodeNameIndexMap() {
      val str = StringBuilder()
      nameIndexMap.forEach { (index, name) ->
         str.append("$index:$name\n")
      }
      nameIdxMapBytes = str.toString().toByteArray()
   }

   internal fun ClassPool.encodeObfNameMap() {
      val str = StringBuilder()
      allClasses.forEach { cls ->
         str.append("${cls.identifier}:${cls.obfName}\n")
         cls.methods.forEach { method ->
            str.append("${method.identifier}:${method.obfName}\n")
         }
         cls.fields.forEach { field ->
            str.append("${field.identifier}:${field.obfName}\n")
         }
      }
      obfNameMapBytes = str.toString().toByteArray()
   }

   @DslMarker
   private annotation class TransformerMarker
}