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
 * Implementation of integer data type for OpenCL operations.
 * Supports int[], Integer[], and Integer primitive types.
 *
 * <p>This class provides conversion and buffer management for integer data,
 * supporting both primitive and boxed types. It includes optimized handling for
 * different input formats while maintaining type safety and null checking.
 *
 * <p>Example usage:
 * <pre>
 * IntDataProcessor intData = new IntDataProcessor();
 *
 * // Using primitive array
 * int[] primitiveArray = {1, 2, 3};
 * int size = intData.getSizeArray(primitiveArray);
 * ByteBuffer buffer = ByteBuffer.allocate(size * intData.getSizeStruct());
 * intData.convertToByteBuffer(buffer, primitiveArray);
 *
 * // Using boxed array
 * Integer[] boxedArray = {1, 2, 3};
 * intData.convertToByteBuffer(buffer, boxedArray);
 *
 * // Using single value
 * intData.convertToByteBuffer(buffer, 1);
 * </pre>
 *
 * @author Vladyslav Kushnir
 * @see DataProcessor
 * @see ToByteBuffer
 * @see FromByteBuffer
 * @since 1.0
 */
public class IntDataProcessor implements DataProcessor, FromByteBuffer, ToByteBuffer {
    private static final Logger logger = LoggerFactory.getLogger(IntDataProcessor.class);

    /**
     * {@inheritDoc}
     * Supports converting to int[], Integer[], and Integer types.
     *
     * @param buffer the source buffer
     * @param target the target object (int[], Integer[], or Integer)
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
            if (target instanceof int[]) {
                convertBufferToPrimitiveArray(buffer, (int[]) target);
            } else if (target instanceof Integer[]) {
                convertBufferToBoxedArray(buffer, (Integer[]) target);
            } else {
                String message = String.format(
                        "Unsupported target type: %s. Expected int[], Integer[] or Integer",
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
     * Converts ByteBuffer data to a primitive int array.
     *
     * @param buffer the source buffer
     * @param target the target array
     */
    private void convertBufferToPrimitiveArray(ByteBuffer buffer, int[] target) {
        logger.debug("Converting buffer to primitive int array of length: {}", target.length);
        buffer.asIntBuffer().get(target);
    }

    /**
     * Converts ByteBuffer data to a boxed Integer array.
     *
     * @param buffer the source buffer
     * @param target the target array
     */
    private void convertBufferToBoxedArray(ByteBuffer buffer, Integer[] target) {
        logger.debug("Converting buffer to boxed Integer array of length: {}", target.length);

        int[] temp = new int[target.length];
        buffer.asIntBuffer().get(temp);

        for (int i = 0; i < temp.length; i++) {
            target[i] = temp[i];
        }
    }

    /**
     * {@inheritDoc}
     * Creates a new int array of the specified size.
     *
     * @param size the size of the array to create
     * @return a new int array
     * @throws IllegalArgumentException if size is negative
     */
    @Override
    public Object createArr(int size) {
        validateSize(size, "array size");
        logger.debug("Creating new int array of size: {}", size);
        return new int[size];
    }

    /**
     * {@inheritDoc}
     * Supports converting from int[], Integer[], and Integer types.
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

        if (source instanceof int[]) {
            convertPrimitiveArrayToBuffer(buffer, (int[]) source);
        } else if (source instanceof Integer[]) {
            convertBoxedArrayToBuffer(buffer, (Integer[]) source);
        } else if (source instanceof Integer) {
            buffer.putInt((Integer) source);
        } else {
            String message = String.format(
                    "Unsupported source type: %s. Expected int[], Integer[] or Integer",
                    source.getClass().getSimpleName()
            );
            logger.error(message);
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * Converts a primitive int array to ByteBuffer.
     *
     * @param buffer the destination buffer
     * @param source the source array
     */
    private void convertPrimitiveArrayToBuffer(ByteBuffer buffer, int[] source) {
        logger.debug("Converting primitive int array of length: {}", source.length);
        buffer.asIntBuffer().put(source);
        buffer.position(buffer.position() + source.length * Integer.BYTES);
    }

    /**
     * Converts a boxed Integer array to ByteBuffer.
     * Performs null checking on array elements.
     *
     * @param buffer the destination buffer
     * @param source the source array
     * @throws NullPointerException if any element is null
     */
    private void convertBoxedArrayToBuffer(ByteBuffer buffer, Integer[] source) {
        logger.debug("Converting boxed Integer array of length: {}", source.length);

        if (Arrays.stream(source).anyMatch(i -> i == null)) {
            String message = "Integer array contains null elements";
            logger.error(message);
            throw new NullPointerException(message);
        }

        int[] primitiveArray = new int[source.length];
        for (int i = 0; i < source.length; i++) {
            primitiveArray[i] = source[i];
        }
        buffer.asIntBuffer().put(primitiveArray);
        buffer.position(buffer.position() + source.length * Integer.BYTES);
    }

    /**
     * {@inheritDoc}
     *
     * @return size of int in bytes (4 bytes)
     */
    @Override
    public int getSizeStruct() {
        return Integer.BYTES;
    }

    /**
     * {@inheritDoc}
     * Supports int[], Integer[], and Integer types.
     *
     * @param arr the array or value to measure
     * @return the number of elements; 1 for single Integer value
     * @throws IllegalArgumentException if the input is null or of unsupported type
     */
    @Override
    public int getSizeArray(Object arr) {
        if (arr == null) {
            String message = "Input array or value cannot be null";
            logger.error(message);
            throw new IllegalArgumentException(message);
        }

        if (arr instanceof int[]) {
            return ((int[]) arr).length;
        } else if (arr instanceof Integer[]) {
            return ((Integer[]) arr).length;
        } else if (arr instanceof Integer) {
            return 1;
        }

        String message = String.format(
                "Unsupported type: %s. Expected int[], Integer[] or Integer",
                arr.getClass().getSimpleName()
        );
        logger.error(message);
        throw new IllegalArgumentException(message);
    }
}
