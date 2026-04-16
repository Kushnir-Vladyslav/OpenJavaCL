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

package io.github.kushnirvladyslav.memory.newBuffer;

import io.github.kushnirvladyslav.exceptions.BufferIndexOutOfBoundsException;
import io.github.kushnirvladyslav.exceptions.BufferOperationException;
import io.github.kushnirvladyslav.util.OpenCLErrorUtils;
import io.github.kushnirvladyslav.util.clEvent.ClEvent;
import io.github.kushnirvladyslav.util.clEvent.ClEventList;
import org.lwjgl.PointerBuffer;
import org.lwjgl.opencl.CL10;
import org.lwjgl.system.MemoryStack;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class CopyableGlobalBuffer
        extends GlobalBuffer {
    private static final Logger logger = LoggerFactory.getLogger(CopyableGlobalBuffer.class);

    protected CopyableGlobalBuffer(CopyableGlobalBufferBuilder<?, ?> builder) {
        super(builder);
    }

    public ClEvent copyFrom (GlobalBuffer src){
        return copyFrom(src, null);
    }

    public ClEvent copyFrom (GlobalBuffer src, ClEventList events){
        if (src == null || src.isClosed()) {
            String message = "Buffer, source not initialized or already closed.";
            logger.error(message);
            throw new BufferOperationException(message);
        }

        return copyFrom(src, src.capacity, events);
    }

    public ClEvent copyFrom (GlobalBuffer src, int size){
        return copyFrom(src, 0, 0, size, null);
    }

    public ClEvent copyFrom (GlobalBuffer src, int size, ClEventList events){
        return copyFrom(src, 0, 0, size, events);
    }

    public ClEvent copyFrom (GlobalBuffer src, int srcOffset, int dstOffset, int size){
        return copyFromBufferToBuffer(src, this, srcOffset, dstOffset, size, null);
    }

    public ClEvent copyFrom (GlobalBuffer src, int srcOffset, int dstOffset, int size, ClEventList events){
        return copyFromBufferToBuffer(src, this, srcOffset, dstOffset, size, events);
    }

    public ClEvent copyTo (GlobalBuffer dst){
        return copyTo(dst, null);
    }

    public ClEvent copyTo (GlobalBuffer dst, ClEventList events){
        if (this.isClosed()) {
            String message = "Buffer, source not initialized or already closed.";
            logger.error(message);
            throw new BufferOperationException(message);
        }

        return copyTo(dst, this.capacity, events);
    }

    public ClEvent copyTo (GlobalBuffer dst, int size){
        return copyTo(dst, 0, 0, size, null);
    }

    public ClEvent copyTo (GlobalBuffer dst, int size, ClEventList events){
        return copyTo(dst, 0, 0, size, events);
    }

    public ClEvent copyTo (GlobalBuffer dst, int srcOffset, int dstOffset, int size){
        return copyFromBufferToBuffer(this, dst, srcOffset, dstOffset, size, null);
    }

    public ClEvent copyTo (GlobalBuffer dst, int srcOffset, int dstOffset, int size, ClEventList events){
        return copyFromBufferToBuffer(this, dst, srcOffset, dstOffset, size, events);
    }

    protected ClEvent copyFromBufferToBuffer(
            GlobalBuffer src, GlobalBuffer dst,
            int srcOffset, int dstOffset,
            int size, ClEventList events) {

        if (src == null || src.isClosed()) {
            String message = "Buffer, source not initialized or already closed.";
            logger.error(message);
            throw new BufferOperationException(message);
        }

        if (dst == null || dst.isClosed()) {
            String message = "Buffer, destination not initialized or already closed.";
            logger.error(message);
            throw new BufferOperationException(message);
        }

        if (!src.inSameContext(dst.context)){
            String message = "Buffers are created in different OpenCl contexts.";
            logger.error(message);
            throw new BufferOperationException(message);
        }

        if (!src.dataProcessor.getClass().equals(dst.dataProcessor.getClass())){
            String message = String.format(
                    "Buffer data types mismatch: source is %s, destination is %s. " +
                            "This will likely cause data corruption or undefined behavior.",
                    src.dataProcessor.getClass().getSimpleName(),
                    dst.dataProcessor.getClass().getSimpleName()
            );
            logger.error(message);
            throw new BufferOperationException(message);
        }

        if (size < 0 || srcOffset < 0 || dstOffset < 0) {
            String message = "Size and offsets must be non-negative.";
            logger.error(message);
            throw new BufferOperationException(message);
        }

        if (src.capacity < srcOffset + size) {
            String message = String.format(
                    "Attempt to read outside the source buffer while copying. Buffer capacity '%s' is %d, attempted to write to %d.",
                    src.name, src.capacity, srcOffset + size);
            logger.error(message);
            throw new BufferOperationException(message);
        }

        if (dst.capacity < dstOffset + size) {
            try{
                changeCapacity(dstOffset + size, events);
            }
            catch (BufferOperationException e) {
                String message = String.format(
                        "Attempted to write outside destination buffer while copying. Buffer capacity s is d, attempted to write to d.",
                        dst.name, dst.capacity, dstOffset + size);
                logger.error(message);
                throw new BufferIndexOutOfBoundsException(message);
            }
        }

        try(MemoryStack stack = MemoryStack.stackPush()){
            PointerBuffer thisEvent = stack.mallocPointer(1);

            int dataSize = src.dataProcessor.getSizeStruct();

            int errorCode = CL10.clEnqueueCopyBuffer(
                    src.context.getCommandQueue(),
                    src.clMem,
                    dst.clMem,
                    (long) srcOffset * dataSize,
                    (long) dstOffset * dataSize,
                    (long) size * dataSize,
                    (events == null) ? null : events.getEventList(stack),
                    thisEvent.rewind()
            );

            if (events != null) events.releaseEvents();

            if (!OpenCLErrorUtils.isSuccess(errorCode)) {
                String message = String.format(
                        "Copying from buffer '%S' to '%s' ended with an error: %s",
                        src.name, dst.name, OpenCLErrorUtils.getCLErrorString(errorCode));
                logger.error(message);
                throw new BufferOperationException(message);
            }

            return new ClEvent(thisEvent.get(0));
        }
    }
}
