package io.runebox.asm.tree

import org.objectweb.asm.tree.ClassNode
import java.io.File
import java.io.FileOutputStream
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream

class ClassPool {

    private val classMap = hashMapOf<String, ClassNode>()

    val classes get() = classMap.values.filter { !it.ignored }.toList()
    val ignoredClasses get() = classMap.values.filter { it.ignored }.toList()
    val allClasses get() = classMap.values.toList()

    fun loadHierarchy() {
        allClasses.forEach { it.loadHierarchy() }
    }

    fun addClass(cls: ClassNode) {
        cls.init(this)
        classMap[cls.name] = cls
    }

    fun removeClass(cls: ClassNode) {
        classMap.remove(cls.name)
    }

    fun replaceClass(old: ClassNode, new: ClassNode) {
        removeClass(old)
        addClass(new)
    }

    fun clear() { classMap.clear() }

    fun getClass(name: String) = classMap.filterValues { !it.ignored }[name]
    fun getIgnoredClass(name: String) = classMap.filterValues { it.ignored }[name]
    fun findClass(name: String) = classMap[name]

    fun addJar(file: File, action: (JarFile) -> Unit = {}) {
        JarFile(file).use { jar ->
            jar.entries().asSequence().forEach { entry ->
                if(entry.name.endsWith(".class")) {
                    val cls = ClassNode().fromByteArray(jar.getInputStream(entry).readAllBytes())
                    addClass(cls)
                }
            }
            action(jar)
        }
    }

    fun writeJar(file: File, action: (JarOutputStream) -> Unit = {}) {
        if(file.exists()) {
            file.deleteRecursively()
        }
        JarOutputStream(FileOutputStream(file)).use { jos ->
            allClasses.forEach { cls ->
                jos.putNextEntry(JarEntry("${cls.name}.class"))
                jos.write(cls.toByteArray())
                jos.closeEntry()
            }
            action(jos)
        }
    }

}