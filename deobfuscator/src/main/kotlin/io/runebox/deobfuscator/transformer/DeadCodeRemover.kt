package io.runebox.deobfuscator.transformer

import io.runebox.asm.tree.ClassPool
import io.runebox.asm.tree.owner
import io.runebox.deobfuscator.Transformer
import org.objectweb.asm.tree.analysis.Analyzer
import org.objectweb.asm.tree.analysis.BasicInterpreter
import org.tinylog.kotlin.Logger

class DeadCodeRemover : Transformer {

    private var count = 0

    override fun run(pool: ClassPool) {
        pool.classes.forEach { cls ->
            cls.methods.forEach methodLoop@ { method ->
                val insns = method.instructions.toArray()
                val frames = Analyzer(BasicInterpreter()).analyze(method.owner.name, method)
                for(i in insns.indices) {
                    if(frames[i] == null) {
                        method.instructions.remove(insns[i])
                        count++
                    }
                }
            }
        }

        Logger.info("Removed $count dead method instructions.")
    }
}