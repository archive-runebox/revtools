package io.runebox.deobfuscator.transformer

import io.runebox.asm.tree.ClassPool
import io.runebox.asm.tree.isConstructor
import io.runebox.asm.util.InsnMatcher
import io.runebox.deobfuscator.Transformer
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.tinylog.kotlin.Logger

class ConstructorErrorRemover : Transformer {

    private var count = 0

    override fun run(pool: ClassPool) {
        pool.classes.forEach { cls ->
            val toRemove = mutableListOf<MethodNode>()
            cls.methods.forEach methodLoop@ { method ->
                if(method.isConstructor()) {
                    for(match in PATTERN.match(method).filter { checkPattern(it) }) {
                        toRemove.add(method)
                        count++
                        return@methodLoop
                    }
                }
            }
            cls.methods.removeAll(toRemove)
        }

        Logger.info("Removed $count constructor 'Error' traps.")
    }


    private fun checkPattern(insns: List<AbstractInsnNode>): Boolean {
        val invoke = insns[2] as MethodInsnNode
        if(invoke.owner != "java/lang/Error" || invoke.name != "<init>" || invoke.desc != "()V") return false
        return true
    }

    companion object {
        private val PATTERN = InsnMatcher.compile(
            """
                (NEW)
                (DUP)
                (INVOKESPECIAL)
                (ATHROW)
            """.trimIndent()
        )
    }
}