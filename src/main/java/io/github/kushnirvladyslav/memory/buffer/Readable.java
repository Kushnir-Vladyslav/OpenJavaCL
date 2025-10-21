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

import io.github.kushnirvladyslav.exceptions.BufferOperationException;
import io.github.kushnirvladyslav.memory.data.DataProcessor;
import io.github.kushnirvladyslav.memory.data.FromByteBuffer;
import io.github.kushnirvladyslav.util.OpenCLErrorUtils;
import org.lwjgl.opencl.CL10;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;

/**
 * Interface that provides reading capabilities for OpenCL buffers.
 * Implements various methods for reading data from OpenCL buffer to host memory.
 *
 * <p>This interface provides a complete implementation of read operations without requiring
 * additional implementation from implementing classes. It supports both object-based
 * and byte-based reading operations with various options for offset and length.
 *
 * <p>Example usage:
 * <pre>
 * // Read entire buffer
 * Object data = buffer.read();
 *
 * // Read with specific offset and length
 * Object partialData = buffer.read(offset, length);
 *
 * // Read as bytes for dynamic buffers
 * ByteBuffer byteData = buffer.readBytes();
 * </pre>
 *
 * @param <T> The type of buffer that implements this interface, must extend AbstractGlobalBuffer
 *            and implement Readable interface
 * @author Vladyslav Kushnir
 * @since 1.0
 */
public interface Readable<T extends AbstractGlobalBuffer & Readable<T>> {
    Logger logger = LoggerFactory.getLogger(Readable.class);

    /**
     * Reads all data from the buffer using default configuration.
     * If copyHostBuffer is enabled, returns the host buffer directly,
     * otherwise creates a new array and copies the data.
     *
     * @return Object containing the read data
     * @throws IllegalArgumentException if any parameters are invalid
     * @throws BufferOperationException if the read operation fails
     */
    default Object read() {
        @SuppressWarnings("unchecked")
        T buffer = (T) this;
        logger.debug("Reading entire buffer '{}'", buffer.getBufferName());

        Object targetArray;
        if (buffer.copyHostBuffer) {
            targetArray = buffer.hostBuffer;
            logger.trace("Using host buffer directly for '{}'", buffer.getBufferName());
        } else {
            FromByteBuffer converter = (FromByteBuffer) buffer.dataProcessorObject;
            targetArray = converter.createArr(buffer.size);

            logger.trace("Created new array for buffer '{}' with size {}",
                    buffer.getBufferName(), buffer.size);
        }

        return read(0, buffer.size, targetArray);
    }

    /**
     * Reads all data from the buffer into a provided target array.
     *
     * @param targetArray The array where the data will be stored
     * @return The target array filled with data from the buffer
     * @throws IllegalArgumentException if targetArray is null
     * @throws BufferOperationException if the read operation fails
     */
    default Object read(Object targetArray) {
        @SuppressWarnings("unchecked")
        T buffer = (T) this;
        if (targetArray == null) {
            String message = String.format("Target array cannot be null for buffer '%s'",
                    buffer.getBufferName());
            logger.error(message);
            throw new IllegalArgumentException(message);
        }

        logger.debug("Reading buffer '{}' into provided array", buffer.getBufferName());
        return read(0, buffer.size, targetArray);
    }

    /**
     * Reads data from the buffer starting at specified offset.
     * Creates a new array to store the data.
     *
     * @param offset Starting position in the buffer to read from
     * @return New array containing the read data
     * @throws IllegalArgumentException if offset is negative or beyond buffer size
     * @throws BufferOperationException if the read operation fails
     */
    default Object read(int offset) {
        @SuppressWarnings("unchecked")
        T buffer = (T) this;
        if (offset < 0) {
            String message = String.format("Offset cannot be negative: %d for buffer '%s'",
                    offset, buffer.getBufferName());
            logger.error(message);
            throw new IllegalArgumentException(message);
        }

        FromByteBuffer converter = (FromByteBuffer) buffer.dataProcessorObject;
        Object targetArray = converter.createArr(buffer.size - offset);

        logger.debug("Reading buffer '{}' from offset {} into new array",
                buffer.getBufferName(), offset);

        return read(offset, buffer.size, targetArray);
    }

    /**
     * Reads data from the buffer starting at specified offset into provided target array.
     *
     * @param offset      Starting position in the buffer to read from
     * @param targetArray The array where the data will be stored
     * @return The target array filled with data from the buffer
     * @throws IllegalArgumentException if offset is negative or targetArray is null
     * @throws BufferOperationException if the read operation fails
     */
    default Object read(int offset, Object targetArray) {
        @SuppressWarnings("unchecked")
        T buffer = (T) this;
        if (targetArray == null) {
            String message = String.format("Target array cannot be null for buffer '%s'",
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

        logger.debug("Reading buffer '{}' from offset {} into provided array",
                buffer.getBufferName(), offset);
        return read(offset, buffer.size, targetArray);
    }

    /**
     * Reads specified amount of data from the buffer starting at given offset.
     * Creates a new array to store the data.
     *
     * @param offset Starting position in the buffer to read from
     * @param len    Number of elements to read
     * @return New array containing the read data
     * @throws IllegalArgumentException if offset or length parameters are invalid
     * @throws BufferOperationException if the read operation fails
     */
    default Object read(int offset, int len) {
        @SuppressWarnings("unchecked")
        T buffer = (T) this;
        if (offset < 0) {
            String message = String.format("Offset cannot be negative: %d for buffer '%s'",
                    offset, buffer.getBufferName());
            logger.error(message);
            throw new IllegalArgumentException(message);
        }
        if (len <= 0) {
            String message = String.format("Length must be positive: %d for buffer '%s'",
                    len, buffer.getBufferName());
            logger.error(message);
            throw new IllegalArgumentException(message);
        }

        FromByteBuffer converter = (FromByteBuffer) buffer.dataProcessorObject;
        Object targetArray = converter.createArr(len - offset);

        logger.debug("Reading {} elements from buffer '{}' starting at offset {}",
                len, buffer.getBufferName(), offset);
        return read(offset, len, targetArray);
    }

    /**
     * Main implementation of read operation. Reads specified amount of data
     * from the buffer starting at given offset into provided target array.
     *
     * @param offset      Starting position in the buffer to read from
     * @param len         Number of elements to read
     * @param targetArray The array where the data will be stored
     * @return The target array filled with data from the buffer
     * @throws IllegalArgumentException if any parameters are invalid
     * @throws BufferOperationException if the read operation fails
     */
    default Object read(int offset, int len, Object targetArray) {
        @SuppressWarnings("unchecked")
        T buffer = (T) this;

        if (offset + len > buffer.capacity) {
            String message = String.format(
                    "Attempt to read outside buffer bounds: offset=%d, length=%d, capacity=%d for buffer '%s'",
                    offset, len, buffer.capacity, buffer.getBufferName());
            logger.error(message);
            throw new IllegalArgumentException(message);
        }

        if (offset + len > buffer.size) {
            logger.warn("Reading uninitialized dataProcessor: offset={}, length={}, size={} for buffer '{}'",
                    offset, len, buffer.size, buffer.getBufferName());
        }

        DataProcessor dataProcessor = buffer.dataProcessorObject;
        FromByteBuffer converter = (FromByteBuffer) dataProcessor;
        ByteBuffer tempNativeBuffer = null;

        try {
            if (buffer.copyHostBuffer) {
                tempNativeBuffer = (ByteBuffer) buffer.nativeBuffer.rewind().limit(len);
            } else {
                tempNativeBuffer = MemoryUtil.memAlloc(len * dataProcessor.getSizeStruct());
                if (tempNativeBuffer == null) {
                    throw new IllegalArgumentException("Failed to allocate temporary native buffer");
                }
            }

            logger.debug("Reading {} elements from OpenCL buffer '{}' at offset {}",
                    len, buffer.getBufferName(), offset);

            int errorCode = CL10.clEnqueueReadBuffer(
                    buffer.context.getCommandQueue(),
                    buffer.clBuffer,
                    true,
                    offset * dataProcessor.getSizeStruct(),
                    tempNativeBuffer,
                    null,
                    null
            );

            if (errorCode != CL10.CL_SUCCESS) {
                String message = String.format(
                        "OpenCL read buffer failed for buffer '%s': error - %s",
                        buffer.getBufferName(), OpenCLErrorUtils.getCLErrorString(errorCode));
                logger.error(message);
                throw new BufferOperationException(message, errorCode);
            }

            converter.convertFromByteBuffer((ByteBuffer) tempNativeBuffer.rewind(), targetArray);

            if (buffer.copyHostBuffer) {
                buffer.nativeBuffer.clear();
            }

            return targetArray;

        } catch (Exception e) {
            String message = String.format("Failed to read from buffer '%s'", buffer.getBufferName());
            logger.error(message, e);
            throw new BufferOperationException(message, e);
        } finally {
            if (!buffer.copyHostBuffer && tempNativeBuffer != null) {
                MemoryUtil.memFree(tempNativeBuffer);
            }
        }
    }

    /**
     * Reads all data from the buffer as bytes.
     * This operation is only available for dynamic buffers.
     *
     * @return ByteBuffer containing the read data
     * @throws BufferOperationException if the buffer is not dynamic
     * @throws IllegalArgumentException if any parameters are invalid
     * @throws BufferOperationException if the read operation fails
     */
    default ByteBuffer readBytes() {
        @SuppressWarnings("unchecked")
        T buffer = (T) this;
        if (!(buffer instanceof Dynamical<?>)) {
            String message = String.format(
                    "Buffer '%s' is not dynamic, cannot perform byte-based read operation",
                    buffer.getBufferName());
            logger.error(message);
            throw new BufferOperationException(message);
        }

        logger.debug("Reading entire buffer '{}' as bytes", buffer.getBufferName());
        return readBytes(0, buffer.nativeBuffer);
    }

    /**
     * Reads all data from the buffer into provided ByteBuffer.
     *
     * @param tempNativeBuffer The ByteBuffer where the data will be stored
     * @return The provided ByteBuffer filled with data from the buffer
     * @throws IllegalArgumentException if tempNativeBuffer is null
     * @throws BufferOperationException if the read operation fails
     */
    default ByteBuffer readBytes(ByteBuffer tempNativeBuffer) {
        if (tempNativeBuffer == null) {
            String message = "Temporary native buffer cannot be null";
            logger.error(message);
            throw new IllegalArgumentException(message);
        }

        logger.debug("Reading buffer into provided ByteBuffer");
        return readBytes(0, tempNativeBuffer);
    }

    /**
     * Reads data from the buffer starting at specified offset as bytes.
     *
     * @param offset Starting position in the buffer to read from
     * @return ByteBuffer containing the read data
     * @throws IllegalStateException    if the buffer is not dynamic
     * @throws IllegalArgumentException if offset is invalid
     * @throws BufferOperationException if the read operation fails
     */
    default ByteBuffer readBytes(int offset) {
        @SuppressWarnings("unchecked")
        T buffer = (T) this;
        if (!(buffer instanceof Dynamical<?>)) {
            String message = String.format(
                    "Buffer '%s' is not dynamic, cannot perform byte-based read operation",
                    buffer.getBufferName());
            logger.error(message);
            throw new IllegalStateException(message);
        }

        if (offset < 0) {
            String message = String.format("Offset cannot be negative: %d for buffer '%s'",
                    offset, buffer.getBufferName());
            logger.error(message);
            throw new IllegalArgumentException(message);
        }

        int len = (buffer.size - offset)
                * buffer.dataProcessorObject.getSizeStruct();
        ByteBuffer tempNativeBuffer = (ByteBuffer) buffer
                .nativeBuffer
                .position(0)
                .limit(len);

        tempNativeBuffer = tempNativeBuffer.slice();

        buffer.nativeBuffer.clear();

        logger.debug("Reading buffer '{}' from offset {} as bytes", buffer.getBufferName(), offset);
        return readBytes(offset, tempNativeBuffer);
    }

    /**
     * Reads data from the buffer starting at specified offset into provided ByteBuffer.
     *
     * @param offset           Starting position in the buffer to read from
     * @param tempNativeBuffer The ByteBuffer where the data will be stored
     * @return The provided ByteBuffer filled with data from the buffer
     * @throws IllegalArgumentException if any parameters are invalid
     * @throws BufferOperationException if the read operation fails
     */
    default ByteBuffer readBytes(int offset, ByteBuffer tempNativeBuffer) {
        @SuppressWarnings("unchecked")
        T buffer = (T) this;
        DataProcessor dataProcessor = buffer.dataProcessorObject;

        if (tempNativeBuffer == null) {
            String message = String.format("Temporary native buffer cannot be null for buffer '%s'",
                    buffer.getBufferName());
            logger.error(message);
            throw new IllegalArgumentException(message);
        }

        int elementCount = tempNativeBuffer.capacity() / dataProcessor.getSizeStruct();
        if (offset + elementCount > buffer.capacity) {
            String message = String.format(
                    "Attempt to read outside buffer bounds: offset=%d, elements=%d, capacity=%d for buffer '%s'",
                    offset, elementCount, buffer.capacity, buffer.getBufferName());
            logger.error(message);
            throw new IllegalArgumentException(message);
        }

        if (offset + elementCount > buffer.size) {
            logger.warn("Reading uninitialized dataProcessor: offset={}, elements={}, size={} for buffer '{}'",
                    offset, elementCount, buffer.size, buffer.getBufferName());
        }

        try {
            logger.debug("Reading {} bytes from buffer '{}' at offset {}",
                    tempNativeBuffer.capacity(), buffer.getBufferName(), offset);

            int errorCode = CL10.clEnqueueReadBuffer(
                    buffer.context.getCommandQueue(),
                    buffer.clBuffer,
                    true,
                    offset * dataProcessor.getSizeStruct(),
                    (ByteBuffer) tempNativeBuffer.rewind(),
                    null,
                    null
            );

            if (errorCode != CL10.CL_SUCCESS) {
                String message = String.format(
                        "OpenCL read buffer failed for buffer '%s': error - %s",
                        buffer.getBufferName(), OpenCLErrorUtils.getCLErrorString(errorCode));
                logger.error(message);
                throw new BufferOperationException(message, errorCode);
            }

            return tempNativeBuffer;
        } catch (Exception e) {
            String message = String.format("Failed to read bytes from buffer '%s'",
                    buffer.getBufferName());
            logger.error(message, e);
            throw new BufferOperationException(message, e);
        }
    }
}
