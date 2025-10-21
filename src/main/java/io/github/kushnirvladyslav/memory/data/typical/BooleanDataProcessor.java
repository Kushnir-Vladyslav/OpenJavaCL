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
 * Implementation of boolean data type for OpenCL operations.
 * Supports boolean[], Boolean[], and Boolean types.
 *
 * <p>This class provides conversion and buffer management for boolean data,
 * supporting both primitive and boxed types. Boolean values are stored as bytes
 * where 0 represents false and 1 represents true.
 *
 * <p>Example usage:
 * <pre>
 * BooleanDataProcessor booleanData = new BooleanDataProcessor();
 *
 * // Using primitive array
 * boolean[] primitiveArray = {true, false, true};
 * int size = booleanData.getSizeArray(primitiveArray);
 * ByteBuffer buffer = ByteBuffer.allocate(size * booleanData.getSizeStruct());
 * booleanData.convertToByteBuffer(buffer, primitiveArray);
 *
 * // Using boxed array
 * Boolean[] boxedArray = {true, false, true};
 * booleanData.convertToByteBuffer(buffer, boxedArray);
 *
 * // Using single value
 * booleanData.convertToByteBuffer(buffer, true);
 * </pre>
 *
 * @author Vladyslav Kushnir
 * @see DataProcessor
 * @see ToByteBuffer
 * @see FromByteBuffer
 * @since 1.0
 */
public class BooleanDataProcessor implements DataProcessor, FromByteBuffer, ToByteBuffer {
    private static final Logger logger = LoggerFactory.getLogger(BooleanDataProcessor.class);
    private static final byte TRUE_VALUE = 1;
    private static final byte FALSE_VALUE = 0;

    /**
     * {@inheritDoc}
     * Supports converting to boolean[], Boolean[], and Boolean types.
     *
     * @param buffer the source buffer
     * @param target the target object (boolean[], Boolean[], or Boolean)
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
            if (target instanceof boolean[]) {
                convertBufferToPrimitiveArray(buffer, (boolean[]) target);
            } else if (target instanceof Boolean[]) {
                convertBufferToBoxedArray(buffer, (Boolean[]) target);
            } else {
                String message = String.format(
                        "Unsupported target type: %s. Expected boolean[], Boolean[] or Boolean",
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
     * Converts ByteBuffer data to a primitive boolean array.
     *
     * @param buffer the source buffer
     * @param target the target array
     */
    private void convertBufferToPrimitiveArray(ByteBuffer buffer, boolean[] target) {
        logger.debug("Converting buffer to primitive boolean array of length: {}", target.length);
        for (int i = 0; i < target.length; i++) {
            target[i] = buffer.get() == TRUE_VALUE;
        }
    }

    /**
     * Converts ByteBuffer data to a boxed Boolean array.
     *
     * @param buffer the source buffer
     * @param target the target array
     */
    private void convertBufferToBoxedArray(ByteBuffer buffer, Boolean[] target) {
        logger.debug("Converting buffer to boxed Boolean array of length: {}", target.length);
        for (int i = 0; i < target.length; i++) {
            target[i] = buffer.get() == TRUE_VALUE;
        }
    }

    /**
     * {@inheritDoc}
     * Creates a new boolean array of the specified size.
     *
     * @param size the size of the array to create
     * @return a new boolean array
     * @throws IllegalArgumentException if size is negative
     */
    @Override
    public Object createArr(int size) {
        validateSize(size, "array size");
        logger.debug("Creating new boolean array of size: {}", size);
        return new boolean[size];
    }

    /**
     * {@inheritDoc}
     * Supports converting from boolean[], Boolean[], and Boolean types.
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

        if (source instanceof boolean[]) {
            convertPrimitiveArrayToBuffer(buffer, (boolean[]) source);
        } else if (source instanceof Boolean[]) {
            convertBoxedArrayToBuffer(buffer, (Boolean[]) source);
        } else if (source instanceof Boolean) {
            buffer.put(((Boolean) source) ? TRUE_VALUE : FALSE_VALUE);
        } else {
            String message = String.format(
                    "Unsupported source type: %s. Expected boolean[], Boolean[] or Boolean",
                    source.getClass().getSimpleName()
            );
            logger.error(message);
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * Converts a primitive boolean array to ByteBuffer.
     *
     * @param buffer the destination buffer
     * @param source the source array
     */
    private void convertPrimitiveArrayToBuffer(ByteBuffer buffer, boolean[] source) {
        logger.debug("Converting primitive boolean array of length: {}", source.length);
        for (boolean value : source) {
            buffer.put(value ? TRUE_VALUE : FALSE_VALUE);
        }
    }

    /**
     * Converts a boxed Boolean array to ByteBuffer.
     * Performs null checking on array elements.
     *
     * @param buffer the destination buffer
     * @param source the source array
     * @throws NullPointerException if any element is null
     */
    private void convertBoxedArrayToBuffer(ByteBuffer buffer, Boolean[] source) {
        logger.debug("Converting boxed Boolean array of length: {}", source.length);

        if (Arrays.stream(source).anyMatch(b -> b == null)) {
            String message = "Boolean array contains null elements";
            logger.error(message);
            throw new NullPointerException(message);
        }

        for (Boolean value : source) {
            buffer.put(value ? TRUE_VALUE : FALSE_VALUE);
        }
    }

    /**
     * {@inheritDoc}
     *
     * @return size of boolean in bytes (1 byte)
     */
    @Override
    public int getSizeStruct() {
        return 1; // Boolean stored as single byte
    }

    /**
     * {@inheritDoc}
     * Supports boolean[], Boolean[], and Boolean types.
     *
     * @param arr the array or value to measure
     * @return the number of elements; 1 for single Boolean value
     * @throws IllegalArgumentException if the input is null or of unsupported type
     */
    @Override
    public int getSizeArray(Object arr) {
        if (arr == null) {
            String message = "Input array or value cannot be null";
            logger.error(message);
            throw new IllegalArgumentException(message);
        }

        if (arr instanceof boolean[]) {
            return ((boolean[]) arr).length;
        } else if (arr instanceof Boolean[]) {
            return ((Boolean[]) arr).length;
        } else if (arr instanceof Boolean) {
            return 1;
        }

        String message = String.format(
                "Unsupported type: %s. Expected boolean[], Boolean[] or Boolean",
                arr.getClass().getSimpleName()
        );
        logger.error(message);
        throw new IllegalArgumentException(message);
    }
}
