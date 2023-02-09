package io.runebox.deobfuscator.transformer

import io.runebox.asm.tree.ClassPool
import io.runebox.asm.tree.append
import io.runebox.deobfuscator.Transformer
import org.objectweb.asm.Opcodes.POP
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.tinylog.kotlin.Logger

class GetPathFixer : Transformer {

    private var count = 0

    override fun run(pool: ClassPool) {
        var seen = 0
        pool.classes.forEach { cls ->
            cls.methods.forEach methodLoop@ { method ->
                val insns = method.instructions.toArray()
                for(insn in insns) {
                    if(insn !is MethodInsnNode) continue
                    if(insn.name != "getPath") continue
                    if(++seen == 2) {
                        val pop = InsnNode(POP)
                        val ldc = LdcInsnNode("")
                        method.instructions.append(insn, pop, ldc)
                        method.instructions.remove(insn)
                        count++
                        break
                    }
                }
            }
        }

        Logger.info("Fixed $count invalid 'getPath()' method calls.")
    }
}