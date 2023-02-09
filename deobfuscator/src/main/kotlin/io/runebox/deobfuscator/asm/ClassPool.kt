package io.runebox.deobfuscator.asm

import io.runebox.asm.tree.ClassPool
import io.runebox.asm.util.field
import io.runebox.deobfuscator.Deobfuscator.encodeNameIndexMap
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode
import java.io.File
import java.util.jar.JarFile

fun ClassPool.build() {
    /*
     * Store initial pool nodes names.
     */
    this.initInitialNames()

    /*
     * Extract the initial name index map.
     * This is used to remap between revs.
     */
    this.initNameIndexMap()
}

var ClassPool.sourceFile: File by field()
var ClassPool.nameIndexMap: HashMap<Int, String> by field { hashMapOf() }

private fun ClassPool.initInitialNames() {
    allClasses.forEach { cls ->
        cls.obfName = cls.name
        cls.methods.forEach { method ->
            method.obfName = method.name
            method.obfDesc = method.desc
        }
        cls.fields.forEach { field ->
            field.obfName = field.name
            field.obfDesc = field.desc
        }
    }
}

private fun ClassPool.initNameIndexMap() {
    JarFile(sourceFile).use { jar ->
        jar.entries().asSequence().forEachIndexed { index, entry ->
            if(entry.name.endsWith(".class")) {
                if(entry.name.contains("/")) return@forEachIndexed
                nameIndexMap[index] = entry.name.replace(".class", "")
            }
        }
    }
    encodeNameIndexMap()
}

fun String.isObfuscatedName(): Boolean {
    return this.length <= 2 ||
            (this.length == 3 && listOf("aa", "ab", "ac", "ad", "ae", "af").any { this.startsWith(it) } && this != "add") ||
            (listOf("class", "method", "field").any { this.startsWith(it) })
}

fun ClassPool.getMethodTree(owner: String, name: String, desc: String): Set<MethodNode> {
    val list = mutableSetOf<MethodNode>()
    var cls: ClassNode? = this.getClass(owner)
    while(cls != null) {
        list.addAll(cls.methods.filter { it.name == name && it.desc == desc})
        cls = this.getClass(cls.superName)
    }
    return list
}