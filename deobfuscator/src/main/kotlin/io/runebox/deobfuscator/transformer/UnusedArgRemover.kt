package io.runebox.deobfuscator.transformer

import com.google.common.collect.MultimapBuilder
import io.runebox.asm.tree.*
import io.runebox.deobfuscator.Transformer
import io.runebox.deobfuscator.asm.*
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.Type
import org.objectweb.asm.Type.*
import org.objectweb.asm.tree.*
import org.tinylog.kotlin.Logger
import java.lang.reflect.Modifier

class UnusedArgRemover : Transformer {
    private var count = 0

    override fun run(pool: ClassPool) {
        val classNames = pool.allClasses.associateBy { it.name }
        val rootMethods = hashSetOf<String>()
        val opaqueMethodsMap = MultimapBuilder.hashKeys().arrayListValues().build<String, MethodNode>()
        val opaqueMethods = opaqueMethodsMap.asMap()

        pool.classes.forEach { cls ->
            val superClasses = findSupers(cls, classNames)
            cls.methods.forEach { method ->
                if(superClasses.none { it.getMethod(method.name, method.desc) != null }) {
                    rootMethods.add(method.identifier)
                }
            }
        }

        pool.classes.forEach { cls ->
            cls.methods.forEach methodLoop@ { method ->
                val identifier = findOverride(method.owner.name, method.name, method.desc, rootMethods, classNames) ?: return@methodLoop
                opaqueMethodsMap.put(identifier, method)
            }
        }

        val it = opaqueMethods.iterator()
        for((_, method) in it) {
            if(method.any { !it.hasOpaqueArg() }) it.remove()
        }

        pool.classes.forEach { cls ->
            cls.methods.forEach { method ->
                method.instructions.forEach insnLoop@ { insn ->
                    if(insn !is MethodInsnNode) return@insnLoop
                    val identifier = findOverride(insn.owner, insn.name, insn.desc, opaqueMethods.keys, classNames) ?: return@insnLoop
                    if(!insn.previous.isIntConstant()) opaqueMethods.remove(identifier)
                }
            }
        }

        opaqueMethodsMap.values().forEach { method ->
            val oldDesc = method.desc
            val newDesc = oldDesc.dropLastArg()
            method.desc = newDesc
            count++
        }

        pool.classes.flatMap { it.methods }.forEach { method ->
            val insns = method.instructions
            for(insn in insns) {
                if(insn !is MethodInsnNode) continue
                val found = findOverride(insn.owner, insn.name, insn.desc, opaqueMethods.keys, classNames)
                if(found != null) {
                    insn.desc = insn.desc.dropLastArg()
                    val prev = insn.previous
                    insns.remove(prev)
                }
            }
        }

        Logger.info("Removed $count unused method arguments.")
    }

    private val MethodNode.lastArgIndex: Int get() {
        val offset = if(Modifier.isStatic(access)) 1 else 0
        return (Type.getArgumentsAndReturnSizes(desc) shr 2) - offset - 1
    }

    private fun MethodNode.hasOpaqueArg(): Boolean {
        val argTypes = Type.getArgumentTypes(desc)
        if(argTypes.isEmpty()) return false
        val lastArg = argTypes.last()
        if(lastArg != BYTE_TYPE && lastArg != SHORT_TYPE && lastArg != INT_TYPE) return false
        if(Modifier.isAbstract(access)) return true
        instructions.forEach { insn ->
            if(insn !is VarInsnNode) return@forEach
            if(insn.`var` == lastArgIndex) return false
        }
        if(isJdkMethod(owner.name, name, desc)) return false
        return true
    }

    private fun String.dropLastArg(): String {
        val type = Type.getMethodType(this)
        return Type.getMethodDescriptor(type.returnType, *type.argumentTypes.copyOf(type.argumentTypes.size - 1))
    }

    private fun AbstractInsnNode.isIntConstant() = when(opcode) {
        LDC -> (this as LdcInsnNode).cst is Int
        SIPUSH, BIPUSH, ICONST_M1, ICONST_0, ICONST_1, ICONST_2, ICONST_3, ICONST_4, ICONST_5 -> true
        else -> false
    }

    private val AbstractInsnNode.intValue: Int get() {
        if(opcode in 2..8) return opcode - 3
        if(opcode == BIPUSH || opcode == SIPUSH) return (this as IntInsnNode).operand
        if(this is LdcInsnNode && cst is Int) return cst as Int
        throw IllegalArgumentException()
    }

    private fun findSupers(cls: ClassNode, classNames: Map<String, ClassNode>): Collection<ClassNode> {
        return cls.interfaces.plus(cls.superName).mapNotNull { classNames[it] }.flatMap { findSupers(it, classNames).plus(it) }
    }

    private fun findOverride(owner: String, name: String, desc: String, methods: Set<String>, classNames: Map<String, ClassNode>): String? {
        val identifier = "$owner.$name$desc"
        if(identifier in methods) return identifier
        if(name.startsWith("<init>")) return null
        val cls = classNames[owner] ?: return null
        for(superCls in findSupers(cls, classNames)) {
            return findOverride(superCls.name, name, desc, methods, classNames) ?: continue
        }
        return null
    }

    private fun isJdkMethod(owner: String, name: String, desc: String): Boolean {
        try {
            var classes = listOf(Class.forName(Type.getObjectType(owner).className))
            while(classes.isNotEmpty()) {
                classes.forEach { cls ->
                    if(cls.declaredMethods.any { it.name == name && Type.getMethodDescriptor(it) == desc}) return true
                }
                classes = classes.flatMap { mutableListOf<Class<*>?>().apply {
                    addAll(it.interfaces)
                    if(it.superclass != null) add(it.superclass)
                } }
            }
        } catch (e: Exception) {
            /* Do Nothing */
        }
        return false
    }
}