package io.runebox.deobfuscator.transformer

import io.disassemble.asm.ClassFactory
import io.disassemble.asm.visitor.expr.node.CompBranchExpr
import io.disassemble.asm.visitor.expr.node.ConstExpr
import io.disassemble.asm.visitor.expr.node.FieldExpr
import io.disassemble.asm.visitor.expr.node.MathExpr
import io.disassemble.asm.visitor.expr.node.VarLoadExpr
import io.runebox.asm.tree.ClassPool
import io.runebox.deobfuscator.Transformer
import org.objectweb.asm.Opcodes.*
import org.tinylog.kotlin.Logger

class ExprOrderNormalizer : Transformer {

    private var count = 0

    override fun run(pool: ClassPool) {
        pool.classes.forEach { cls ->
            ClassFactory(cls).methods.forEach { method ->
                method.tree().get().forEach exprLoop@ { expr ->
                    if(expr is MathExpr) {
                        if(expr.opcode() !in IADD..DADD && expr.opcode() !in IMUL..DMUL) return@exprLoop
                        if(expr.expr1() is ConstExpr && (expr.expr2() is FieldExpr || expr.expr2() is VarLoadExpr)) {
                            val origLeft = expr.left()
                            val origRight = expr.right()
                            expr.setLeft(origRight)
                            expr.setRight(origLeft)
                            count++
                        }
                    }
                    else if(expr is CompBranchExpr) {
                        if(expr.opcode() !in IF_ICMPEQ..IF_ICMPNE && expr.opcode() !in IF_ACMPEQ..IF_ACMPNE) return@exprLoop
                        if(expr.left() is ConstExpr && (expr.right() is FieldExpr || expr.right() is VarLoadExpr)) {
                            val origLeft = expr.left()
                            val origRight = expr.right()
                            expr.setLeft(origRight)
                            expr.setRight(origLeft)
                            count++
                        }
                    }
                }
            }
        }

        Logger.info("Reordered $count method expressions.")
    }
}