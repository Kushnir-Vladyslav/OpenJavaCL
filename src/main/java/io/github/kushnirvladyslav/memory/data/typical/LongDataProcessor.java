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

import io.github.kushnirvladyslav.memory.data.FromByteBuffer;
import io.github.kushnirvladyslav.memory.data.ToByteBuffer;
import io.github.kushnirvladyslav.memory.data.DataProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Implementation of long integer data type for OpenCL operations.
 * Supports long[], Long[], and Long primitive types.
 *
 * <p>This class provides conversion and buffer management for long integer data,
 * supporting both primitive and boxed types. It includes optimized handling for
 * different input formats while maintaining type safety and null checking.
 *
 * <p>Example usage:
 * <pre>
 * LongDataProcessor longData = new LongDataProcessor();
 *
 * // Using primitive array
 * long[] primitiveArray = {1L, 2L, 3L};
 * int size = longData.getSizeArray(primitiveArray);
 * ByteBuffer buffer = ByteBuffer.allocate(size * longData.getSizeStruct());
 * longData.convertToByteBuffer(buffer, primitiveArray);
 *
 * // Using boxed array
 * Long[] boxedArray = {1L, 2L, 3L};
 * longData.convertToByteBuffer(buffer, boxedArray);
 *
 * // Using single value
 * longData.convertToByteBuffer(buffer, 1L);
 * </pre>
 *
 * @author Vladyslav Kushnir
 * @see DataProcessor
 * @see ToByteBuffer
 * @see FromByteBuffer
 * @since 1.0
 */
public class LongDataProcessor implements DataProcessor, FromByteBuffer, ToByteBuffer {
    private static final Logger logger = LoggerFactory.getLogger(LongDataProcessor.class);

    /**
     * {@inheritDoc}
     * Supports converting to long[], Long[], and Long types.
     *
     * @param buffer the source buffer
     * @param target the target object (long[], Long[], or Long)
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
            if (target instanceof long[]) {
                convertBufferToPrimitiveArray(buffer, (long[]) target);
            } else if (target instanceof Long[]) {
                convertBufferToBoxedArray(buffer, (Long[]) target);
            } else {
                String message = String.format(
                        "Unsupported target type: %s. Expected long[], Long[] or Long",
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
     * Converts ByteBuffer data to a primitive long array.
     *
     * @param buffer the source buffer
     * @param target the target array
     */
    private void convertBufferToPrimitiveArray(ByteBuffer buffer, long[] target) {
        logger.debug("Converting buffer to primitive long array of length: {}", target.length);
        buffer.asLongBuffer().get(target);
    }

    /**
     * Converts ByteBuffer data to a boxed Long array.
     *
     * @param buffer the source buffer
     * @param target the target array
     */
    private void convertBufferToBoxedArray(ByteBuffer buffer, Long[] target) {
        logger.debug("Converting buffer to boxed Long array of length: {}", target.length);

        long[] temp = new long[target.length];
        buffer.asLongBuffer().get(temp);

        for (int i = 0; i < temp.length; i++) {
            target[i] = temp[i];
        }
    }

    /**
     * {@inheritDoc}
     * Creates a new long array of the specified size.
     *
     * @param size the size of the array to create
     * @return a new long array
     * @throws IllegalArgumentException if size is negative
     */
    @Override
    public Object createArr(int size) {
        validateSize(size, "array size");
        logger.debug("Creating new long array of size: {}", size);
        return new long[size];
    }

    /**
     * {@inheritDoc}
     * Supports converting from long[], Long[], and Long types.
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

        if (source instanceof long[]) {
            convertPrimitiveArrayToBuffer(buffer, (long[]) source);
        } else if (source instanceof Long[]) {
            convertBoxedArrayToBuffer(buffer, (Long[]) source);
        } else if (source instanceof Long) {
            buffer.putLong((Long) source);
        } else {
            String message = String.format(
                    "Unsupported source type: %s. Expected long[], Long[] or Long",
                    source.getClass().getSimpleName()
            );
            logger.error(message);
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * Converts a primitive long array to ByteBuffer.
     *
     * @param buffer the destination buffer
     * @param source the source array
     */
    private void convertPrimitiveArrayToBuffer(ByteBuffer buffer, long[] source) {
        logger.debug("Converting primitive long array of length: {}", source.length);
        buffer.asLongBuffer().put(source);
        buffer.position(buffer.position() + source.length * Long.BYTES);
    }

    /**
     * Converts a boxed Long array to ByteBuffer.
     * Performs null checking on array elements.
     *
     * @param buffer the destination buffer
     * @param source the source array
     * @throws NullPointerException if any element is null
     */
    private void convertBoxedArrayToBuffer(ByteBuffer buffer, Long[] source) {
        logger.debug("Converting boxed Long array of length: {}", source.length);

        if (Arrays.stream(source).anyMatch(l -> l == null)) {
            String message = "Long array contains null elements";
            logger.error(message);
            throw new NullPointerException(message);
        }

        long[] primitiveArray = new long[source.length];
        for (int i = 0; i < source.length; i++) {
            primitiveArray[i] = source[i];
        }
        buffer.asLongBuffer().put(primitiveArray);
        buffer.position(buffer.position() + source.length * Long.BYTES);
    }

    /**
     * {@inheritDoc}
     *
     * @return size of long in bytes (8 bytes)
     */
    @Override
    public int getSizeStruct() {
        return Long.BYTES;
    }

    /**
     * {@inheritDoc}
     * Supports long[], Long[], and Long types.
     *
     * @param arr the array or value to measure
     * @return the number of elements; 1 for single Long value
     * @throws IllegalArgumentException if the input is null or of unsupported type
     */
    @Override
    public int getSizeArray(Object arr) {
        if (arr == null) {
            String message = "Input array or value cannot be null";
            logger.error(message);
            throw new IllegalArgumentException(message);
        }

        if (arr instanceof long[]) {
            return ((long[]) arr).length;
        } else if (arr instanceof Long[]) {
            return ((Long[]) arr).length;
        } else if (arr instanceof Long) {
            return 1;
        }

        String message = String.format(
                "Unsupported type: %s. Expected long[], Long[] or Long",
                arr.getClass().getSimpleName()
        );
        logger.error(message);
        throw new IllegalArgumentException(message);
    }
}
