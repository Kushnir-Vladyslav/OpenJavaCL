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
import io.github.kushnirvladyslav.memory.data.ToByteBuffer;
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
import java.nio.ByteOrder;

public interface WritableGlobal
        <T extends CopyableGlobalBuffer & WritableGlobal<T>>{
    Logger logger = LoggerFactory.getLogger(WritableGlobal.class);

    @SuppressWarnings("unchecked")
    default ClEvent writeAsync (int offset, ClEventList events, Object array){
        T buffer = (T) this;

        buffer.checkNotClosed();

        DataProcessor dataProcessor = buffer.dataProcessor;

        if(array == null){
            String message = String.format(
                    "For write data to a buffer '%s', the array of objects cannot be null.",
                    buffer.getName());
            logger.error(message);
            throw new IllegalArgumentException(message);
        }

        if(offset < 0) {
            String message = String.format(
                    "To write data to a buffer, the offset passed cannot be negative: offset=%d, for buffer '%s'",
                    offset, buffer.getName());
            logger.error(message);
            throw new IllegalArgumentException(message);
        }

        int len = dataProcessor.getSizeArray(array);

        if (offset + len > buffer.capacity) {
            try{
                buffer.changeCapacity(offset + len, events);
            } catch (Exception e) {
                String message = String.format(
                        "Attempt to write outside buffer bounds: offset=%d, length=%d, capacity=%d for buffer '%s'",
                        offset, len, buffer.capacity, buffer.getName());
                logger.error(message, e);
                throw new IllegalArgumentException(message, e);
            }
        }

        long byteLen = (long) len * dataProcessor.getSizeStruct();
        if (byteLen > Integer.MAX_VALUE) {
            String message = String.format(
                    "Write size exceeds byte[] limit (2GB). Try to write %d byte from buffer '%s'",
                    byteLen, buffer.getName());
            logger.error(message);
            throw new IllegalArgumentException(message);
        }

        try (MemoryStack stack = MemoryStack.stackPush()){
            PointerBuffer rowEvent = stack.mallocPointer(1);
            ClCustomEvent customEvent = new ClCustomEvent(buffer.context);
            ByteBuffer tempNativeBuffer = MemoryUtil.memAlloc((int)byteLen);

            ToByteBuffer converter = (ToByteBuffer) dataProcessor;
            converter.convertToByteBuffer(tempNativeBuffer, array);
            tempNativeBuffer.rewind();

            int errorCode = CL10.clEnqueueWriteBuffer(
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
                        "OpenCL write buffer failed for buffer '%s': error - %s",
                        buffer.getName(), OpenCLErrorUtils.getCLErrorString(errorCode));
                logger.error(message);
                throw new BufferOperationException(message, errorCode);
            }

            ClEvent thisEvent = new ClEvent(rowEvent.get(0));
            thisEvent.onComplete((long event, int status) ->{
                try {
                    if (OpenCLErrorUtils.isSuccess(status)) {
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

    default ClEvent writeAsync (int offset, Object array){
        return writeAsync(offset, null, array);
    }

    default ClEvent writeAsync (ClEventList events, Object array){
        return writeAsync(0, events, array);
    }

    default ClEvent writeAsync (Object array){
        return writeAsync(0, null, array);
    }

    @SuppressWarnings("unchecked")
    default ClEvent writeNextAsync (ClEventList events, Object array){
        T buffer = (T) this;

        buffer.checkNotClosed();

        DataProcessor dataProcessor = buffer.dataProcessor;
        int len = dataProcessor.getSizeArray(array);
        int offset = buffer.pointer;

        buffer.pointer += len;

        return writeAsync(offset, events, array);
    }

    default ClEvent writeNextAsync (Object array){
        return writeNextAsync(null, array);
    }

    @SuppressWarnings("unchecked")
    default void writeSync (int offset, ClEventList events, Object array){
        T buffer = (T) this;

        buffer.checkNotClosed();

        DataProcessor dataProcessor = buffer.dataProcessor;

        if(array == null){
            String message = String.format(
                    "For write data to a buffer '%s', the array of objects cannot be null.",
                    buffer.getName());
            logger.error(message);
            throw new IllegalArgumentException(message);
        }

        if(offset < 0) {
            String message = String.format(
                    "To write data to a buffer, the offset passed cannot be negative: offset=%d, for buffer '%s'",
                    offset, buffer.getName());
            logger.error(message);
            throw new IllegalArgumentException(message);
        }

        int len = dataProcessor.getSizeArray(array);

        if (offset + len > buffer.capacity) {
            try{
                buffer.changeCapacity(offset + len, events);
            } catch (Exception e) {
                String message = String.format(
                        "Attempt to write outside buffer bounds: offset=%d, length=%d, capacity=%d for buffer '%s'",
                        offset, len, buffer.capacity, buffer.getName());
                logger.error(message, e);
                throw new IllegalArgumentException(message, e);
            }
        }

        long byteLen = (long) len * dataProcessor.getSizeStruct();
        if (byteLen > Integer.MAX_VALUE) {
            String message = String.format(
                    "Write size exceeds byte[] limit (2GB). Try to write %d byte from buffer '%s'",
                    byteLen, buffer.getName());
            logger.error(message);
            throw new IllegalArgumentException(message);
        }

        ByteBuffer tempNativeBuffer = null;

        try (MemoryStack stack = MemoryStack.stackPush()){
            if(buffer.stagingBuffer == null) {
                tempNativeBuffer = MemoryUtil.memAlloc((int)byteLen);
            } else {
                buffer.stagingBuffer.rewind().limit((int)byteLen);
                tempNativeBuffer = buffer.stagingBuffer.slice().order(ByteOrder.nativeOrder());
                buffer.stagingBuffer.clear();
            }

            ToByteBuffer converter = (ToByteBuffer) dataProcessor;
            converter.convertToByteBuffer(tempNativeBuffer, array);
            tempNativeBuffer.rewind();

            int errorCode = CL10.clEnqueueWriteBuffer(
                    buffer.context.getCommandQueue(),
                    buffer.clMem,
                    true,
                    (long) offset * dataProcessor.getSizeStruct(),
                    tempNativeBuffer,
                    events != null ? events.getEventList(stack) : null,
                    null
            );

            if (events != null) {
                events.releaseEvents();
            }

            if (!OpenCLErrorUtils.isSuccess(errorCode)) {
                if(buffer.stagingBuffer == null){
                    MemoryUtil.memFree(tempNativeBuffer);
                }

                String message = String.format(
                        "OpenCL write buffer failed for buffer '%s': error - %s",
                        buffer.getName(), OpenCLErrorUtils.getCLErrorString(errorCode));
                logger.error(message);
                throw new BufferOperationException(message, errorCode);
            }
        }finally {
            if(buffer.stagingBuffer == null && tempNativeBuffer != null){
                MemoryUtil.memFree(tempNativeBuffer);
            }
        }
    }

    default void writeSync (int offset, Object array){
        writeSync(offset, null, array);
    }

    default void writeSync (ClEventList events, Object array){
        writeSync(0, events, array);
    }

    default void writeSync (Object array){
        writeSync(0, null, array);
    }

    @SuppressWarnings("unchecked")
    default void writeNextSync (ClEventList events, Object array){
        T buffer = (T) this;

        buffer.checkNotClosed();

        DataProcessor dataProcessor = buffer.dataProcessor;
        int len = dataProcessor.getSizeArray(array);
        int offset = buffer.pointer;

        buffer.pointer += len;

        writeSync(offset, events, array);
    }

    default void writeNextSync (Object array){
        writeNextSync(null, array);
    }

    @SuppressWarnings("unchecked")
    default ClEvent writeAsyncByte (int offset, ClEventList events, byte[] array){
        T buffer = (T) this;

        buffer.checkNotClosed();

        DataProcessor dataProcessor = buffer.dataProcessor;

        if(array == null){
            String message = String.format(
                    "For write data to a buffer '%s', the array of byte cannot be null.",
                    buffer.getName());
            logger.error(message);
            throw new IllegalArgumentException(message);
        }

        if(offset < 0) {
            String message = String.format(
                    "To write data to a buffer, the offset passed cannot be negative: offset=%d, for buffer '%s'",
                    offset, buffer.getName());
            logger.error(message);
            throw new IllegalArgumentException(message);
        }

        int len = array.length;
        int structureSize = dataProcessor.getSizeStruct();

        if ((buffer.capacity - offset) * structureSize < len) {
            try{
                int newCapacity = offset + (int)Math.ceil((double) len / structureSize);
                buffer.changeCapacity(newCapacity, events);
            } catch (Exception e) {
                String message = String.format(
                        "Attempt to write outside buffer bounds: offset=%d, length=%d, capacity=%d for buffer '%s'",
                        offset, len, buffer.capacity, buffer.getName());
                logger.error(message, e);
                throw new IllegalArgumentException(message, e);
            }
        }

        if (len % dataProcessor.getSizeStruct() != 0) {
            logger.warn("The size of the passed array ({}) for writing to the buffer {} is not a multiple of the number of elements ({}).",
                    len, buffer.getName(), dataProcessor.getSizeStruct());
        }

        try (MemoryStack stack = MemoryStack.stackPush()){
            PointerBuffer rowEvent = stack.mallocPointer(1);
            ClCustomEvent customEvent = new ClCustomEvent(buffer.context);
            ByteBuffer tempNativeBuffer = (ByteBuffer) MemoryUtil.memAlloc(len).put(array).rewind();

            int errorCode = CL10.clEnqueueWriteBuffer(
                    buffer.context.getCommandQueue(),
                    buffer.clMem,
                    false,
                    (long) offset * structureSize,
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
                        "OpenCL write buffer failed for buffer '%s': error - %s",
                        buffer.getName(), OpenCLErrorUtils.getCLErrorString(errorCode));
                logger.error(message);
                throw new BufferOperationException(message, errorCode);
            }

            ClEvent thisEvent = new ClEvent(rowEvent.get(0));
            thisEvent.onComplete((long event, int status) ->{
                try {
                    if (OpenCLErrorUtils.isSuccess(status)) {
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

    default ClEvent writeAsyncByte (int offset, byte[] array){
        return writeAsyncByte(offset, null, array);
    }

    default ClEvent writeAsyncByte (ClEventList events, byte[] array){
        return writeAsyncByte(0, events, array);
    }

    default ClEvent writeAsyncByte (byte[] array){
        return writeAsyncByte(0, null, array);
    }

    @SuppressWarnings("unchecked")
    default ClEvent writeNextAsyncByte (ClEventList events, byte[] array){
        T buffer = (T) this;

        buffer.checkNotClosed();

        DataProcessor dataProcessor = buffer.dataProcessor;

        int offset = buffer.pointer;

        buffer.pointer += array.length / dataProcessor.getSizeStruct();

        return writeAsyncByte(offset, events, array);
    }

    default ClEvent writeNextAsyncByte (byte[] array){
        return writeNextAsyncByte(null, array);
    }

    @SuppressWarnings("unchecked")
    default void writeSyncByte (int offset, ClEventList events, byte[] array){
        T buffer = (T) this;

        buffer.checkNotClosed();

        DataProcessor dataProcessor = buffer.dataProcessor;

        if(array == null){
            String message = String.format(
                    "For write data to a buffer '%s', the array of byte cannot be null.",
                    buffer.getName());
            logger.error(message);
            throw new IllegalArgumentException(message);
        }

        if(offset < 0) {
            String message = String.format(
                    "To write data to a buffer, the offset passed cannot be negative: offset=%d, for buffer '%s'",
                    offset, buffer.getName());
            logger.error(message);
            throw new IllegalArgumentException(message);
        }

        int len = array.length;
        int structureSize = dataProcessor.getSizeStruct();

        if ((buffer.capacity - offset) * structureSize < len) {
            try{
                int newCapacity = offset + (int)Math.ceil((double) len / structureSize);
                buffer.changeCapacity(newCapacity, events);
            } catch (Exception e) {
                String message = String.format(
                        "Attempt to write outside buffer bounds: offset=%d, length=%d, capacity=%d for buffer '%s'",
                        offset, len, buffer.capacity, buffer.getName());
                logger.error(message, e);
                throw new IllegalArgumentException(message, e);
            }
        }

        ByteBuffer tempNativeBuffer = null;

        try (MemoryStack stack = MemoryStack.stackPush()){
            if(buffer.stagingBuffer == null) {
                tempNativeBuffer = MemoryUtil.memAlloc(len);
            } else {
                buffer.stagingBuffer.rewind().limit(len);
                tempNativeBuffer = buffer.stagingBuffer.slice().order(ByteOrder.nativeOrder());
                buffer.stagingBuffer.clear();
            }

            tempNativeBuffer.put(array).rewind();

            int errorCode = CL10.clEnqueueWriteBuffer(
                    buffer.context.getCommandQueue(),
                    buffer.clMem,
                    true,
                    (long) offset * dataProcessor.getSizeStruct(),
                    tempNativeBuffer,
                    events != null ? events.getEventList(stack) : null,
                    null
            );

            if (events != null) {
                events.releaseEvents();
            }

            if (!OpenCLErrorUtils.isSuccess(errorCode)) {
                if(buffer.stagingBuffer == null){
                    MemoryUtil.memFree(tempNativeBuffer);
                }

                String message = String.format(
                        "OpenCL write buffer failed for buffer '%s': error - %s",
                        buffer.getName(), OpenCLErrorUtils.getCLErrorString(errorCode));
                logger.error(message);
                throw new BufferOperationException(message, errorCode);
            }
        }finally {
            if(buffer.stagingBuffer == null && tempNativeBuffer != null){
                MemoryUtil.memFree(tempNativeBuffer);
            }
        }
    }

    default void writeSyncByte (int offset, byte[] array){
        writeSyncByte(offset, null, array);
    }

    default void writeSyncByte (ClEventList events, byte[] array){
        writeSyncByte(0, events, array);
    }

    default void writeSyncByte (byte[] array){
        writeSyncByte(0, null, array);
    }

    @SuppressWarnings("unchecked")
    default void writeNextSyncByte (ClEventList events, byte[] array){
        T buffer = (T) this;

        buffer.checkNotClosed();

        DataProcessor dataProcessor = buffer.dataProcessor;

        int offset = buffer.pointer;

        buffer.pointer += array.length / dataProcessor.getSizeStruct();

        writeSyncByte(offset, events, array);
    }

    default void writeNextSyncByte (byte[] array){
        writeNextSyncByte(null, array);
    }
}
