package io.runebox.deobfuscator.asm

import io.runebox.asm.tree.getField
import io.runebox.asm.tree.getMethod
import io.runebox.asm.util.field
import org.objectweb.asm.tree.ClassNode

var ClassNode.obfName: String by field()