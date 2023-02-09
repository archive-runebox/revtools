package io.runebox.deobfuscator.asm

import io.runebox.asm.util.field
import org.objectweb.asm.tree.FieldNode

var FieldNode.obfName: String by field()
var FieldNode.obfDesc: String by field()