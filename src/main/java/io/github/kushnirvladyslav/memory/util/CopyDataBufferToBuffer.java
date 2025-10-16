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

package io.github.kushnirvladyslav.memory.util;

import io.github.kushnirvladyslav.ClContext;
import io.github.kushnirvladyslav.memory.buffer.AbstractGlobalBuffer;
import org.lwjgl.opencl.CL10;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for copying data between OpenCL buffers.
 * Provides functionality to safely copy data from one OpenCL buffer to another.
 *
 * @since 1.0
 * @author Vladyslav Kushnir
 */
public class CopyDataBufferToBuffer {
    private static final Logger logger = LoggerFactory.getLogger(CopyDataBufferToBuffer.class);

    /**
     * Copies data between two OpenCL buffers.
     *
     * @param clContext the OpenCL context
     * @param src the source buffer
     * @param dst the destination buffer
     * @param size the number of bytes to copy
     * @return OpenCL error code
     * @throws IllegalArgumentException if any parameters are invalid
     */
    public static int copyData (ClContext clContext, AbstractGlobalBuffer src, AbstractGlobalBuffer dst, long size) {
        return copyData(clContext, src, dst, 0, 0, size);
    }

    /**
     * Copies data between two OpenCL buffers with offset support.
     *
     * @param clContext the OpenCL context
     * @param src the source buffer
     * @param dst the destination buffer
     * @param srcOffset offset in the source buffer
     * @param dstOffset offset in the destination buffer
     * @param size the number of bytes to copy
     * @return OpenCL error code
     * @throws IllegalArgumentException if any parameters are invalid
     */
    public static int copyData (ClContext clContext, AbstractGlobalBuffer src, AbstractGlobalBuffer dst, long srcOffset, long dstOffset, long size) {
        if (clContext == null || src == null || dst == null) {
            String message = "OpenCL context, source buffer, and destination buffer cannot be null";
            logger.error(message);
            throw new IllegalArgumentException(message);
        }

        if (size <= 0) {
            String message = String.format("Copy size must be positive, got %d", size);
            logger.error(message);
            throw new IllegalArgumentException(message);
        }

        if (srcOffset < 0 || dstOffset < 0) {
            String message = String.format("Offsets must be non-negative, got src=%d, dst=%d",
                    srcOffset, dstOffset);
            logger.error(message);
            throw new IllegalArgumentException(message);
        }

        if (src.getDataClass().equals(dst.getDataClass())) {
            logger.warn("Unsafe copy operation: Data type mismatch between buffers '{}' and '{}'",
                    src.getBufferName(), dst.getBufferName());
        }

        logger.debug("Copying {} bytes from buffer '{}' to '{}' (offsets: src={}, dst={})",
                size, src.getBufferName(), dst.getBufferName(), srcOffset, dstOffset);

        return CL10.clEnqueueCopyBuffer(
                clContext.getCommandQueue(),
                src.getClBuffer(),
                dst.getClBuffer(),
                srcOffset,
                dstOffset,
                size,
                null,
                null
        );
    }

    /**
     * Copies data between two OpenCL memory objects using their handles.
     *
     * @param clContext the OpenCL context
     * @param src source memory object handle
     * @param dst destination memory object handle
     * @param size the number of bytes to copy
     * @return OpenCL error code
     * @throws IllegalArgumentException if any parameters are invalid
     */
    public static int copyData (ClContext clContext, long src, long dst, int size) {
        return copyData(clContext, src, dst, 0, 0, size);
    }

    /**
     * Copies data between two OpenCL memory objects using their handles with offset support.
     *
     * @param clContext the OpenCL context
     * @param src source memory object handle
     * @param dst destination memory object handle
     * @param srcOffset offset in the source buffer
     * @param dstOffset offset in the destination buffer
     * @param size the number of bytes to copy
     * @return OpenCL error code
     * @throws IllegalArgumentException if any parameters are invalid
     */
    public static int copyData (ClContext clContext, long src, long dst, int srcOffset, long dstOffset, long size) {
        if (clContext == null) {
            String message = "OpenCL context cannot be null";
            logger.error(message);
            throw new IllegalArgumentException(message);
        }

        if (src == 0 || dst == 0) {
            String message = String.format("Invalid memory object handles: src=%d, dst=%d", src, dst);
            logger.error(message);
            throw new IllegalArgumentException(message);
        }

        if (size <= 0) {
            String message = String.format("Copy size must be positive, got %d", size);
            logger.error(message);
            throw new IllegalArgumentException(message);
        }

        if (srcOffset < 0 || dstOffset < 0) {
            String message = String.format("Offsets must be non-negative, got src=%d, dst=%d",
                    srcOffset, dstOffset);
            logger.error(message);
            throw new IllegalArgumentException(message);
        }

        logger.debug("Copying {} bytes between memory objects (src={}, dst={}, offsets: src={}, dst={})",
                size, src, dst, srcOffset, dstOffset);

        return CL10.clEnqueueCopyBuffer(
                clContext.getCommandQueue(),
                src,
                dst,
                srcOffset,
                dstOffset,
                size,
                null,
                null
        );
    }
}
