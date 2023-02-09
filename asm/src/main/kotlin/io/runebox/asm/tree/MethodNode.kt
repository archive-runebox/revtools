package io.runebox.asm.tree

import io.runebox.asm.util.field
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode

fun MethodNode.init(owner: ClassNode) {
    this.owner = owner
}

var MethodNode.owner: ClassNode by field()
val MethodNode.pool get() = owner.pool

val MethodNode.identifier get() = "${owner.identifier}.$name$desc"
val MethodNode.type get() = Type.getMethodType(desc)

fun MethodNode.isStatic() = (access and ACC_STATIC) != 0
fun MethodNode.isAbstract() = (access and ACC_ABSTRACT) != 0
fun MethodNode.isPrivate() = (access and ACC_PRIVATE) != 0

fun MethodNode.isConstructor() = name == "<init>"
fun MethodNode.isInitializer() = name == "<clinit>"

val MethodNode.hierarchy: Set<MethodNode> get() = owner.superClasses
    .plus(owner.subClasses)
    .plus(owner)
    .mapNotNull { it.getMethod(name, desc) }
    .distinct()
    .toSet()