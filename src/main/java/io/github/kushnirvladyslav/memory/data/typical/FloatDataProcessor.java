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
 * Implementation of floating-point data type for OpenCL operations.
 * Supports float[], Float[], and Float primitive types.
 *
 * <p>This class provides conversion and buffer management for floating-point data,
 * supporting both primitive and boxed types. It includes optimized handling for
 * different input formats while maintaining type safety and null checking.
 *
 * <p>Example usage:
 * <pre>
 * FloatDataProcessor floatData = new FloatDataProcessor();
 *
 * // Using primitive array
 * float[] primitiveArray = {1.0f, 2.0f, 3.0f};
 * int size = floatData.getSizeArray(primitiveArray);
 * ByteBuffer buffer = ByteBuffer.allocate(size * floatData.getSizeStruct());
 * floatData.convertToByteBuffer(buffer, primitiveArray);
 *
 * // Using boxed array
 * Float[] boxedArray = {1.0f, 2.0f, 3.0f};
 * floatData.convertToByteBuffer(buffer, boxedArray);
 *
 * // Using single value
 * floatData.convertToByteBuffer(buffer, 1.0f);
 * </pre>
 *
 * @author Vladyslav Kushnir
 * @see DataProcessor
 * @see ToByteBuffer
 * @see FromByteBuffer
 * @since 1.0
 */
public class FloatDataProcessor implements DataProcessor, FromByteBuffer, ToByteBuffer {
    private static final Logger logger = LoggerFactory.getLogger(FloatDataProcessor.class);

    /**
     * {@inheritDoc}
     * Supports converting to float[], Float[], and Float types.
     *
     * @param buffer the source buffer
     * @param target the target object (float[], Float[], or Float)
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
            if (target instanceof float[]) {
                convertBufferToPrimitiveArray(buffer, (float[]) target);
            } else if (target instanceof Float[]) {
                convertBufferToBoxedArray(buffer, (Float[]) target);
            } else {
                String message = String.format(
                        "Unsupported target type: %s. Expected float[], Float[] or Float",
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
     * Converts ByteBuffer data to a primitive float array.
     *
     * @param buffer the source buffer
     * @param target the target array
     */
    private void convertBufferToPrimitiveArray(ByteBuffer buffer, float[] target) {
        logger.debug("Converting buffer to primitive float array of length: {}", target.length);
        buffer.asFloatBuffer().get(target);
    }

    /**
     * Converts ByteBuffer data to a boxed Float array.
     *
     * @param buffer the source buffer
     * @param target the target array
     */
    private void convertBufferToBoxedArray(ByteBuffer buffer, Float[] target) {
        logger.debug("Converting buffer to boxed Float array of length: {}", target.length);

        float[] temp = new float[target.length];
        buffer.asFloatBuffer().get(temp);

        for (int i = 0; i < temp.length; i++) {
            target[i] = temp[i];
        }
    }

    /**
     * {@inheritDoc}
     * Creates a new float array of the specified size.
     *
     * @param size the size of the array to create
     * @return a new float array
     * @throws IllegalArgumentException if size is negative
     */
    @Override
    public Object createArr(int size) {
        validateSize(size, "array size");
        logger.debug("Creating new float array of size: {}", size);
        return new float[size];
    }

    /**
     * {@inheritDoc}
     * Supports converting from float[], Float[], and Float types.
     *
     * @param buffer the destination buffer
     * @param source the source data
     * @throws IllegalArgumentException if source is null, contains null elements, or is of unsupported type
     * @throws BufferOverflowException  if buffer has insufficient space
     */
    @Override
    public void convertToByteBuffer(ByteBuffer buffer, Object source) {
        if (source == null) {
            String message = "Source data cannot be null";
            logger.error(message);
            throw new IllegalArgumentException(message);
        }


        if (source instanceof float[]) {
            convertPrimitiveArrayToBuffer(buffer, (float[]) source);
        } else if (source instanceof Float[]) {
            convertBoxedArrayToBuffer(buffer, (Float[]) source);
        } else if (source instanceof Float) {
            buffer.putFloat((Float) source);
        } else {
            String message = String.format(
                    "Unsupported source type: %s. Expected float[], Float[] or Float",
                    source.getClass().getSimpleName()
            );
            logger.error(message);
            throw new IllegalArgumentException(message);
        }

    }

    /**
     * Converts a primitive float array to ByteBuffer.
     *
     * @param buffer the destination buffer
     * @param source the source array
     */
    private void convertPrimitiveArrayToBuffer(ByteBuffer buffer, float[] source) {
        logger.debug("Converting primitive float array of length: {}", source.length);
        buffer.asFloatBuffer().put(source);
        buffer.position(buffer.position() + source.length * Float.BYTES);
    }

    /**
     * Converts a boxed Float array to ByteBuffer.
     * Performs null checking on array elements.
     *
     * @param buffer the destination buffer
     * @param source the source array
     * @throws NullPointerException if any element is null
     */
    private void convertBoxedArrayToBuffer(ByteBuffer buffer, Float[] source) {
        logger.debug("Converting boxed Float array of length: {}", source.length);

        if (Arrays.stream(source).anyMatch(f -> f == null)) {
            String message = "Float array contains null elements";
            logger.error(message);
            throw new NullPointerException(message);
        }

        float[] primitiveArray = new float[source.length];
        for (int i = 0; i < source.length; i++) {
            primitiveArray[i] = source[i];
        }
        buffer.asFloatBuffer().put(primitiveArray);
        buffer.position(buffer.position() + source.length * Float.BYTES);
    }

    /**
     * {@inheritDoc}
     *
     * @return size of float in bytes (4 bytes)
     */
    @Override
    public int getSizeStruct() {
        return Float.BYTES;
    }

    /**
     * {@inheritDoc}
     * Supports float[], Float[], and Float types.
     *
     * @param arr the array or value to measure
     * @return the number of elements; 1 for single Float value
     * @throws IllegalArgumentException if the input is null or of unsupported type
     */
    @Override
    public int getSizeArray(Object arr) {
        if (arr == null) {
            String message = "Input array or value cannot be null";
            logger.error(message);
            throw new IllegalArgumentException(message);
        }

        if (arr instanceof float[]) {
            return ((float[]) arr).length;
        } else if (arr instanceof Float[]) {
            return ((Float[]) arr).length;
        } else if (arr instanceof Float) {
            return 1;
        }

        String message = String.format(
                "Unsupported type: %s. Expected float[], Float[] or Float",
                arr.getClass().getSimpleName()
        );
        logger.error(message);
        throw new IllegalArgumentException(message);
    }

}
