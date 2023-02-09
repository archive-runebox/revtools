package io.runebox.deobfuscator.transformer

import io.runebox.asm.tree.ClassPool
import io.runebox.asm.tree.ignored
import io.runebox.deobfuscator.Transformer
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.util.CheckClassAdapter
import org.tinylog.kotlin.Logger

class StackFrameFixer : Transformer {

    override fun run(pool: ClassPool) {
        val newNodes = mutableListOf<ClassNode>()
        pool.allClasses.forEach { cls ->
            val newNode = ClassNode()
            val writer = Writer(pool)
            cls.accept(writer)

            //checkDataFlow(cls.name, writer.toByteArray())

            val reader = ClassReader(writer.toByteArray())
            reader.accept(newNode, ClassReader.SKIP_FRAMES)
            newNode.ignored = cls.ignored
            newNodes.add(newNode)
        }

        pool.clear()
        newNodes.forEach { pool.addClass(it) }

        Logger.info("Fixed method stack frames for ${pool.allClasses.size} classes.")
    }

    private fun checkDataFlow(className: String, data: ByteArray) {
        try {
            val reader = ClassReader(data)
            val writer = ClassWriter(reader, ClassWriter.COMPUTE_MAXS)
            val checker = CheckClassAdapter(writer, true)
            reader.accept(checker, 0)
        } catch(e: Exception) {
            Logger.warn(e, "Class $className data-flow validation failed.")
        }
    }

    private class Writer(private val pool: ClassPool) : ClassWriter(COMPUTE_FRAMES or COMPUTE_MAXS) {

        companion object {
            val OBJECT_INTERNAL_NAME = Type.getInternalName(Any::class.java)
        }

        private val classNames = pool.allClasses.associate { it.name to it }

        override fun getCommonSuperClass(type1: String, type2: String): String {
            if(isAssignable(type1, type2)) return type2
            if(isAssignable(type2, type1)) return type1
            var t1 = type1
            do {
                t1 = checkNotNull(superClassName(t1, classNames))
            } while(!isAssignable(type2, t1))
            return t1
        }

        private fun isAssignable(from: String, to: String): Boolean {
            if(from == to) return true
            val superClass = superClassName(from, classNames) ?: return false
            if(isAssignable(superClass, to)) return true
            return interfaceNames(from).any { isAssignable(it, to) }
        }

        private fun interfaceNames(type: String): List<String> {
            return if(type in classNames) {
                classNames.getValue(type).interfaces
            } else {
                Class.forName(type.replace('/', '.')).interfaces.map { Type.getInternalName(it) }
            }
        }

        private fun superClassName(type: String, classNames: Map<String, ClassNode>): String? {
            return if(type in classNames) {
                classNames.getValue(type).superName
            } else {
                val c = Class.forName(type.replace('/', '.'))
                if(c.isInterface) {
                    OBJECT_INTERNAL_NAME
                } else {
                    c.superclass?.let { Type.getInternalName(it) }
                }
            }
        }
    }
}