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

        try (MemoryStack stack = MemoryStack.stackPush()){
            PointerBuffer rowEvent = stack.mallocPointer(1);
            ClCustomEvent customEvent = new ClCustomEvent(buffer.context);
            ByteBuffer tempNativeBuffer = MemoryUtil.memAlloc((int)byteLen);

            int errorCode = CL10.clEnqueueReadBuffer(
                    buffer.context.getCommandQueue(),
                    buffer.clMem,
                    false,
                    (long) offset * dataProcessor.getSizeStruct(),
                    tempNativeBuffer,
                    events != null ? events.getEventList(stack) : null,
                    rowEvent
            );

            if (events != null) {
                events.releaseEvents();
            }

            if (!OpenCLErrorUtils.isSuccess(errorCode)) {
                MemoryUtil.memFree(tempNativeBuffer);

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
                    MemoryUtil.memFree(tempNativeBuffer);
                }
            });

            return customEvent;
        }
    }

    default ClEvent readAsync(int offset, Object targetArray){
        return readAsync(offset, null, targetArray);
    }

    default ClEvent readAsync(ClEventList events, Object targetArray){
        return readAsync(0, events, targetArray);
    }

    default ClEvent readAsync(Object targetArray){
        return readAsync(0, null, targetArray);
    }

    @SuppressWarnings("unchecked")
    default ClEvent readNextAsync(ClEventList events, Object targetArray){
        T buffer = (T) this;

        buffer.checkNotClosed();

        DataProcessor dataProcessor = buffer.dataProcessor;

        int len = dataProcessor.getSizeArray(targetArray);
        int offset = buffer.pointer;

        if (offset + len > buffer.capacity) {
            String message = String.format(
                    "Attempt to read outside buffer bounds: offset=%d, length=%d, capacity=%d for buffer '%s'",
                    offset, len, buffer.capacity, buffer.getName());
            logger.error(message);
            throw new IllegalArgumentException(message);
        }

        buffer.pointer += len;
        return readAsync(offset, events, targetArray);
    }

    default ClEvent readNextAsync(Object targetArray){
        return readNextAsync(null, targetArray);
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

    default Object readSync(int offset, int len){
        return readSync(offset, len, null);
    }

    default Object readSync(int len, ClEventList events){
        return readSync(0, len, events);
    }

    default Object readSync(int len){
        return readSync(0, len, null);
    }

    @SuppressWarnings("unchecked")
    default Object readNextSync(int len, ClEventList events){
        T buffer = (T) this;

        buffer.checkNotClosed();

        DataProcessor dataProcessor = buffer.dataProcessor;

        int offset = buffer.pointer;

        if (offset + len > buffer.capacity) {
            String message = String.format(
                    "Attempt to read outside buffer bounds: offset=%d, length=%d, capacity=%d for buffer '%s'",
                    offset, len, buffer.capacity, buffer.getName());
            logger.error(message);
            throw new IllegalArgumentException(message);
        }

        buffer.pointer += len;
        return readSync(offset, len, events);
    }

    default Object readNextSync(ClEventList events){
        return readNextSync(1, events);
    }

    default Object readNextSync(int len){
        return readNextSync(len, null);
    }

    default Object readNextSync(){
        return readNextSync(1, null);
    }

    @SuppressWarnings("unchecked")
    default void readSync(int offset, ClEventList events, Object targetArray){
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
        }finally {
            if(buffer.stagingBuffer == null && tempNativeBuffer != null){
                MemoryUtil.memFree(tempNativeBuffer);
            }
        }
    }

    default void readSync(ClEventList events, Object targetArray){
        readSync(0, events, targetArray);
    }

    default void readSync(int offset, Object targetArray){
        readSync(offset, null, targetArray);
    }

    default void readSync(Object targetArray){
        readSync(0, null, targetArray);
    }

    @SuppressWarnings("unchecked")
    default void readNextSync(ClEventList events, Object targetArray){
        T buffer = (T) this;

        buffer.checkNotClosed();

        DataProcessor dataProcessor = buffer.dataProcessor;
        int len = dataProcessor.getSizeArray(targetArray);
        int offset = buffer.pointer;

        if (offset + len > buffer.capacity) {
            String message = String.format(
                    "Attempt to read outside buffer bounds: offset=%d, length=%d, capacity=%d for buffer '%s'",
                    offset, len, buffer.capacity, buffer.getName());
            logger.error(message);
            throw new IllegalArgumentException(message);
        }

        buffer.pointer += len;
        readSync(offset, events, targetArray);
    }

    default void readNextSync(Object targetArray){
        readNextSync(null, targetArray);
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

        if (len % dataProcessor.getSizeStruct() != 0) {
            logger.warn("The size of the passed array ({}) for reading the buffer {} is not a multiple of the number of elements ({}).",
                    len, buffer.getName(), dataProcessor.getSizeStruct());
        }

        try (MemoryStack stack = MemoryStack.stackPush()){
            PointerBuffer rowEvent = stack.mallocPointer(1);
            ClCustomEvent customEvent = new ClCustomEvent(buffer.context);
            ByteBuffer tempNativeBuffer = MemoryUtil.memAlloc(len);

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
                MemoryUtil.memFree(tempNativeBuffer);

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
                    MemoryUtil.memFree(tempNativeBuffer);
                }
            });

            return customEvent;
        }
    }

    default ClEvent readAsyncByte(int offset, byte[] targetArray){
        return readAsyncByte(offset, null, targetArray);
    }

    @SuppressWarnings("unchecked")
    default ClEvent readNextAsyncByte(ClEventList events, byte[] targetArray){
        T buffer = (T) this;

        buffer.checkNotClosed();

        DataProcessor dataProcessor = buffer.dataProcessor;

        int len = targetArray.length;
        int offset = buffer.pointer;
        int structureSize = dataProcessor.getSizeStruct();

        if ((long)offset * structureSize + len > (long)buffer.capacity * structureSize) {
            String message = String.format(
                    "Attempt to read outside buffer bounds: offset=%d, length=%d by Byte, length=%d by elements, capacity=%d for buffer '%s'",
                    offset, len, (int) Math.ceil((double)len / structureSize), buffer.capacity, buffer.getName());
            logger.error(message);
            throw new IllegalArgumentException(message);
        }

        buffer.pointer += len / structureSize;
        return readAsyncByte(offset, events, targetArray);
    }

    default ClEvent readNextAsyncByte(byte[] targetArray){
        return readNextAsyncByte(null, targetArray);
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

    default byte[] readSyncByte(int offset, int len){
        return readSyncByte(offset, len, null);
    }

    @SuppressWarnings("unchecked")
    default byte[] readNextSyncByte(int len, ClEventList events){
        T buffer = (T) this;

        buffer.checkNotClosed();

        DataProcessor dataProcessor = buffer.dataProcessor;

        int offset = buffer.pointer;
        int structureSize = dataProcessor.getSizeStruct();

        if ((long)(offset + len) * structureSize  > (long)buffer.capacity * structureSize) {
            String message = String.format(
                    "Attempt to read outside buffer bounds: offset=%d, length=%d by Byte, length=%d by elements, capacity=%d for buffer '%s'",
                    offset, len, (int) Math.ceil((double)len / structureSize), buffer.capacity, buffer.getName());
            logger.error(message);
            throw new IllegalArgumentException(message);
        }

        buffer.pointer += len / structureSize;
        return readSyncByte(offset, len, events);
    }

    default byte[] readNextSyncByte(ClEventList events){
        return readNextSyncByte(1, events);
    }

    default byte[] readNextSyncByte(int len){
        return readNextSyncByte(len, null);
    }

    default byte[] readNextSyncByte(){
        return readNextSyncByte(1, null);
    }

    @SuppressWarnings("unchecked")
    default void readSyncByte(int offset, ClEventList events, byte[] targetArray){
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

        if (len % dataProcessor.getSizeStruct() != 0) {
            logger.warn("The size of the passed array ({}) for reading the buffer {} is not a multiple of the number of elements ({}).",
                    len, buffer.getName(), dataProcessor.getSizeStruct());
        }

        ByteBuffer tempNativeBuffer = null;

        try (MemoryStack stack = MemoryStack.stackPush()){
            if(buffer.stagingBuffer == null) {
                tempNativeBuffer = MemoryUtil.memAlloc(len);
            } else {
                tempNativeBuffer = (ByteBuffer) buffer.stagingBuffer.rewind().limit(len).slice();
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
        }finally {
            if(buffer.stagingBuffer == null && tempNativeBuffer != null){
                MemoryUtil.memFree(tempNativeBuffer);
            }
        }
    }

    default void readSyncByte(int offset, byte[] targetArray){
        readSyncByte(offset, null, targetArray);
    }

    default void readSyncByte(ClEventList events, byte[] targetArray){
        readSyncByte(0, events, targetArray);
    }

    default void readSyncByte(byte[] targetArray){
        readSyncByte(0, null, targetArray);
    }

    @SuppressWarnings("unchecked")
    default void readNextSyncByte(ClEventList events, byte[] targetArray){
        T buffer = (T) this;

        buffer.checkNotClosed();

        DataProcessor dataProcessor = buffer.dataProcessor;

        int len = targetArray.length;
        int offset = buffer.pointer;
        int structureSize = dataProcessor.getSizeStruct();

        if ((long)offset * structureSize+ len > (long)buffer.capacity * structureSize) {
            String message = String.format(
                    "Attempt to read outside buffer bounds: offset=%d, length=%d by Byte, length=%d by elements, capacity=%d for buffer '%s'",
                    offset, len, (int) Math.ceil((double)len / structureSize), buffer.capacity, buffer.getName());
            logger.error(message);
            throw new IllegalArgumentException(message);
        }

        buffer.pointer += len / structureSize;
        readSyncByte(offset, events, targetArray);
    }

    default void readNextSyncByte(byte[] targetArray){
        readNextSyncByte(null, targetArray);
    }
}
