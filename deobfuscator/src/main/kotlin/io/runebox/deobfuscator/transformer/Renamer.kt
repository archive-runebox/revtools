package io.runebox.deobfuscator.transformer

import io.runebox.asm.tree.*
import io.runebox.asm.util.InheritanceGraph
import io.runebox.deobfuscator.Deobfuscator
import io.runebox.deobfuscator.Deobfuscator.encodeObfNameMap
import io.runebox.deobfuscator.Transformer
import io.runebox.deobfuscator.asm.isObfuscatedName
import io.runebox.deobfuscator.asm.obfDesc
import io.runebox.deobfuscator.asm.obfName
import org.objectweb.asm.commons.ClassRemapper
import org.objectweb.asm.commons.SimpleRemapper
import org.objectweb.asm.tree.ClassNode
import org.tinylog.kotlin.Logger

class Renamer : Transformer {

    private var classCount = 0
    private var methodCount = 0
    private var fieldCount = 0

    private val mappings = hashMapOf<String, String>()

    override fun run(pool: ClassPool) {
        this.generateMappings(pool)
        this.applyMappings(pool)

        Logger.info("Renamed $classCount classes.")
        Logger.info("Renamed $methodCount method.")
        Logger.info("Renamed $fieldCount fields.")
    }

    private fun generateMappings(pool: ClassPool) {
        val hierarchy = InheritanceGraph(pool)
        /*
         * Generate Class name mappings
         */
        mappings["client"] = "Client"
        pool.classes.forEach { cls ->
            if(!cls.name.isObfuscatedName()) return@forEach
            val newName = "class${++classCount}"
            mappings[cls.identifier] = newName
        }

        /*
         * Generate Method name mappings
         */
        pool.classes.forEach { cls ->
            cls.methods.forEach methodLoop@ { method ->
                if(!method.name.isObfuscatedName() || mappings.containsKey(method.identifier)) return@methodLoop
                val newName = "method${++methodCount}"
                mappings[method.identifier] = newName
                hierarchy[cls.name]!!.children.forEach { child ->
                    mappings["${child.name}.${method.name}${method.desc}"] = newName
                }
            }
        }

        /*
         * Generate Field mapping names
         */
        pool.classes.forEach { cls ->
            cls.fields.forEach fieldLoop@ { field ->
                if(!field.name.isObfuscatedName() || mappings.containsKey(field.identifier)) return@fieldLoop
                val newName = "field${++fieldCount}"
                mappings[field.identifier] = newName
                hierarchy[cls.name]!!.children.forEach { child ->
                    mappings["${child.name}.${field.name}"] = newName
                }
            }
        }
    }

    private fun applyMappings(pool: ClassPool) {
        val remapper = SimpleRemapper(mappings)
        pool.classes.forEach { cls ->
            val newCls = ClassNode()
            cls.accept(ClassRemapper(newCls, remapper))
            newCls.ignored = cls.ignored
            newCls.obfName = cls.obfName
            cls.methods.forEachIndexed { i, m ->
                newCls.methods[i].obfName = m.obfName
                newCls.methods[i].obfDesc = m.obfDesc
            }
            cls.fields.forEachIndexed { i, f ->
                newCls.fields[i].obfName = f.obfName
                newCls.fields[i].obfDesc = f.obfDesc
            }
            pool.replaceClass(cls, newCls)
        }
        pool.loadHierarchy()
        pool.encodeObfNameMap()
    }
}