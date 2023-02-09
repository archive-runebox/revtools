package io.runebox.revtools.mapper.asm

import io.runebox.asm.util.nullField
import org.objectweb.asm.tree.MethodNode

var MethodNode.origOwner: String? by nullField()