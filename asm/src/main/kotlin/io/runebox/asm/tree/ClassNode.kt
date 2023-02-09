package io.runebox.asm.tree

import io.runebox.asm.PoolClassWriter
import io.runebox.asm.util.field
import io.runebox.asm.util.nullField
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes.ACC_ABSTRACT
import org.objectweb.asm.Opcodes.ACC_INTERFACE
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.MethodNode

fun ClassNode.init(pool: ClassPool) {
    this.pool = pool
    methods.forEach { it.init(this) }
    fields.forEach { it.init(this) }
}

fun ClassNode.loadHierarchy() {
    superClass = null
    interfaceClasses.clear()
    children.clear()

    superClass = pool.getClass(superName)
    if(superClass != null) {
        superClass!!.children.add(this)
    }
    interfaces.mapNotNull { pool.getClass(it) }.forEach {
        interfaceClasses.add(it)
        it.children.add(this)
    }
}

var ClassNode.pool: ClassPool by field()
var ClassNode.ignored: Boolean by field { false }
var ClassNode.superClass: ClassNode? by nullField()
val ClassNode.interfaceClasses: MutableList<ClassNode> by field { mutableListOf() }
val ClassNode.children: MutableList<ClassNode> by field { mutableListOf() }

val ClassNode.identifier get() = name
val ClassNode.type get() = Type.getObjectType(name)

fun ClassNode.getMethod(name: String, desc: String) = methods.firstOrNull { it.name == name && it.desc == desc }
fun ClassNode.getField(name: String, desc: String) = fields.firstOrNull { it.name == name && it.desc == desc }

fun ClassNode.isInterface() = (access and ACC_INTERFACE) != 0
fun ClassNode.isAbstract() = (access and ACC_ABSTRACT) != 0

fun ClassNode.toByteArray(): ByteArray {
    val writer = PoolClassWriter(pool, ClassWriter.COMPUTE_FRAMES)
    this.accept(writer)
    return writer.toByteArray()
}

fun ClassNode.fromByteArray(bytes: ByteArray): ClassNode {
    val reader = ClassReader(bytes)
    reader.accept(this, ClassReader.SKIP_FRAMES)
    return this
}

fun ClassNode.isOverride(name: String, desc: String): Boolean {
    val supCls = this.superClass
    if(supCls != null) {
        if(supCls.getMethod(name, desc) != null) {
            return true
        }
        if(supCls.isOverride(name, desc)) {
            return true
        }
    }
    interfaceClasses.forEach { itf ->
        if(itf.getMethod(name, desc) != null) {
            return true
        }
        if(itf.isOverride(name, desc)) {
            return true
        }
    }
    return false
}

fun ClassNode.isAssignableFrom(cls: ClassNode): Boolean {
    return cls == this || this.isSuperClassOf(cls) || this.isSuperInterfaceOf(cls)
}

val ClassNode.superClasses: Set<ClassNode> get() = interfaceClasses
    .plus(superClass)
    .filterNotNull()
    .flatMap { it.superClasses.plus(it) }
    .toSet()

val ClassNode.subClasses: Set<ClassNode> get() = children
    .flatMap { it.subClasses.plus(it) }
    .toSet()

val ClassNode.hierarchy: Set<ClassNode> get() = superClasses
    .plus(subClasses)
    .plus(this)
    .distinct()
    .toSet()

fun ClassNode.resolveMethod(name: String, desc: String): MethodNode? {
    for(rel in superClasses) {
        return rel.resolveMethod(name, desc) ?: continue
    }
    return getMethod(name, desc)
}

fun ClassNode.resolveField(name: String, desc: String): FieldNode? {
    for(rel in superClasses) {
        return rel.resolveField(name, desc) ?: continue
    }
    return getField(name, desc)
}

private tailrec fun ClassNode.isSuperClassOf(cls: ClassNode): Boolean {
    val supCls = cls.superClass ?: return false
    if(supCls == this) {
        return true
    }
    return this.isSuperClassOf(supCls)
}

private fun ClassNode.isSuperInterfaceOf(cls: ClassNode): Boolean {
    cls.interfaceClasses.forEach { supItf ->
        if(supItf == this || this.isSuperInterfaceOf(supItf)) {
            return true
        }
    }
    return false
}
