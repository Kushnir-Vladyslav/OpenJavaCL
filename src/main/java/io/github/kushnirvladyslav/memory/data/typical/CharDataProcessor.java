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
 * Implementation of character data type for OpenCL operations.
 * Supports char[], Character[], and Character types.
 *
 * <p>This class provides conversion and buffer management for character data,
 * supporting both primitive and boxed types. It includes optimized handling for
 * different input formats while maintaining type safety and null checking.
 *
 * <p>Example usage:
 * <pre>
 * CharDataProcessor charData = new CharDataProcessor();
 *
 * // Using primitive array
 * char[] primitiveArray = {'a', 'b', 'c'};
 * int size = charData.getSizeArray(primitiveArray);
 * ByteBuffer buffer = ByteBuffer.allocate(size * charData.getSizeStruct());
 * charData.convertToByteBuffer(buffer, primitiveArray);
 *
 * // Using boxed array
 * Character[] boxedArray = {'x', 'y', 'z'};
 * charData.convertToByteBuffer(buffer, boxedArray);
 *
 * // Using single value
 * charData.convertToByteBuffer(buffer, 'q');
 * </pre>
 *
 * @author Vladyslav Kushnir
 * @see DataProcessor
 * @see ToByteBuffer
 * @see FromByteBuffer
 * @since 1.0
 */
public class CharDataProcessor implements DataProcessor, FromByteBuffer, ToByteBuffer {
    private static final Logger logger = LoggerFactory.getLogger(CharDataProcessor.class);

    /**
     * {@inheritDoc}
     * Supports converting to char[], Character[], and Character types.
     *
     * @param buffer the source buffer
     * @param target the target object (char[], Character[], or Character)
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
            if (target instanceof char[]) {
                convertBufferToPrimitiveArray(buffer, (char[]) target);
            } else if (target instanceof Character[]) {
                convertBufferToBoxedArray(buffer, (Character[]) target);
            } else {
                String message = String.format(
                        "Unsupported target type: %s. Expected char[], Character[] or Character",
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
     * Converts ByteBuffer data to a primitive char array.
     *
     * @param buffer the source buffer
     * @param target the target array
     */
    private void convertBufferToPrimitiveArray(ByteBuffer buffer, char[] target) {
        logger.debug("Converting buffer to primitive char array of length: {}", target.length);
        buffer.asCharBuffer().get(target);
    }

    /**
     * Converts ByteBuffer data to a boxed Character array.
     *
     * @param buffer the source buffer
     * @param target the target array
     */
    private void convertBufferToBoxedArray(ByteBuffer buffer, Character[] target) {
        logger.debug("Converting buffer to boxed Character array of length: {}", target.length);

        char[] temp = new char[target.length];
        buffer.asCharBuffer().get(temp);

        for (int i = 0; i < temp.length; i++) {
            target[i] = temp[i];
        }
    }

    /**
     * {@inheritDoc}
     * Creates a new char array of the specified size.
     *
     * @param size the size of the array to create
     * @return a new char array
     * @throws IllegalArgumentException if size is negative
     */
    @Override
    public Object createArr(int size) {
        validateSize(size, "array size");
        logger.debug("Creating new char array of size: {}", size);
        return new char[size];
    }

    /**
     * {@inheritDoc}
     * Supports converting from char[], Character[], and Character types.
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

        if (source instanceof char[]) {
            convertPrimitiveArrayToBuffer(buffer, (char[]) source);
        } else if (source instanceof Character[]) {
            convertBoxedArrayToBuffer(buffer, (Character[]) source);
        } else if (source instanceof Character) {
            buffer.putChar((Character) source);
        } else {
            String message = String.format(
                    "Unsupported source type: %s. Expected char[], Character[] or Character",
                    source.getClass().getSimpleName()
            );
            logger.error(message);
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * Converts a primitive char array to ByteBuffer.
     *
     * @param buffer the destination buffer
     * @param source the source array
     */
    private void convertPrimitiveArrayToBuffer(ByteBuffer buffer, char[] source) {
        logger.debug("Converting primitive char array of length: {}", source.length);
        buffer.asCharBuffer().put(source);
        buffer.position(buffer.position() + source.length * Character.BYTES);
    }

    /**
     * Converts a boxed Character array to ByteBuffer.
     * Performs null checking on array elements.
     *
     * @param buffer the destination buffer
     * @param source the source array
     * @throws NullPointerException if any element is null
     */
    private void convertBoxedArrayToBuffer(ByteBuffer buffer, Character[] source) {
        logger.debug("Converting boxed Character array of length: {}", source.length);

        if (Arrays.stream(source).anyMatch(c -> c == null)) {
            String message = "Character array contains null elements";
            logger.error(message);
            throw new NullPointerException(message);
        }

        char[] primitiveArray = new char[source.length];
        for (int i = 0; i < source.length; i++) {
            primitiveArray[i] = source[i];
        }
        buffer.asCharBuffer().put(primitiveArray);
        buffer.position(buffer.position() + source.length * Character.BYTES);
    }

    /**
     * {@inheritDoc}
     *
     * @return size of char in bytes (2 bytes)
     */
    @Override
    public int getSizeStruct() {
        return Character.BYTES;
    }

    /**
     * {@inheritDoc}
     * Supports char[], Character[], and Character types.
     *
     * @param arr the array or value to measure
     * @return the number of elements; 1 for single Character value
     * @throws IllegalArgumentException if the input is null or of unsupported type
     */
    @Override
    public int getSizeArray(Object arr) {
        if (arr == null) {
            String message = "Input array or value cannot be null";
            logger.error(message);
            throw new IllegalArgumentException(message);
        }

        if (arr instanceof char[]) {
            return ((char[]) arr).length;
        } else if (arr instanceof Character[]) {
            return ((Character[]) arr).length;
        } else if (arr instanceof Character) {
            return 1;
        }

        String message = String.format(
                "Unsupported type: %s. Expected char[], Character[] or Character",
                arr.getClass().getSimpleName()
        );
        logger.error(message);
        throw new IllegalArgumentException(message);
    }
}
