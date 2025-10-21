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

package io.github.kushnirvladyslav.memory.buffer;

import io.github.kushnirvladyslav.memory.data.ToByteBuffer;
import io.github.kushnirvladyslav.memory.util.CopyDataBufferToBuffer;
import org.lwjgl.opencl.CL10;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;

/**
 * Interface for OpenCL buffers that support write operations.
 * Provides methods for writing data to OpenCL buffer and managing buffer content.
 *
 * <p>This interface implements write operations for OpenCL buffers by working in conjunction
 * with {@link AbstractGlobalBuffer}. It provides complete implementation of write operations
 * without requiring additional implementation from implementing classes.
 *
 * @param <T> The type of buffer that implements this interface
 * @author Vladyslav Kushnir
 * @since 1.0
 */
public interface Writable<T extends AbstractGlobalBuffer & Writable<T>> {
    Logger logger = LoggerFactory.getLogger(Writable.class);

    /**
     * Writes data to the buffer starting from the beginning.
     *
     * @param arr the data array to write
     * @throws IllegalArgumentException if the input array is null
     * @throws IllegalStateException    if the write operation fails
     */
    default void write(Object arr) {
        if (arr == null) {
            String message = "Input array cannot be null";
            logger.error(message);
            throw new IllegalArgumentException(message);
        }
        write(arr, 0);
    }

    /**
     * Writes data to the buffer starting at specified offset.
     *
     * @param arr    the data array to write
     * @param offset starting position in the buffer
     * @throws IllegalArgumentException if the input array is null or offset is negative
     * @throws IllegalStateException    if the write operation fails
     */
    default void write(Object arr, int offset) {
        @SuppressWarnings("unchecked")
        T buffer = (T) this;

        if (arr == null) {
            String message = String.format("Input array cannot be null for buffer '%s'",
                    buffer.getBufferName());
            logger.error(message);
            throw new IllegalArgumentException(message);
        }

        if (offset < 0) {
            String message = String.format("Offset cannot be negative: %d for buffer '%s'",
                    offset, buffer.getBufferName());
            logger.error(message);
            throw new IllegalArgumentException(message);
        }

        ToByteBuffer converter = (ToByteBuffer) buffer.dataProcessorObject;

        int arrSize = buffer.dataProcessorObject.getSizeArray(arr);
        int dataSize = buffer.dataProcessorObject.getSizeStruct();

        if (arrSize + offset > buffer.capacity) {
            if (buffer instanceof Dynamical<?>) {
                Dynamical<?> dynamical = (Dynamical<?>) this;
                logger.debug("Resizing dynamic buffer '{}' to accommodate new data",
                        buffer.getBufferName());
                dynamical.resize((int) ((arrSize + offset) * 1.5));
            } else {
                String message = String.format(
                        "DataProcessor size (%d) exceeds static buffer capacity (%d) for buffer '%s'",
                        arrSize + offset, buffer.capacity, buffer.getBufferName());
                logger.error(message);
                throw new IllegalStateException(message);
            }
        }

        ByteBuffer tempNativeBuffer = null;

        try {
            if (buffer instanceof Dynamical<?>) {
                tempNativeBuffer = (ByteBuffer) buffer.nativeBuffer.rewind().limit(arrSize * dataSize);
                logger.trace("Using existing native buffer for dynamic buffer '{}'",
                        buffer.getBufferName());
            } else {
                tempNativeBuffer = MemoryUtil.memAlloc(arrSize * dataSize);
                if (tempNativeBuffer == null) {
                    String message = String.format(
                            "Failed to allocate temporary native buffer for buffer '%s'",
                            buffer.getBufferName());
                    logger.error(message);
                    throw new IllegalStateException(message);
                }
                logger.trace("Allocated temporary native buffer for static buffer '{}'",
                        buffer.getBufferName());
            }

            converter.convertToByteBuffer(tempNativeBuffer, arr);

            logger.debug("Writing {} elements to buffer '{}' at offset {}",
                    arrSize, buffer.getBufferName(), offset);

            int errorCode = CL10.clEnqueueWriteBuffer(
                    buffer.context.getCommandQueue(),
                    buffer.clBuffer,
                    true,
                    offset * dataSize,
                    tempNativeBuffer,
                    null,
                    null
            );

            if (errorCode != CL10.CL_SUCCESS) {
                String message = String.format(
                        "OpenCL write buffer failed for buffer '%s': error code %d",
                        buffer.getBufferName(), errorCode);
                logger.error(message);
                throw new IllegalStateException(message);
            }

            buffer.size += arrSize;

            logger.debug("Successfully wrote data to buffer '{}', new size: {}",
                    buffer.getBufferName(), buffer.size);

        }catch (Exception e) {
            String message = String.format("Failed to write to buffer '%s'", buffer.getBufferName());
            logger.error(message, e);
            throw new IllegalStateException(message, e);
        } finally {
            if (!(buffer instanceof Dynamical<?>) && tempNativeBuffer != null) {
                MemoryUtil.memFree(tempNativeBuffer);
                logger.trace("Freed temporary native buffer for static buffer '{}'",
                        buffer.getBufferName());
            } else if (buffer instanceof Dynamical<?> && tempNativeBuffer != null) {
                buffer.nativeBuffer.clear();
            }
        }
    }

    /**
     * Appends data to the end of the buffer.
     *
     * @param arr the data array to append
     * @throws IllegalArgumentException if the input array is null
     * @throws IllegalStateException if the write operation fails
     */
    default void add(Object arr) {
        @SuppressWarnings("unchecked")
        T buffer = (T) this;
        if (arr == null) {
            String message = String.format("Input array cannot be null for buffer '%s'",
                    buffer.getBufferName());
            logger.error(message);
            throw new IllegalArgumentException(message);
        }

        logger.debug("Adding data to the end of buffer '{}' at position {}",
                buffer.getBufferName(), buffer.size);
        write(arr, buffer.size);
    }

    /**
     * Removes one element at the specified index.
     *
     * @param index the position from which to remove the element
     * @throws IllegalArgumentException if the index is invalid
     * @throws IllegalStateException if the remove operation fails
     */
    default void remove(int index) {
        remove(index, 1);
    }

    /**
     * Removes specified number of elements starting at the given index.
     * For dynamic buffers, also checks if buffer should be shrunk after removal.
     *
     * @param index starting position from which to remove elements
     * @param num number of elements to remove
     * @throws IllegalArgumentException if the index or number is invalid
     * @throws IllegalStateException if the remove operation fails
     */
    default void remove(int index, int num) {
        @SuppressWarnings("unchecked")
        T buffer = (T) this;

        if (index < 0) {
            String message = String.format("Index cannot be negative: %d for buffer '%s'",
                    index, buffer.getBufferName());
            logger.error(message);
            throw new IllegalArgumentException(message);
        }

        if (num <= 0) {
            String message = String.format("Number of elements to remove must be positive: %d for buffer '%s'",
                    num, buffer.getBufferName());
            logger.error(message);
            throw new IllegalArgumentException(message);
        }

        if (index + num > buffer.size) {
            String message = String.format(
                    "Cannot remove elements beyond buffer size: index=%d, count=%d, size=%d for buffer '%s'",
                    index, num, buffer.size, buffer.getBufferName());
            logger.error(message);
            throw new IllegalArgumentException(message);
        }

        int dataSize = buffer.dataProcessorObject.getSizeStruct();
        logger.debug("Removing {} elements from buffer '{}' at index {}",
                num, buffer.getBufferName(), index);

        int errorCode = CopyDataBufferToBuffer.copyData(
                buffer.context,
                buffer.clBuffer,
                buffer.clBuffer,
                (index + num) * dataSize,
                index * dataSize,
                (buffer.size - index - num) * dataSize);

        if (errorCode != CL10.CL_SUCCESS) {
            String message = String.format(
                    "Failed to remove elements from buffer '%s': OpenCL error code %d",
                    buffer.getBufferName(), errorCode);
            logger.error(message);
            throw new IllegalStateException(message);
        }

        buffer.size -= num;
        logger.debug("Successfully removed elements from buffer '{}', new size: {}",
                buffer.getBufferName(), buffer.size);

        if (buffer instanceof Dynamical<?>) {
            Dynamical<?> dynamical = (Dynamical<?>) this;
            double capacityRatio = (double) buffer.capacity / buffer.size;

            if (capacityRatio > dynamical.getShrinkFactor()) {
                logger.debug("Buffer '{}' capacity ratio ({}) exceeds shrink factor ({}), resizing...",
                        buffer.getBufferName(), capacityRatio, dynamical.getShrinkFactor());

                int newCapacity = (int) Math.ceil(buffer.size * dynamical.getCapacityMultiplier());
                newCapacity = Math.max(newCapacity, dynamical.getMinCapacity());

                logger.debug("Shrinking buffer '{}' capacity from {} to {}",
                        buffer.getBufferName(), buffer.capacity, newCapacity);

                dynamical.resize(newCapacity);
            }
        }

    }
}
