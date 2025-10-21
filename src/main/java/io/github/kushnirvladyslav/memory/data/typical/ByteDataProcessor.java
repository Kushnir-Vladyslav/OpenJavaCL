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
 * Implementation of byte data type for OpenCL operations.
 * Supports byte[], Byte[], and Byte primitive types.
 *
 * <p>This class provides conversion and buffer management for byte data,
 * supporting both primitive and boxed types. It includes optimized handling for
 * different input formats while maintaining type safety and null checking.
 *
 * <p>Example usage:
 * <pre>
 * ByteDataProcessor byteData = new ByteDataProcessor();
 *
 * // Using primitive array
 * byte[] primitiveArray = {1, 2, 3};
 * int size = byteData.getSizeArray(primitiveArray);
 * ByteBuffer buffer = ByteBuffer.allocate(size * byteData.getSizeStruct());
 * byteData.convertToByteBuffer(buffer, primitiveArray);
 *
 * // Using boxed array
 * Byte[] boxedArray = {1, 2, 3};
 * byteData.convertToByteBuffer(buffer, boxedArray);
 *
 * // Using single value
 * byteData.convertToByteBuffer(buffer, (byte)1);
 * </pre>
 *
 * @author Vladyslav Kushnir
 * @see DataProcessor
 * @see ToByteBuffer
 * @see FromByteBuffer
 * @since 1.0
 */
public class ByteDataProcessor implements DataProcessor, FromByteBuffer, ToByteBuffer {
    private static final Logger logger = LoggerFactory.getLogger(ByteDataProcessor.class);

    /**
     * {@inheritDoc}
     * Supports converting to byte[], Byte[], and Byte types.
     *
     * @param buffer the source buffer
     * @param target the target object (byte[], Byte[], or Byte)
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
            if (target instanceof byte[]) {
                convertBufferToPrimitiveArray(buffer, (byte[]) target);
            } else if (target instanceof Byte[]) {
                convertBufferToBoxedArray(buffer, (Byte[]) target);
            } else {
                String message = String.format(
                        "Unsupported target type: %s. Expected byte[], Byte[] or Byte",
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
     * Converts ByteBuffer data to a primitive byte array.
     *
     * @param buffer the source buffer
     * @param target the target array
     */
    private void convertBufferToPrimitiveArray(ByteBuffer buffer, byte[] target) {
        logger.debug("Converting buffer to primitive byte array of length: {}", target.length);
        buffer.get(target);
    }

    /**
     * Converts ByteBuffer data to a boxed Byte array.
     *
     * @param buffer the source buffer
     * @param target the target array
     */
    private void convertBufferToBoxedArray(ByteBuffer buffer, Byte[] target) {
        logger.debug("Converting buffer to boxed Byte array of length: {}", target.length);

        byte[] temp = new byte[target.length];
        buffer.get(temp);

        for (int i = 0; i < temp.length; i++) {
            target[i] = temp[i];
        }
    }

    /**
     * {@inheritDoc}
     * Creates a new byte array of the specified size.
     *
     * @param size the size of the array to create
     * @return a new byte array
     * @throws IllegalArgumentException if size is negative
     */
    @Override
    public Object createArr(int size) {
        validateSize(size, "array size");
        logger.debug("Creating new byte array of size: {}", size);
        return new byte[size];
    }

    /**
     * {@inheritDoc}
     * Supports converting from byte[], Byte[], and Byte types.
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

        if (source instanceof byte[]) {
            convertPrimitiveArrayToBuffer(buffer, (byte[]) source);
        } else if (source instanceof Byte[]) {
            convertBoxedArrayToBuffer(buffer, (Byte[]) source);
        } else if (source instanceof Byte) {
            buffer.put((Byte) source);
        } else {
            String message = String.format(
                    "Unsupported source type: %s. Expected byte[], Byte[] or Byte",
                    source.getClass().getSimpleName()
            );
            logger.error(message);
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * Converts a primitive byte array to ByteBuffer.
     *
     * @param buffer the destination buffer
     * @param source the source array
     */
    private void convertPrimitiveArrayToBuffer(ByteBuffer buffer, byte[] source) {
        logger.debug("Converting primitive byte array of length: {}", source.length);
        buffer.put(source);
    }

    /**
     * Converts a boxed Byte array to ByteBuffer.
     * Performs null checking on array elements.
     *
     * @param buffer the destination buffer
     * @param source the source array
     * @throws NullPointerException if any element is null
     */
    private void convertBoxedArrayToBuffer(ByteBuffer buffer, Byte[] source) {
        logger.debug("Converting boxed Byte array of length: {}", source.length);

        if (Arrays.stream(source).anyMatch(b -> b == null)) {
            String message = "Byte array contains null elements";
            logger.error(message);
            throw new NullPointerException(message);
        }

        byte[] primitiveArray = new byte[source.length];
        for (int i = 0; i < source.length; i++) {
            primitiveArray[i] = source[i];
        }
        buffer.put(primitiveArray);
    }

    /**
     * {@inheritDoc}
     *
     * @return size of byte in bytes (1 byte)
     */
    @Override
    public int getSizeStruct() {
        return Byte.BYTES;
    }

    /**
     * {@inheritDoc}
     * Supports byte[], Byte[], and Byte types.
     *
     * @param arr the array or value to measure
     * @return the number of elements; 1 for single Byte value
     * @throws IllegalArgumentException if the input is null or of unsupported type
     */
    @Override
    public int getSizeArray(Object arr) {
        if (arr == null) {
            String message = "Input array or value cannot be null";
            logger.error(message);
            throw new IllegalArgumentException(message);
        }

        if (arr instanceof byte[]) {
            return ((byte[]) arr).length;
        } else if (arr instanceof Byte[]) {
            return ((Byte[]) arr).length;
        } else if (arr instanceof Byte) {
            return 1;
        }

        String message = String.format(
                "Unsupported type: %s. Expected byte[], Byte[] or Byte",
                arr.getClass().getSimpleName()
        );
        logger.error(message);
        throw new IllegalArgumentException(message);
    }
}
