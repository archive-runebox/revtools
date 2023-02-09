package io.runebox.deobfuscator.transformer

import io.runebox.asm.tree.ClassPool
import io.runebox.deobfuscator.Transformer
import org.objectweb.asm.tree.TryCatchBlockNode
import org.tinylog.kotlin.Logger

class RuntimeExceptionRemover : Transformer {

    private var count = 0

    override fun run(pool: ClassPool) {
        pool.classes.forEach { cls ->
            cls.methods.forEach { method ->
                val toRemove = mutableListOf<TryCatchBlockNode>()
                method.tryCatchBlocks.forEach { tcb ->
                    if(tcb.type == "java/lang/RuntimeException") {
                        toRemove.add(tcb)
                    }
                }
                method.tryCatchBlocks.removeAll(toRemove)
                count += toRemove.size
            }
        }

        Logger.info("Removed $count 'RuntimeException' try-catch blocks.")
    }
}