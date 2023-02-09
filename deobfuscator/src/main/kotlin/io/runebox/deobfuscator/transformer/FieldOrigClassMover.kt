package io.runebox.deobfuscator.transformer

import io.runebox.asm.tree.ClassPool
import io.runebox.deobfuscator.Transformer
import org.objectweb.asm.Opcodes.GETSTATIC
import org.objectweb.asm.Opcodes.PUTSTATIC
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldInsnNode
import org.tinylog.kotlin.Logger
import java.lang.reflect.Modifier

class FieldOrigClassMover : Transformer {

    private var count = 0

    override fun run(pool: ClassPool) {
        val resolver = FieldOwnerResolver(pool)
        pool.classes.forEach { cls ->
            cls.methods.forEach { method ->
                method.instructions.iterator().forEach { insn ->
                    if(insn is FieldInsnNode) {
                        val opcode = insn.opcode
                        val prevOwner = insn.owner
                        val isStatic = opcode in listOf(GETSTATIC, PUTSTATIC)
                        insn.owner = resolver.getOwner(insn.owner, insn.name, insn.desc, isStatic)
                        val newOwner = insn.owner
                        if(prevOwner != newOwner) {
                            count++
                        }
                    }
                }
            }
        }

        Logger.info("Moved $count fields to original classes.")
    }

    private class FieldOwnerResolver(private val pool: ClassPool) {

        private val classNames = pool.allClasses.associateBy { it.name }

        fun getOwner(owner: String, name: String, desc: String, isStatic: Boolean): String {
            var cls = classNames[owner] ?: return owner
            while(true) {
                if(cls.containsField(name, desc, isStatic)) {
                    return cls.name
                }
                cls = classNames[cls.superName] ?: return cls.superName
            }
        }

        private fun ClassNode.containsField(name: String, desc: String, isStatic: Boolean): Boolean {
            return fields.any { it.name == name && it.desc == desc && Modifier.isStatic(it.access) == isStatic }
        }
    }
}