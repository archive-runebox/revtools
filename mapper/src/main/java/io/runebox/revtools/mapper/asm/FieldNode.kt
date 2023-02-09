package io.runebox.revtools.mapper.asm

import io.runebox.asm.util.nullField
import org.objectweb.asm.tree.FieldNode

var FieldNode.origOwner: String? by nullField()