/*
 * Copyright 2025 Kushnir Vladyslav
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.kushnirvladyslav.memory.data.typical;

import io.github.kushnirvladyslav.memory.data.DataProcessor;
import io.github.kushnirvladyslav.memory.data.FromByteBuffer;
import io.github.kushnirvladyslav.memory.data.ToByteBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Implementation of double-precision floating-point data type for OpenCL operations.
 * Supports double[], Double[], and Double primitive types.
 *
 * <p>This class provides conversion and buffer management for double-precision data,
 * supporting both primitive and boxed types. It includes optimized handling for
 * different input formats while maintaining type safety and null checking.
 *
 * <p>Example usage:
 * <pre>
 * DoubleDataProcessor doubleData = new DoubleDataProcessor();
 *
 * // Using primitive array
 * double[] primitiveArray = {1.0, 2.0, 3.0};
 * int size = doubleData.getSizeArray(primitiveArray);
 * ByteBuffer buffer = ByteBuffer.allocate(size * doubleData.getSizeStruct());
 * doubleData.convertToByteBuffer(buffer, primitiveArray);
 *
 * // Using boxed array
 * Double[] boxedArray = {1.0, 2.0, 3.0};
 * doubleData.convertToByteBuffer(buffer, boxedArray);
 *
 * // Using single value
 * doubleData.convertToByteBuffer(buffer, 1.0);
 * </pre>
 *
 * @author Vladyslav Kushnir
 * @see DataProcessor
 * @see ToByteBuffer
 * @see FromByteBuffer
 * @since 1.0
 */
public class DoubleDataProcessor implements DataProcessor, FromByteBuffer, ToByteBuffer {
    private static final Logger logger = LoggerFactory.getLogger(DoubleDataProcessor.class);

    /**
     * {@inheritDoc}
     * Supports converting to double[], Double[], and Double types.
     *
     * @param buffer the source buffer
     * @param target the target object (double[], Double[], or Double)
     * @throws IllegalArgumentException if target is null or of unsupported type
     * @throws BufferUnderflowException if buffer has insufficient data
     */
    @Override
    public void convertFromByteBuffer(ByteBuffer buffer, Object target) {
        if (target == null) {
            String message = "Target array or value cannot be null";
            logger.error(message);
            throw new IllegalArgumentException(message);
        }

        try {
            if (target instanceof double[]) {
                convertBufferToPrimitiveArray(buffer, (double[]) target);
            } else if (target instanceof Double[]) {
                convertBufferToBoxedArray(buffer, (Double[]) target);
            } else {
                String message = String.format(
                        "Unsupported target type: %s. Expected double[], Double[] or Double",
                        target.getClass().getSimpleName()
                );
                logger.error(message);
                throw new IllegalArgumentException(message);
            }
        } catch (NullPointerException e) {
            String message = "Target array contains null elements";
            logger.error(message, e);
            throw new IllegalArgumentException(message, e);
        }
    }

    /**
     * Converts ByteBuffer data to a primitive double array.
     *
     * @param buffer the source buffer
     * @param target the target array
     */
    private void convertBufferToPrimitiveArray(ByteBuffer buffer, double[] target) {
        logger.debug("Converting buffer to primitive double array of length: {}", target.length);
        buffer.asDoubleBuffer().get(target);
    }

    /**
     * Converts ByteBuffer data to a boxed Double array.
     *
     * @param buffer the source buffer
     * @param target the target array
     */
    private void convertBufferToBoxedArray(ByteBuffer buffer, Double[] target) {
        logger.debug("Converting buffer to boxed Double array of length: {}", target.length);

        double[] temp = new double[target.length];
        buffer.asDoubleBuffer().get(temp);

        for (int i = 0; i < temp.length; i++) {
            target[i] = temp[i];
        }
    }

    /**
     * {@inheritDoc}
     * Creates a new double array of the specified size.
     *
     * @param size the size of the array to create
     * @return a new double array
     * @throws IllegalArgumentException if size is negative
     */
    @Override
    public Object createArr(int size) {
        validateSize(size, "array size");
        logger.debug("Creating new double array of size: {}", size);
        return new double[size];
    }

    /**
     * {@inheritDoc}
     * Supports converting from double[], Double[], and Double types.
     *
     * @param buffer the destination buffer
     * @param source the source data
     * @throws IllegalArgumentException if source is null, contains null elements, or is of unsupported type
     * @throws BufferOverflowException if buffer has insufficient space
     */
    @Override
    public void convertToByteBuffer(ByteBuffer buffer, Object source) {
        if (source == null) {
            String message = "Source data cannot be null";
            logger.error(message);
            throw new IllegalArgumentException(message);
        }

        if (source instanceof double[]) {
            convertPrimitiveArrayToBuffer(buffer, (double[]) source);
        } else if (source instanceof Double[]) {
            convertBoxedArrayToBuffer(buffer, (Double[]) source);
        } else if (source instanceof Double) {
            buffer.putDouble((Double) source);
        } else {
            String message = String.format(
                    "Unsupported source type: %s. Expected double[], Double[] or Double",
                    source.getClass().getSimpleName()
            );
            logger.error(message);
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * Converts a primitive double array to ByteBuffer.
     *
     * @param buffer the destination buffer
     * @param source the source array
     */
    private void convertPrimitiveArrayToBuffer(ByteBuffer buffer, double[] source) {
        logger.debug("Converting primitive double array of length: {}", source.length);
        buffer.asDoubleBuffer().put(source);
        buffer.position(buffer.position() + source.length * Double.BYTES);
    }

    /**
     * Converts a boxed Double array to ByteBuffer.
     * Performs null checking on array elements.
     *
     * @param buffer the destination buffer
     * @param source the source array
     * @throws NullPointerException if any element is null
     */
    private void convertBoxedArrayToBuffer(ByteBuffer buffer, Double[] source) {
        logger.debug("Converting boxed Double array of length: {}", source.length);

        if (Arrays.stream(source).anyMatch(d -> d == null)) {
            String message = "Double array contains null elements";
            logger.error(message);
            throw new NullPointerException(message);
        }

        double[] primitiveArray = new double[source.length];
        for (int i = 0; i < source.length; i++) {
            primitiveArray[i] = source[i];
        }
        buffer.asDoubleBuffer().put(primitiveArray);
        buffer.position(buffer.position() + source.length * Double.BYTES);
    }

    /**
     * {@inheritDoc}
     *
     * @return size of double in bytes (8 bytes)
     */
    @Override
    public int getSizeStruct() {
        return Double.BYTES;
    }

    /**
     * {@inheritDoc}
     * Supports double[], Double[], and Double types.
     *
     * @param arr the array or value to measure
     * @return the number of elements; 1 for single Double value
     * @throws IllegalArgumentException if the input is null or of unsupported type
     */
    @Override
    public int getSizeArray(Object arr) {
        if (arr == null) {
            String message = "Input array or value cannot be null";
            logger.error(message);
            throw new IllegalArgumentException(message);
        }

        if (arr instanceof double[]) {
            return ((double[]) arr).length;
        } else if (arr instanceof Double[]) {
            return ((Double[]) arr).length;
        } else if (arr instanceof Double) {
            return 1;
        }

        String message = String.format(
                "Unsupported type: %s. Expected double[], Double[] or Double",
                arr.getClass().getSimpleName()
        );
        logger.error(message);
        throw new IllegalArgumentException(message);
    }
}
