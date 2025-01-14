/*
 * Copyright 2021 JetBrains s.r.o. and Kotlin Deep Learning project contributors. All Rights Reserved.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package org.jetbrains.kotlinx.dl.api.core.layer.activation

import org.jetbrains.kotlinx.dl.api.core.KGraph
import org.jetbrains.kotlinx.dl.api.core.layer.Layer
import org.jetbrains.kotlinx.dl.api.core.shape.TensorShape
import org.tensorflow.Operand
import org.tensorflow.Shape
import org.tensorflow.op.Ops

/**
 * Base class for all layer class representing activation functions.
 *
 * By default it is marked as __not trainable__ layer with no extra
 * parameters and weights but having the activation on it.
 *
 * By default it defines returning the output with the same shape
 * as the input Operand
 *
 * @param [name] Layer name. Would be changed if empty during model compilation.
 */
public abstract class AbstractActivationLayer(name: String) : Layer(name) {

    init {
        isTrainable = false
    }

    public abstract fun forward(
        tf: Ops,
        input: Operand<Float>
    ): Operand<Float>

    override fun forward(
        tf: Ops,
        input: Operand<Float>,
        isTraining: Operand<Boolean>,
        numberOfLosses: Operand<Float>?
    ): Operand<Float> = forward(tf, input)

    override fun build(tf: Ops, kGraph: KGraph, inputShape: Shape): Unit = Unit

    override fun computeOutputShape(inputShape: Shape): Shape {
        this.outputShape = TensorShape(inputShape)
        return inputShape
    }

    override val weights: Map<String, Array<*>> get() = emptyMap()

    override val hasActivation: Boolean get() = true

    override val paramCount: Int get() = 0
}
