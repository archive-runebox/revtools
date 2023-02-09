package io.runebox.deobfuscator.transformer

import com.google.common.collect.Iterables
import io.runebox.asm.tree.*
import io.runebox.deobfuscator.Deobfuscator.encodeOrigOwnerMap
import io.runebox.deobfuscator.Transformer
import io.runebox.deobfuscator.asm.origOwner
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.tinylog.kotlin.Logger
import java.lang.reflect.Modifier

class StaticNodeConsolidator : Transformer {

    private var methodCount = 0
    private var fieldCount = 0

    private lateinit var staticsCls: ClassNode

    override fun run(pool: ClassPool) {
        staticsCls = ClassNode()
        staticsCls.visit(50, ACC_PUBLIC or ACC_FINAL or ACC_SUPER, "Statics", null, "java/lang/Object", arrayOf())
        pool.addClass(staticsCls)

        this.moveStaticMethods(pool)
        this.moveStaticFields(pool)
        pool.encodeOrigOwnerMap()
    }

    private fun moveStaticMethods(pool: ClassPool) {
        val movedMethods = hashSetOf<MethodNode>()
        val staticMethods = pool.classes.flatMap { it.methods }.filter { Modifier.isStatic(it.access) && !it.isInitializer() }.toList()

        staticMethods.forEach { method ->
            val exceptions = Iterables.toArray(method.exceptions, String::class.java)
            val copy = MethodNode(method.access, method.name, method.desc, method.signature, exceptions)
            method.accept(copy)
            copy.origOwner = method.owner.name
            staticsCls.methods.add(copy)
            movedMethods.add(method)
            methodCount++
        }

        val movedMethodKeys = movedMethods.map { it.key() }
        pool.classes.forEach { cls ->
            cls.methods.forEach { method ->
                val insns = method.instructions
                for(insn in insns) {
                    if(insn is MethodInsnNode && insn.opcode == INVOKESTATIC) {
                        if(insn.owner == "Statics") continue
                        if("${insn.name}${insn.desc}" in movedMethodKeys) {
                            insn.owner = "Statics"
                        }
                    }
                }
            }
        }

        movedMethods.forEach { method ->
            method.owner.methods.remove(method)
        }

        Logger.info("Copied $methodCount static methods to statics class.")
    }

    private fun moveStaticFields(pool: ClassPool) {
        val movedFields = hashSetOf<FieldNode>()
        val staticFields = pool.classes.flatMap { it.fields }.filter { Modifier.isStatic(it.access) }.toList()

        staticFields.forEach { field ->
            val copy = FieldNode(ASM9, field.access, field.name, field.desc, null, field.value)
            copy.origOwner = field.owner.name
            staticsCls.fields.add(copy)
            movedFields.add(field)
            fieldCount++
        }

        val movedFieldKeys = movedFields.map { it.key() }
        pool.classes.forEach { cls ->
            cls.methods.forEach { method ->
                val insns = method.instructions
                for(insn in insns) {
                    if(insn is FieldInsnNode && (insn.opcode == GETSTATIC || insn.opcode == PUTSTATIC)) {
                        if(insn.owner == "Statics") continue
                        if("${insn.name}${insn.desc}" in movedFieldKeys) {
                            insn.owner = "Statics"
                        }
                    }
                }
            }
        }

        movedFields.forEach { field ->
            field.owner.fields.remove(field)
        }

        Logger.info("Moved $fieldCount static fields to statics class.")
    }

    private fun MethodNode.key() = "$name$desc"
    private fun FieldNode.key() = "$name$desc"
}