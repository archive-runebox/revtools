package io.runebox.deobfuscator.transformer

import io.runebox.asm.tree.ClassPool
import io.runebox.asm.tree.identifier
import io.runebox.deobfuscator.Transformer
import org.objectweb.asm.tree.FieldInsnNode
import org.tinylog.kotlin.Logger
import java.lang.reflect.Modifier

class UnusedFieldRemover : Transformer {

    private var count = 0

    override fun run(pool: ClassPool) {
        val usedFields = pool.allClasses.flatMap { it.methods }
            .flatMap { it.instructions.toArray().asIterable() }
            .mapNotNull { it as? FieldInsnNode }
            .map { "${it.owner}.${it.name}" }
            .toSet()

        pool.classes.forEach { cls ->
            val fields = cls.fields.iterator()
            while(fields.hasNext()) {
                val field = fields.next()
                if(!usedFields.contains(field.identifier) && Modifier.isFinal(field.access)) {
                    fields.remove()
                    count++
                }
            }
        }

        Logger.info("Removed $count unused fields.")
    }
}
