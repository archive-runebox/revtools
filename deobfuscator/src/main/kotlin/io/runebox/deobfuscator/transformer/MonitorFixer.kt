package io.runebox.deobfuscator.transformer

import io.runebox.asm.tree.ClassPool
import io.runebox.asm.tree.nextReal
import io.runebox.asm.tree.previousReal
import io.runebox.asm.util.InsnMatcher
import io.runebox.deobfuscator.Transformer
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.tree.*
import org.tinylog.kotlin.Logger

class MonitorFixer : Transformer {

    private var count = 0

    override fun run(pool: ClassPool) {
        pool.classes.forEach { cls ->
            cls.methods.forEach { method ->
                val insns = method.instructions
                for(insn in insns) {
                    if(insn.opcode == MONITORENTER || insn.opcode == MONITOREXIT) {
                        val prev = insn.previous ?: continue
                        if(prev.opcode == GETSTATIC || prev.opcode == ALOAD) {
                            method.instructions.remove(insn.previous)
                            method.instructions.remove(insn)
                            count++
                        }
                    }
                }
            }
        }

        Logger.info("Fixed $count monitor state scopes.")
    }
}