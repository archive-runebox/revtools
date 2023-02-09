package io.runebox.deobfuscator.transformer

import io.runebox.asm.tree.ClassPool
import io.runebox.asm.tree.isConstructor
import io.runebox.asm.tree.isInitializer
import io.runebox.asm.tree.isStatic
import io.runebox.deobfuscator.Transformer
import org.objectweb.asm.Type
import org.objectweb.asm.tree.LineNumberNode
import org.objectweb.asm.tree.MethodNode
import org.tinylog.kotlin.Logger
import java.lang.reflect.Modifier

class MethodSorter : Transformer {

    private var count = 0

    override fun run(pool: ClassPool) {
        pool.classes.forEach { cls ->
            count += cls.methods.size
            cls.methods = cls.methods.sortedWith(compareBy<MethodNode> { !it.isInitializer() }
                .thenBy { !it.isConstructor() }
                .thenBy { it.isStatic() }
                .thenBy { it.lineNumber }
                .thenBy { Modifier.toString(it.access and Modifier.methodModifiers()) }
                .thenBy { Type.getMethodType(it.desc).returnType.className }
                .thenBy { it.name }
            )
        }

        Logger.info("Reordered $count methods.")
    }

    private companion object {

        private val MethodNode.lineNumber: Int? get() {
            for(insn in instructions) {
                if(insn is LineNumberNode) {
                    return insn.line
                }
            }
            return null
        }
    }
}