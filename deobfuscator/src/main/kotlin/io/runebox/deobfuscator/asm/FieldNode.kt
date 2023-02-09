package io.runebox.deobfuscator.asm

import io.runebox.asm.util.field
import io.runebox.asm.util.nullField
import org.objectweb.asm.tree.FieldNode

var FieldNode.origOwner: String? by nullField()
var FieldNode.obfName: String by field()
var FieldNode.obfDesc: String by field()