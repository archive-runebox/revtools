package io.runebox.deobfuscator.asm

import io.runebox.asm.tree.owner
import io.runebox.asm.tree.pool
import io.runebox.asm.util.field
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes.ASM9
import org.objectweb.asm.tree.MethodNode

var MethodNode.obfName: String by field()
var MethodNode.obfDesc: String by field()

var MethodNode.hasOpaque: Boolean by field { false }

val MethodNode.callers: Set<MethodNode> get() {
    val list = hashSetOf<MethodNode>()
    this.accept(object : MethodVisitor(ASM9) {
        override fun visitMethodInsn(
            opcode: Int,
            owner: String,
            name: String,
            descriptor: String,
            isInterface: Boolean
        ) {
            if(this@callers.pool.findClass(owner) != null) {
                list.addAll(pool.getMethodTree(owner, name, desc))
            }
        }
    })
    return list
}

val MethodNode.superCallers: Set<MethodNode> get() {
    val list = hashSetOf<MethodNode>()
    list.addAll(pool.getMethodTree(owner.name, name, desc))
    list.remove(this)
    return list
}