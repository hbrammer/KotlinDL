/*
 * Copyright 2020 JetBrains s.r.o. and Kotlin Deep Learning project contributors. All Rights Reserved.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package org.jetbrains.kotlinx.dl.api.core.layer.convolutional

import org.jetbrains.kotlinx.dl.api.core.KGraph
import org.jetbrains.kotlinx.dl.api.core.activation.Activations
import org.jetbrains.kotlinx.dl.api.core.initializer.HeNormal
import org.jetbrains.kotlinx.dl.api.core.initializer.HeUniform
import org.jetbrains.kotlinx.dl.api.core.initializer.Initializer
import org.jetbrains.kotlinx.dl.api.core.layer.Layer
import org.jetbrains.kotlinx.dl.api.core.shape.*
import org.jetbrains.kotlinx.dl.api.core.util.conv2dBiasVarName
import org.jetbrains.kotlinx.dl.api.core.util.conv2dKernelVarName
import org.jetbrains.kotlinx.dl.api.core.util.getDType
import org.tensorflow.Operand
import org.tensorflow.Shape
import org.tensorflow.op.Ops
import org.tensorflow.op.core.Variable
import org.tensorflow.op.nn.Conv2d
import org.tensorflow.op.nn.Conv2d.dilations
import kotlin.math.roundToInt

private const val KERNEL_VARIABLE_NAME = "conv2d_kernel"
private const val BIAS_VARIABLE_NAME = "conv2d_bias"

/**
 * 2D convolution layer (e.g. spatial convolution over images).
 *
 * This layer creates a convolution kernel that is convolved (actually cross-correlated)
 * with the layer input to produce a tensor of outputs.
 * Finally, if `activation` is applied to the outputs as well.
 *
 * @property [filters] The dimensionality of the output space (i.e. the number of filters in the convolution).
 * @property [kernelSize] Two long numbers, specifying the height and width of the 2D convolution window.
 * @property [strides] Strides of the pooling operation for each dimension of input tensor.
 * NOTE: Specifying any stride value != 1 is incompatible with specifying any `dilation_rate` value != 1.
 * @property [dilations] Four numbers, specifying the dilation rate to use for dilated convolution for each dimension of input tensor.
 * @property [activation] Activation function.
 * @property [kernelInitializer] An initializer for the convolution kernel
 * @property [biasInitializer] An initializer for the bias vector.
 * @property [padding] The padding method, either 'valid' or 'same' or 'full'.
 * @property [name] Custom layer name.
 * @property [useBias] If true the layer uses a bias vector.
 * @constructor Creates [Conv2D] object.
 */
public class Conv2D(
    public val filters: Long = 32,
    public val kernelSize: LongArray = longArrayOf(3, 3),
    public val strides: LongArray = longArrayOf(1, 1, 1, 1),
    public val dilations: LongArray = longArrayOf(1, 1, 1, 1),
    public val activation: Activations = Activations.Relu,
    public val kernelInitializer: Initializer = HeNormal(),
    public val biasInitializer: Initializer = HeUniform(),
    public val padding: ConvPadding = ConvPadding.SAME,
    public val useBias: Boolean = true,
    name: String = ""
) : Layer(name) {
    // weight tensors
    private lateinit var kernel: Variable<Float>
    private var bias: Variable<Float>? = null

    // weight tensor shapes
    private lateinit var biasShape: Shape
    private lateinit var kernelShape: Shape

    override fun build(tf: Ops, kGraph: KGraph, inputShape: Shape) {
        // Amount of channels should be the last value in the inputShape (make warning here)
        val lastElement = inputShape.size(inputShape.numDimensions() - 1)

        // Compute shapes of kernel and bias matrices
        kernelShape = shapeFromDims(*kernelSize, lastElement, filters)
        biasShape = Shape.make(filters)

        // should be calculated before addWeight because it's used in calculation, need to rewrite addWEight to avoid strange behaviour
        // calculate fanIn, fanOut
        val inputDepth = lastElement // amount of channels
        val outputDepth = filters // amount of channels for the next layer

        fanIn = (inputDepth * kernelSize[0] * kernelSize[1]).toInt()
        fanOut = ((outputDepth * kernelSize[0] * kernelSize[1] / (strides[0].toDouble() * strides[1])).roundToInt())

        val (kernelVariableName, biasVariableName) = defineVariableNames()
        createConv2DVariables(tf, kernelVariableName, biasVariableName, kGraph)
    }

    private fun defineVariableNames(): Pair<String, String> {
        return if (name.isNotEmpty()) {
            Pair(conv2dKernelVarName(name), conv2dBiasVarName(name))
        } else {
            Pair(KERNEL_VARIABLE_NAME, BIAS_VARIABLE_NAME)
        }
    }

    private fun createConv2DVariables(
        tf: Ops,
        kernelVariableName: String,
        biasVariableName: String,
        kGraph: KGraph
    ) {
        kernel = tf.withName(kernelVariableName).variable(kernelShape, getDType())
        if (useBias) bias = tf.withName(biasVariableName).variable(biasShape, getDType())

        kernel = addWeight(tf, kGraph, kernelVariableName, kernel, kernelInitializer)
        if (useBias) bias = addWeight(tf, kGraph, biasVariableName, bias!!, biasInitializer)
    }

    override fun computeOutputShape(inputShape: Shape): Shape {
        var rows = inputShape.size(1)
        var cols = inputShape.size(2)
        rows = convOutputLength(
            rows, kernelSize[0].toInt(), padding,
            strides[1].toInt(), dilations[1].toInt()
        )
        cols = convOutputLength(
            cols, kernelSize[1].toInt(), padding,
            strides[2].toInt(), dilations[2].toInt()
        )

        val shape = Shape.make(inputShape.size(0), rows, cols, filters)
        outputShape = TensorShape(shape)
        return shape
    }

    override fun forward(
        tf: Ops,
        input: Operand<Float>,
        isTraining: Operand<Boolean>,
        numberOfLosses: Operand<Float>?
    ): Operand<Float> {
        val tfPadding = when (padding) {
            ConvPadding.SAME -> "SAME"
            ConvPadding.VALID -> "VALID"
            ConvPadding.FULL -> "FULL"
        }

        val options: Conv2d.Options = dilations(dilations.toList()).dataFormat("NHWC")
        var output: Operand<Float> = tf.nn.conv2d(input, kernel, strides.toMutableList(), tfPadding, options)

        if (useBias) {
            output = tf.nn.biasAdd(output, bias)
        }

        return Activations.convert(activation).apply(tf, output, name)
    }

    override val weights: Map<String, Array<*>> get() = extractConv2DWeights()

    private fun extractConv2DWeights(): Map<String, Array<*>> {
        return extractWeights(defineVariableNames().toList())
    }

    /** Returns the shape of kernel weights. */
    public val kernelShapeArray: LongArray get() = TensorShape(kernelShape).dims()

    /** Returns the shape of bias weights. */
    public val biasShapeArray: LongArray get() = TensorShape(biasShape).dims()

    override val hasActivation: Boolean get() = true

    override val paramCount: Int
        get() = (numElementsInShape(shapeToLongArray(kernelShape)) + numElementsInShape(shapeToLongArray(biasShape))).toInt()

    override fun toString(): String {
        return "Conv2D(filters=$filters, kernelSize=${kernelSize.contentToString()}, strides=${strides.contentToString()}, dilations=${dilations.contentToString()}, activation=$activation, kernelInitializer=$kernelInitializer, biasInitializer=$biasInitializer, kernelShape=$kernelShape, padding=$padding)"
    }
}
