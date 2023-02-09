package io.runebox.deobfuscator.transformer

import io.runebox.asm.tree.ClassPool
import io.runebox.deobfuscator.Transformer
import org.objectweb.asm.Type
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.TryCatchBlockNode
import org.tinylog.kotlin.Logger

class ExceptionRangeOptimizer : Transformer {

    private var count = 0

    override fun run(pool: ClassPool) {
        pool.classes.forEach { cls ->
            cls.methods.forEach { method ->
                val tryCatchIndexes = hashMapOf<Int, MutableList<TryCatchBlockNode>>()
                method.tryCatchBlocks.forEach { tcb ->
                    val handlerIndex = method.instructions.indexOf(tcb.handler)
                    if(!tryCatchIndexes.containsKey(handlerIndex)) {
                        val tcbHandlers = mutableListOf<TryCatchBlockNode>()
                        tcbHandlers.add(tcb)
                        tryCatchIndexes[handlerIndex] = tcbHandlers
                    } else {
                        tryCatchIndexes[handlerIndex]!!.add(tcb)
                    }
                }

                tryCatchIndexes.forEach { _, tcbList ->
                    if(tcbList.size > 1) {
                        var start = Int.MAX_VALUE
                        var end = 0
                        val handler = tcbList[0].handler
                        val types = hashSetOf<String>()
                        tcbList.forEach { tcb ->
                            types.add(tcb.type)
                        }
                        tcbList.forEach { tcb ->
                            val startIdx = method.instructions.indexOf(tcb.start)
                            if(startIdx < start) {
                                start = startIdx
                            }

                            val endIdx = method.instructions.indexOf(tcb.end)
                            if(endIdx > end) {
                                end = endIdx
                            }

                            method.tryCatchBlocks.remove(tcb)
                            count++
                        }

                        types.filterNotNull().forEach { type ->
                            val tcb = TryCatchBlockNode(
                                method.instructions[start] as LabelNode,
                                method.instructions[end] as LabelNode,
                                handler,
                                type
                            )
                            method.tryCatchBlocks.add(tcb)
                        }
                    }
                }
            }
        }

        Logger.info("Optimized $count try-catch block handler exception ranges.")
    }
}