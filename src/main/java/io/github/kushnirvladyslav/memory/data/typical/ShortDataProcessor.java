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
 * Implementation of short integer data type for OpenCL operations.
 * Supports short[], Short[], and Short primitive types.
 *
 * <p>This class provides conversion and buffer management for short integer data,
 * supporting both primitive and boxed types. It includes optimized handling for
 * different input formats while maintaining type safety and null checking.
 *
 * <p>Example usage:
 * <pre>
 * ShortDataProcessor shortData = new ShortDataProcessor();
 *
 * // Using primitive array
 * short[] primitiveArray = {1, 2, 3};
 * int size = shortData.getSizeArray(primitiveArray);
 * ByteBuffer buffer = ByteBuffer.allocate(size * shortData.getSizeStruct());
 * shortData.convertToByteBuffer(buffer, primitiveArray);
 *
 * // Using boxed array
 * Short[] boxedArray = {1, 2, 3};
 * shortData.convertToByteBuffer(buffer, boxedArray);
 *
 * // Using single value
 * shortData.convertToByteBuffer(buffer, (short)1);
 * </pre>
 *
 * @author Vladyslav Kushnir
 * @see DataProcessor
 * @see ToByteBuffer
 * @see FromByteBuffer
 * @since 1.0
 */
public class ShortDataProcessor implements DataProcessor, FromByteBuffer, ToByteBuffer {
    private static final Logger logger = LoggerFactory.getLogger(ShortDataProcessor.class);

    /**
     * {@inheritDoc}
     * Supports converting to short[], Short[], and Short types.
     *
     * @param buffer the source buffer
     * @param target the target object (short[], Short[], or Short)
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
            if (target instanceof short[]) {
                convertBufferToPrimitiveArray(buffer, (short[]) target);
            } else if (target instanceof Short[]) {
                convertBufferToBoxedArray(buffer, (Short[]) target);
            } else {
                String message = String.format(
                        "Unsupported target type: %s. Expected short[], Short[] or Short",
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
     * Converts ByteBuffer data to a primitive short array.
     *
     * @param buffer the source buffer
     * @param target the target array
     */
    private void convertBufferToPrimitiveArray(ByteBuffer buffer, short[] target) {
        logger.debug("Converting buffer to primitive short array of length: {}", target.length);
        buffer.asShortBuffer().get(target);
    }

    /**
     * Converts ByteBuffer data to a boxed Short array.
     *
     * @param buffer the source buffer
     * @param target the target array
     */
    private void convertBufferToBoxedArray(ByteBuffer buffer, Short[] target) {
        logger.debug("Converting buffer to boxed Short array of length: {}", target.length);

        short[] temp = new short[target.length];
        buffer.asShortBuffer().get(temp);

        for (int i = 0; i < temp.length; i++) {
            target[i] = temp[i];
        }
    }

    /**
     * {@inheritDoc}
     * Creates a new short array of the specified size.
     *
     * @param size the size of the array to create
     * @return a new short array
     * @throws IllegalArgumentException if size is negative
     */
    @Override
    public Object createArr(int size) {
        validateSize(size, "array size");
        logger.debug("Creating new short array of size: {}", size);
        return new short[size];
    }

    /**
     * {@inheritDoc}
     * Supports converting from short[], Short[], and Short types.
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

        if (source instanceof short[]) {
            convertPrimitiveArrayToBuffer(buffer, (short[]) source);
        } else if (source instanceof Short[]) {
            convertBoxedArrayToBuffer(buffer, (Short[]) source);
        } else if (source instanceof Short) {
            buffer.putShort((Short) source);
        } else {
            String message = String.format(
                    "Unsupported source type: %s. Expected short[], Short[] or Short",
                    source.getClass().getSimpleName()
            );
            logger.error(message);
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * Converts a primitive short array to ByteBuffer.
     *
     * @param buffer the destination buffer
     * @param source the source array
     */
    private void convertPrimitiveArrayToBuffer(ByteBuffer buffer, short[] source) {
        logger.debug("Converting primitive short array of length: {}", source.length);
        buffer.asShortBuffer().put(source);
        buffer.position(buffer.position() + source.length * Short.BYTES);
    }

    /**
     * Converts a boxed Short array to ByteBuffer.
     * Performs null checking on array elements.
     *
     * @param buffer the destination buffer
     * @param source the source array
     * @throws NullPointerException if any element is null
     */
    private void convertBoxedArrayToBuffer(ByteBuffer buffer, Short[] source) {
        logger.debug("Converting boxed Short array of length: {}", source.length);

        if (Arrays.stream(source).anyMatch(s -> s == null)) {
            String message = "Short array contains null elements";
            logger.error(message);
            throw new NullPointerException(message);
        }

        short[] primitiveArray = new short[source.length];
        for (int i = 0; i < source.length; i++) {
            primitiveArray[i] = source[i];
        }
        buffer.asShortBuffer().put(primitiveArray);
        buffer.position(buffer.position() + source.length * Short.BYTES);
    }

    /**
     * {@inheritDoc}
     *
     * @return size of short in bytes (2 bytes)
     */
    @Override
    public int getSizeStruct() {
        return Short.BYTES;
    }

    /**
     * {@inheritDoc}
     * Supports short[], Short[], and Short types.
     *
     * @param arr the array or value to measure
     * @return the number of elements; 1 for single Short value
     * @throws IllegalArgumentException if the input is null or of unsupported type
     */
    @Override
    public int getSizeArray(Object arr) {
        if (arr == null) {
            String message = "Input array or value cannot be null";
            logger.error(message);
            throw new IllegalArgumentException(message);
        }

        if (arr instanceof short[]) {
            return ((short[]) arr).length;
        } else if (arr instanceof Short[]) {
            return ((Short[]) arr).length;
        } else if (arr instanceof Short) {
            return 1;
        }

        String message = String.format(
                "Unsupported type: %s. Expected short[], Short[] or Short",
                arr.getClass().getSimpleName()
        );
        logger.error(message);
        throw new IllegalArgumentException(message);
    }
}
