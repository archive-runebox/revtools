package io.runebox.asm

import io.runebox.asm.tree.ClassPool
import io.runebox.asm.tree.isAssignableFrom
import io.runebox.asm.tree.isInterface
import io.runebox.asm.tree.superClass
import org.objectweb.asm.ClassWriter

class PoolClassWriter(private val pool: ClassPool, flags: Int) : ClassWriter(flags) {
    override fun getCommonSuperClass(typeA: String, typeB: String): String {
        try {
            return super.getCommonSuperClass(typeA, typeB)
        } catch (e: Exception) {
            if(pool.findClass(typeA) != null && pool.findClass(typeB) != null) {
                val superA = pool.findClass(typeA)!!.superName
                val superB = pool.findClass(typeB)!!.superName
                if(superA == superB) {
                    return superA
                }
            }
            return "java/lang/Object"
        }
    }
}