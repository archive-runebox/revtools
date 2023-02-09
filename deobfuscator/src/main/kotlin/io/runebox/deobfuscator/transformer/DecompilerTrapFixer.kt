package io.runebox.deobfuscator.transformer

import io.runebox.asm.tree.ClassPool
import io.runebox.asm.tree.nextReal
import io.runebox.deobfuscator.Transformer
import org.objectweb.asm.Opcodes.NOP
import org.objectweb.asm.tree.InsnNode
import org.tinylog.kotlin.Logger

class DecompilerTrapFixer : Transformer {

    private var count = 0

    override fun run(pool: ClassPool) {
        pool.classes.forEach { cls ->
            cls.methods.forEach { method ->
                if(method.tryCatchBlocks.any { it.end.nextReal == null }) {
                    method.instructions.add(InsnNode(NOP))
                    count++
                }
            }
        }

        Logger.info("Fixed $count decompiler exception traps.")
    }
}