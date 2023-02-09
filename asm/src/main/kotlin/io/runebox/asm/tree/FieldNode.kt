package io.runebox.asm.tree

import io.runebox.asm.util.field
import org.objectweb.asm.Opcodes.ACC_PRIVATE
import org.objectweb.asm.Opcodes.ACC_STATIC
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode

fun FieldNode.init(owner: ClassNode) {
    this.owner = owner
}

var FieldNode.owner: ClassNode by field()
val FieldNode.pool get() = owner.pool

val FieldNode.identifier get() = "${owner.identifier}.$name"
val FieldNode.type get() = Type.getType(desc)

fun FieldNode.isStatic() = (access and ACC_STATIC) != 0
fun FieldNode.isPrivate() = (access and ACC_PRIVATE) != 0

val FieldNode.hierarchy: Set<FieldNode> get() {
    return owner.superClasses.plus(owner.subClasses).mapNotNull { it.getField(name, desc) }.toSet()
}