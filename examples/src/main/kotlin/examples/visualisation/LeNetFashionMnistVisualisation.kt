/*
 * Copyright 2020 JetBrains s.r.o. and Kotlin Deep Learning project contributors. All Rights Reserved.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package examples.visualisation

import examples.inference.lenet5
import org.jetbrains.kotlinx.dl.api.core.loss.Losses
import org.jetbrains.kotlinx.dl.api.core.metric.Metrics
import org.jetbrains.kotlinx.dl.api.core.optimizer.Adam
import org.jetbrains.kotlinx.dl.dataset.fashionMnist

private const val EPOCHS = 1
private const val TRAINING_BATCH_SIZE = 500
private const val TEST_BATCH_SIZE = 1000

private val fashionMnistLabelEncoding = mapOf(
    0 to "T-shirt/top",
    1 to "Trouser",
    2 to "Pullover",
    3 to "Dress",
    4 to "Coat",
    5 to "Sandal",
    6 to "Shirt",
    7 to "Sneaker",
    8 to "Bag",
    9 to "Ankle boot"
)

/**
 * This examples demonstrates model activations and Conv2D filters visualisation.
 *
 * Model is trained on FashionMnist dataset.
 */
fun main() {
    val (train, test) = fashionMnist()

    val (newTrain, validation) = train.split(0.95)

    lenet5().use {
        it.compile(
            optimizer = Adam(),
            loss = Losses.SOFT_MAX_CROSS_ENTROPY_WITH_LOGITS,
            metric = Metrics.ACCURACY
        )

        it.fit(
            trainingDataset = newTrain,
            validationDataset = validation,
            epochs = EPOCHS,
            trainBatchSize = TRAINING_BATCH_SIZE,
            validationBatchSize = TEST_BATCH_SIZE
        )

        val imageId = 0

        val weights = it.layers[1].weights // first conv2d layer

        drawFilters(weights.values.toTypedArray()[0], colorCoefficient = 10.0)

        val weights2 = it.layers[3].weights // first conv2d layer

        drawFilters(weights2.values.toTypedArray()[0], colorCoefficient = 12.0)

        val accuracy = it.evaluate(dataset = test, batchSize = TEST_BATCH_SIZE).metrics[Metrics.ACCURACY]

        println("Accuracy $accuracy")

        val (prediction, activations) = it.predictAndGetActivations(train.getX(imageId))

        println("Prediction: ${fashionMnistLabelEncoding[prediction]}")

        drawActivations(activations)

        val trainImageLabel = train.getY(imageId)

        println("Ground Truth: ${fashionMnistLabelEncoding[trainImageLabel.toInt()]}")
    }
}
