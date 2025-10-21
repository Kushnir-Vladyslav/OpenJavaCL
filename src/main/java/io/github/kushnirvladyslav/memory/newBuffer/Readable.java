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

import io.github.kushnirvladyslav.exceptions.BufferOperationException;
import io.github.kushnirvladyslav.memory.data.DataProcessor;
import io.github.kushnirvladyslav.memory.data.FromByteBuffer;
import io.github.kushnirvladyslav.util.OpenCLErrorUtils;
import io.github.kushnirvladyslav.util.clEvent.ClCustomEvent;
import io.github.kushnirvladyslav.util.clEvent.ClEvent;
import io.github.kushnirvladyslav.util.clEvent.ClEventList;
import org.lwjgl.PointerBuffer;
import org.lwjgl.opencl.CL10;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;

public interface Readable<T extends CopyableGlobalBuffer & Readable<T>> {
    Logger logger = LoggerFactory.getLogger(Readable.class);


    @SuppressWarnings("unchecked")
    default ClEvent readAsync(int offset, ClEventList events, Object targetArray){
        T buffer = (T) this;

        buffer.checkNotClosed();

        DataProcessor dataProcessor = buffer.dataProcessor;

        if(offset < 0) {
            String message = String.format(
                    "To read data from a buffer, the offset passed cannot be negative: offset=%d, for buffer '%s'",
                    offset, buffer.getName());
            logger.error(message);
            throw new IllegalArgumentException(message);
        }

        int len = dataProcessor.getSizeArray(targetArray);

        if (offset + len > buffer.capacity) {
            String message = String.format(
                    "Attempt to read outside buffer bounds: offset=%d, length=%d, capacity=%d for buffer '%s'",
                    offset, len, buffer.capacity, buffer.getName());
            logger.error(message);
            throw new IllegalArgumentException(message);
        }

        long byteLen = (long) len * dataProcessor.getSizeStruct();
        if (byteLen > Integer.MAX_VALUE) {
            String message = String.format(
                    "Read size exceeds byte[] limit (2GB). Try to read %d byte from buffer '%s'",
                    byteLen, buffer.getName());
            logger.error(message);
            throw new IllegalArgumentException(message);
        }

        if (offset + len > buffer.size) {
            logger.info("Reading uninitialized data: offset={}, length={}, host-initiated size={} for buffer '{}'",
                    offset, len, buffer.size, buffer.getName());
        }

        try (MemoryStack stack = MemoryStack.stackPush()){
            PointerBuffer rowEvent = stack.mallocPointer(1);
            ClCustomEvent customEvent = new ClCustomEvent(buffer.context);
            ByteBuffer tempNativeBuffer;
            if(buffer.stagingBuffer == null) {
                tempNativeBuffer = MemoryUtil.memAlloc((int)byteLen);
            } else {
                tempNativeBuffer = (ByteBuffer) buffer.stagingBuffer.rewind().limit((int)byteLen).slice();
                buffer.stagingBuffer.clear();
            }

            int errorCode = CL10.clEnqueueReadBuffer(
                    buffer.context.getCommandQueue(),
                    buffer.clMem,
                    false,
                    (long) offset * dataProcessor.getSizeStruct(),
                    tempNativeBuffer,
                    events != null ? events.getEventList(stack) : null,
                    rowEvent
            );

            if( events != null) {
                events.releaseEvents();
            }

            if (!OpenCLErrorUtils.isSuccess(errorCode)) {
                if(buffer.stagingBuffer == null){
                    MemoryUtil.memFree(tempNativeBuffer);
                }
                customEvent.setComplete();
                String message = String.format(
                        "OpenCL read buffer failed for buffer '%s': error - %s",
                        buffer.getName(), OpenCLErrorUtils.getCLErrorString(errorCode));
                logger.error(message);
                throw new BufferOperationException(message, errorCode);
            }

            ClEvent thisEvent = new ClEvent(rowEvent.get(0));
            thisEvent.onComplete((long event, int status) ->{
                try {
                    if (OpenCLErrorUtils.isSuccess(status)) {
                        ((FromByteBuffer) dataProcessor).convertFromByteBuffer((ByteBuffer) tempNativeBuffer.rewind(), targetArray);
                        customEvent.setComplete();
                    } else {
                        customEvent.setError(status);
                    }
                } finally {
                    if(buffer.stagingBuffer == null){
                        MemoryUtil.memFree(tempNativeBuffer);
                    }
                }
            });

            return customEvent;
        }
    }

    @SuppressWarnings("unchecked")
    default Object readSync(int offset, int len, ClEventList events){
        T buffer = (T) this;

        buffer.checkNotClosed();

        DataProcessor dataProcessor = buffer.dataProcessor;

        if(offset < 0) {
            String message = String.format(
                    "To read data from a buffer, the offset passed cannot be negative: offset=%d, for buffer '%s'",
                    offset, buffer.getName());
            logger.error(message);
            throw new IllegalArgumentException(message);
        }

        if(len <= 0) {
            String message = String.format(
                    "To read data from a buffer, the passed data size must be positive: length=%d, for buffer '%s'",
                    len, buffer.getName());
            logger.error(message);
            throw new IllegalArgumentException(message);
        }

        if (offset + len > buffer.capacity) {
            String message = String.format(
                    "Attempt to read outside buffer bounds: offset=%d, length=%d, capacity=%d for buffer '%s'",
                    offset, len, buffer.capacity, buffer.getName());
            logger.error(message);
            throw new IllegalArgumentException(message);
        }

        long byteLen = (long) len * dataProcessor.getSizeStruct();
        if (byteLen > Integer.MAX_VALUE) {
            String message = String.format(
                    "Read size exceeds byte[] limit (2GB). Try to read %d byte from buffer '%s'",
                    byteLen, buffer.getName());
            logger.error(message);
            throw new IllegalArgumentException(message);
        }

        if (offset + len > buffer.size) {
            logger.info("Reading uninitialized data: offset={}, length={}, host-initiated size={} for buffer '{}'",
                    offset, len, buffer.size, buffer.getName());
        }

        Object targetArray = ((FromByteBuffer)dataProcessor).createArr(len);
        ByteBuffer tempNativeBuffer = null;

        try (MemoryStack stack = MemoryStack.stackPush()){

            if(buffer.stagingBuffer == null) {
                tempNativeBuffer = MemoryUtil.memAlloc((int)byteLen);
            } else {
                tempNativeBuffer = (ByteBuffer) buffer.stagingBuffer.rewind().limit((int)byteLen).slice();
                buffer.stagingBuffer.clear();
            }

            int errorCode = CL10.clEnqueueReadBuffer(
                    buffer.context.getCommandQueue(),
                    buffer.clMem,
                    true,
                    (long) offset * dataProcessor.getSizeStruct(),
                    tempNativeBuffer,
                    events != null ? events.getEventList(stack) : null,
                    null
            );

            if( events != null) {
                events.releaseEvents();
            }

            if (!OpenCLErrorUtils.isSuccess(errorCode)) {
                if(buffer.stagingBuffer == null){
                    MemoryUtil.memFree(tempNativeBuffer);
                }

                String message = String.format(
                        "OpenCL read buffer failed for buffer '%s': error - %s",
                        buffer.getName(), OpenCLErrorUtils.getCLErrorString(errorCode));
                logger.error(message);
                throw new BufferOperationException(message, errorCode);
            }

            ((FromByteBuffer) dataProcessor).convertFromByteBuffer((ByteBuffer) tempNativeBuffer.rewind(), targetArray);

            return targetArray;

        }finally {
            if(buffer.stagingBuffer == null && tempNativeBuffer != null){
                MemoryUtil.memFree(tempNativeBuffer);
            }
        }
    }

    @SuppressWarnings("unchecked")
    default ClEvent readAsyncByte(int offset, ClEventList events, byte[] targetArray){
        T buffer = (T) this;

        buffer.checkNotClosed();

        DataProcessor dataProcessor = buffer.dataProcessor;

        if(targetArray == null) {
            String message = String.format(
                    "The passed array for reading the buffer '%s', can`t be null.",
                    buffer.getName());
            logger.error(message);
            throw new NullPointerException(message);
        }

        if(offset < 0) {
            String message = String.format(
                    "To read data from a buffer, the offset passed cannot be negative: offset=%d, for buffer '%s'",
                    offset, buffer.getName());
            logger.error(message);
            throw new IllegalArgumentException(message);
        }

        int len = targetArray.length;

        if ((long)offset * dataProcessor.getSizeStruct() + len > (long)buffer.capacity * dataProcessor.getSizeStruct()) {
            String message = String.format(
                    "Attempt to read outside buffer bounds: offset=%d, length=%d by Byte, length=%d by elements, capacity=%d for buffer '%s'",
                    offset, len, (int) Math.ceil((double)len / dataProcessor.getSizeStruct()), buffer.capacity, buffer.getName());
            logger.error(message);
            throw new IllegalArgumentException(message);
        }

        if (offset + len > buffer.size) {
            logger.info("Reading uninitialized data: offset={}, length={} by Byte, length={} by elements, host-initiated size={} for buffer '{}'",
                    offset, len, (int) Math.ceil((double)len / dataProcessor.getSizeStruct()), buffer.size, buffer.getName());
        }

        if (len % dataProcessor.getSizeStruct() != 0) {
            logger.info("The size of the passed array ({}) for reading the buffer {} is not a multiple of the number of elements ({}).",
                    len, buffer.getName(), dataProcessor.getSizeStruct());
        }

        try (MemoryStack stack = MemoryStack.stackPush()){
            PointerBuffer rowEvent = stack.mallocPointer(1);
            ClCustomEvent customEvent = new ClCustomEvent(buffer.context);
            ByteBuffer tempNativeBuffer;
            if(buffer.stagingBuffer == null) {
                tempNativeBuffer = MemoryUtil.memAlloc(len);
            } else {
                tempNativeBuffer = (ByteBuffer) buffer.stagingBuffer.rewind().limit(len).slice();
                buffer.stagingBuffer.clear();
            }

            int errorCode = CL10.clEnqueueReadBuffer(
                    buffer.context.getCommandQueue(),
                    buffer.clMem,
                    false,
                    (long) offset * dataProcessor.getSizeStruct(),
                    tempNativeBuffer,
                    events != null ? events.getEventList(stack) : null,
                    rowEvent
            );

            if( events != null) {
                events.releaseEvents();
            }

            if (!OpenCLErrorUtils.isSuccess(errorCode)) {
                if(buffer.stagingBuffer == null){
                    MemoryUtil.memFree(tempNativeBuffer);
                }
                customEvent.setComplete();
                String message = String.format(
                        "OpenCL read buffer failed for buffer '%s': error - %s",
                        buffer.getName(), OpenCLErrorUtils.getCLErrorString(errorCode));
                logger.error(message);
                throw new BufferOperationException(message, errorCode);
            }

            ClEvent thisEvent = new ClEvent(rowEvent.get(0));
            thisEvent.onComplete((long event, int status) ->{
                try {
                    if (OpenCLErrorUtils.isSuccess(status)) {
                        tempNativeBuffer.rewind().get(targetArray);
                        customEvent.setComplete();
                    } else {
                        customEvent.setError(status);
                    }
                } finally {
                    if(buffer.stagingBuffer == null){
                        MemoryUtil.memFree(tempNativeBuffer);
                    }
                }
            });

            return customEvent;
        }
    }

    @SuppressWarnings("unchecked")
    default byte[] readSyncByte(int offset, int len, ClEventList events){
        T buffer = (T) this;

        buffer.checkNotClosed();

        DataProcessor dataProcessor = buffer.dataProcessor;

        if(offset < 0) {
            String message = String.format(
                    "To read data from a buffer, the offset passed cannot be negative: offset=%d, for buffer '%s'",
                    offset, buffer.getName());
            logger.error(message);
            throw new IllegalArgumentException(message);
        }

        if(len <= 0) {
            String message = String.format(
                    "To read data from a buffer, the passed data size must be positive: length=%d, for buffer '%s'",
                    len, buffer.getName());
            logger.error(message);
            throw new IllegalArgumentException(message);
        }

        long byteLen = (long) len * dataProcessor.getSizeStruct();
        if (byteLen > Integer.MAX_VALUE) {
            String message = String.format(
                    "Read size exceeds byte[] limit (2GB). Try to read %d byte from buffer '%s'",
                    byteLen, buffer.getName());
            logger.error(message);
            throw new IllegalArgumentException(message);
        }

        if (offset + len > buffer.capacity) {
            String message = String.format(
                    "Attempt to read outside buffer bounds: offset=%d, length=%d, capacity=%d for buffer '%s'",
                    offset, len, buffer.capacity, buffer.getName());
            logger.error(message);
            throw new IllegalArgumentException(message);
        }

        if (offset + len > buffer.size) {
            logger.info("Reading uninitialized data: offset={}, length={}, host-initiated size={} for buffer '{}'",
                    offset, len, buffer.size, buffer.getName());
        }

        byte[] targetArray = new byte[len * dataProcessor.getSizeStruct()];
        ByteBuffer tempNativeBuffer = null;

        try (MemoryStack stack = MemoryStack.stackPush()){
            if(buffer.stagingBuffer == null) {
                tempNativeBuffer = MemoryUtil.memAlloc((int)byteLen);
            } else {
                tempNativeBuffer = (ByteBuffer) buffer.stagingBuffer.rewind().limit((int)byteLen).slice();
                buffer.stagingBuffer.clear();
            }

            int errorCode = CL10.clEnqueueReadBuffer(
                    buffer.context.getCommandQueue(),
                    buffer.clMem,
                    true,
                    (long) offset * dataProcessor.getSizeStruct(),
                    tempNativeBuffer,
                    events != null ? events.getEventList(stack) : null,
                    null
            );

            if( events != null) {
                events.releaseEvents();
            }

            if (!OpenCLErrorUtils.isSuccess(errorCode)) {
                if(buffer.stagingBuffer == null){
                    MemoryUtil.memFree(tempNativeBuffer);
                }
                String message = String.format(
                        "OpenCL read buffer failed for buffer '%s': error - %s",
                        buffer.getName(), OpenCLErrorUtils.getCLErrorString(errorCode));
                logger.error(message);
                throw new BufferOperationException(message, errorCode);
            }

            tempNativeBuffer.rewind().get(targetArray);

            return targetArray;

        }finally {
            if(buffer.stagingBuffer == null && tempNativeBuffer != null){
                MemoryUtil.memFree(tempNativeBuffer);
            }
        }
    }
}
