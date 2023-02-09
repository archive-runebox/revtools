package io.runebox.deobfuscator

import io.runebox.asm.tree.ClassPool

interface Transformer {

    fun run(pool: ClassPool)

}