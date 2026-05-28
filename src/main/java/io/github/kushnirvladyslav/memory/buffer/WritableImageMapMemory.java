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
import io.github.kushnirvladyslav.memory.data.ToByteImageMapMemBuffer;
import io.github.kushnirvladyslav.memory.util.ImagePitches;
import io.github.kushnirvladyslav.util.OpenCLErrorUtils;
import io.github.kushnirvladyslav.util.clEvent.ClCustomEvent;
import io.github.kushnirvladyslav.util.clEvent.ClEvent;
import io.github.kushnirvladyslav.util.clEvent.ClEventList;
import org.lwjgl.PointerBuffer;
import org.lwjgl.opencl.CL10;
import org.lwjgl.system.MemoryStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Collection;
import java.util.Map;

public interface WritableImageMapMemory
        <T extends CopyableImageBuffer & WritableImageMapMemory<T>>
        extends WritableImage<T>{
    Logger logger = LoggerFactory.getLogger(WritableImageMapMemory.class);

    @SuppressWarnings("unchecked")
    default ClEvent writeMapAsync(CopyableImageBuffer.ImageRegion region, int rowPitch, int slicePitch, ClEventList events, Object array) {
        T buffer = (T) this;

        buffer.checkNotClosed();

        if (array == null) {
            String message = String.format(
                    "The passed array for writing the buffer '%s', can`t be null.",
                    buffer.getName());
            logger.error(message);
            throw new NullPointerException(message);
        }

        if (region == null) {
            String message = String.format(
                    "The region for writing the buffer '%s', can`t be null.",
                    buffer.getName());
            logger.error(message);
            throw new NullPointerException(message);
        }

        if (region.buffer != this) {
            throw new IllegalArgumentException(String.format(
                    "Region belongs to '%s', but writing called on '%s'.",
                    region.buffer.getName(), buffer.getName()));
        }

        buffer.validateSrc(region);

        int pixelSize = buffer.imageChannelOrder.getNumOfChannel() * buffer.imageChannelDataType.getByteSize();

        if (rowPitch < 0) {
            String message = String.format(
                    "To write data from a buffer, the 'row pitch' cannot be negative: 'row pitch'=%d, for buffer '%s'",
                    rowPitch, buffer.getName());
            logger.error(message);
            throw new IllegalArgumentException(message);
        }

        if (rowPitch > 0 && rowPitch < region.getRegionX() * pixelSize) {
            String message = String.format(
                    "To write data from a buffer, the 'row pitch' cannot be less than region width: 'row pitch'=%d, width=%d for buffer '%s'",
                    rowPitch, region.getRegionX() * pixelSize, buffer.getName());
            logger.error(message);
            throw new IllegalArgumentException(message);
        }

        long effectiveRowPitch = rowPitch == 0 ? (long) region.getRegionX() * pixelSize : rowPitch;

        if (slicePitch < 0) {
            String message = String.format(
                    "To write data from a buffer, the 'slice pitch' cannot be negative: 'slice pitch'=%d, for buffer '%s'",
                    slicePitch, buffer.getName());
            logger.error(message);
            throw new IllegalArgumentException(message);
        }

        if (slicePitch > 0 && slicePitch < region.getRegionY() * effectiveRowPitch) {
            String message = String.format(
                    "To write data from a buffer, the 'slice pitch' cannot be less than region height: 'slice pitch'=%d, height=%d for buffer '%s'",
                    slicePitch, region.getRegionY() * effectiveRowPitch, buffer.getName());
            logger.error(message);
            throw new IllegalArgumentException(message);
        }


        long effectiveSlicePitch = slicePitch == 0 ? region.getRegionY() * effectiveRowPitch : slicePitch;


        long dataLength = effectiveSlicePitch * region.getRegionZ();

        if ((long) buffer.dataProcessor.getSizeArray(array) * buffer.dataProcessor.getSizeStruct() < dataLength
                && !(array instanceof Collection || array instanceof Map)) {
            String message = String.format(
                    "The size of the data to be written is smaller than the size of the buffer into which it needs to be written: 'data size'=%d, 'buffer size'=%d for buffer '%s'",
                    dataLength, buffer.dataProcessor.getSizeArray(array), buffer.getName());
            logger.error(message);
            throw new IllegalArgumentException(message);
        }

        if (dataLength > Integer.MAX_VALUE) {
            String message = String.format(
                    "Write size exceeds byte[] limit (2GB). Try to read %d byte from buffer '%s'",
                    dataLength, buffer.getName());
            logger.error(message);
            throw new IllegalArgumentException(message);
        }

        if (pixelSize != buffer.dataProcessor.getSizeStruct()
                && buffer.imageChannelDataType.getByteSize() != buffer.dataProcessor.getSizeStruct()) {
            String message = String.format(
                    "Pixel/channel size does not match data processor size: 'pixel size'=%d, 'channel size'=%d, 'data size'=%d for buffer '%s'",
                    pixelSize, buffer.imageChannelDataType.getByteSize(), buffer.dataProcessor.getSizeStruct(), buffer.getName());
            logger.warn(message);
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer rowEvent = stack.mallocPointer(1);
            ClCustomEvent customEvent = new ClCustomEvent(buffer.context);
            PointerBuffer row = stack.mallocPointer(1);
            PointerBuffer slice = stack.mallocPointer(1);
            IntBuffer errorCode = stack.mallocInt(1);

            ByteBuffer mappedMemory = CL10.clEnqueueMapImage(
                    buffer.context.getCommandQueue(),
                    buffer.clMem,
                    false,
                    CL10.CL_MAP_WRITE,
                    region.getOrigin(stack),
                    region.getRegion(stack),
                    row,
                    slice,
                    events != null ? events.getEventList(stack) : null,
                    rowEvent,
                    errorCode,
                    null
            );

            if (events != null) {
                events.releaseEvents();
            }

            if (!OpenCLErrorUtils.isSuccess(errorCode.get(0))) {
                customEvent.setError(errorCode.get(0));
                String message = String.format(
                        "OpenCL read buffer failed for buffer '%s': error - %s",
                        buffer.getName(), OpenCLErrorUtils.getCLErrorString(errorCode.get(0)));
                logger.error(message);
                throw new BufferOperationException(message, errorCode.get(0));
            }

            int bufferRowPitch = (int) row.get(0);
            int bufferSlicePitch = (int) slice.get(0);

            ClEvent thisEvent = new ClEvent(rowEvent.get(0));
            thisEvent.onComplete((long event, int status) -> {
                try {
                    if (OpenCLErrorUtils.isSuccess(status)) {
                        ((ToByteImageMapMemBuffer) buffer.dataProcessor)
                                .convertToByteBuffer(
                                        mappedMemory,
                                        new ImagePitches(
                                                region.region,
                                                rowPitch,
                                                slicePitch,
                                                bufferRowPitch,
                                                bufferSlicePitch
                                        ),
                                        array
                                );
                        customEvent.setComplete();
                    } else {
                        customEvent.setError(status);
                    }
                } finally {
                    int errCode = CL10.clEnqueueUnmapMemObject(
                            buffer.context.getCommandQueue(),
                            buffer.clMem,
                            mappedMemory,
                            null,
                            null
                    );

                    if (!OpenCLErrorUtils.isSuccess(errCode)) {
                        String message = String.format(
                                "OpenCL write image buffer failed of unmapping memory for buffer '%s': error - %s",
                                buffer.getName(), OpenCLErrorUtils.getCLErrorString(errCode));
                        logger.error(message);
                    }
                }
            });

            return customEvent;
        }
    }

    @SuppressWarnings("unchecked")
    default void writeMapSync(CopyableImageBuffer.ImageRegion region, int rowPitch, int slicePitch, ClEventList events, Object array) {
        T buffer = (T) this;

        buffer.checkNotClosed();

        if (array == null) {
            String message = String.format(
                    "The passed array for writing the buffer '%s', can`t be null.",
                    buffer.getName());
            logger.error(message);
            throw new NullPointerException(message);
        }

        if (region == null) {
            String message = String.format(
                    "The region for writing the buffer '%s', can`t be null.",
                    buffer.getName());
            logger.error(message);
            throw new NullPointerException(message);
        }

        if (region.buffer != this) {
            throw new IllegalArgumentException(String.format(
                    "Region belongs to '%s', but writing called on '%s'.",
                    region.buffer.getName(), buffer.getName()));
        }

        buffer.validateSrc(region);

        int pixelSize = buffer.imageChannelOrder.getNumOfChannel() * buffer.imageChannelDataType.getByteSize();

        if (rowPitch < 0) {
            String message = String.format(
                    "To write data from a buffer, the 'row pitch' cannot be negative: 'row pitch'=%d, for buffer '%s'",
                    rowPitch, buffer.getName());
            logger.error(message);
            throw new IllegalArgumentException(message);
        }

        if (rowPitch > 0 && rowPitch < region.getRegionX() * pixelSize) {
            String message = String.format(
                    "To write data from a buffer, the 'row pitch' cannot be less than region width: 'row pitch'=%d, width=%d for buffer '%s'",
                    rowPitch, region.getRegionX() * pixelSize, buffer.getName());
            logger.error(message);
            throw new IllegalArgumentException(message);
        }

        long effectiveRowPitch = rowPitch == 0 ? (long) region.getRegionX() * pixelSize : rowPitch;

        if (slicePitch < 0) {
            String message = String.format(
                    "To write data from a buffer, the 'slice pitch' cannot be negative: 'slice pitch'=%d, for buffer '%s'",
                    slicePitch, buffer.getName());
            logger.error(message);
            throw new IllegalArgumentException(message);
        }

        if (slicePitch > 0 && slicePitch < region.getRegionY() * effectiveRowPitch) {
            String message = String.format(
                    "To write data from a buffer, the 'slice pitch' cannot be less than region height: 'slice pitch'=%d, height=%d for buffer '%s'",
                    slicePitch, region.getRegionY() * effectiveRowPitch, buffer.getName());
            logger.error(message);
            throw new IllegalArgumentException(message);
        }


        long effectiveSlicePitch = slicePitch == 0 ? region.getRegionY() * effectiveRowPitch : slicePitch;


        long dataLength = effectiveSlicePitch * region.getRegionZ();

        if ((long) buffer.dataProcessor.getSizeArray(array) * buffer.dataProcessor.getSizeStruct() < dataLength
                && !(array instanceof Collection || array instanceof Map)) {
            String message = String.format(
                    "The size of the data to be written is smaller than the size of the buffer into which it needs to be written: 'data size'=%d, 'buffer size'=%d for buffer '%s'",
                    dataLength, buffer.dataProcessor.getSizeArray(array), buffer.getName());
            logger.error(message);
            throw new IllegalArgumentException(message);
        }

        if (dataLength > Integer.MAX_VALUE) {
            String message = String.format(
                    "Write size exceeds byte[] limit (2GB). Try to read %d byte from buffer '%s'",
                    dataLength, buffer.getName());
            logger.error(message);
            throw new IllegalArgumentException(message);
        }

        if (pixelSize != buffer.dataProcessor.getSizeStruct()
                && buffer.imageChannelDataType.getByteSize() != buffer.dataProcessor.getSizeStruct()) {
            String message = String.format(
                    "Pixel/channel size does not match data processor size: 'pixel size'=%d, 'channel size'=%d, 'data size'=%d for buffer '%s'",
                    pixelSize, buffer.imageChannelDataType.getByteSize(), buffer.dataProcessor.getSizeStruct(), buffer.getName());
            logger.warn(message);
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer row = stack.mallocPointer(1);
            PointerBuffer slice = stack.mallocPointer(1);
            IntBuffer errorCode = stack.mallocInt(1);

            ByteBuffer mappedMemory = CL10.clEnqueueMapImage(
                    buffer.context.getCommandQueue(),
                    buffer.clMem,
                    true,
                    CL10.CL_MAP_WRITE,
                    region.getOrigin(stack),
                    region.getRegion(stack),
                    row,
                    slice,
                    events != null ? events.getEventList(stack) : null,
                    null,
                    errorCode,
                    null
            );

            if (events != null) {
                events.releaseEvents();
            }

            if (!OpenCLErrorUtils.isSuccess(errorCode.get(0))) {
                String message = String.format(
                        "OpenCL write buffer failed for buffer '%s': error - %s",
                        buffer.getName(), OpenCLErrorUtils.getCLErrorString(errorCode.get(0)));
                logger.error(message);
                throw new BufferOperationException(message, errorCode.get(0));
            }

            int bufferRowPitch = (int) row.get(0);
            int bufferSlicePitch = (int) slice.get(0);
            
            ((ToByteImageMapMemBuffer) buffer.dataProcessor)
                    .convertToByteBuffer(
                            mappedMemory,
                            new ImagePitches(
                                    region.region,
                                    rowPitch,
                                    slicePitch,
                                    bufferRowPitch,
                                    bufferSlicePitch
                            ),
                            array
                    );

            int errCode = CL10.clEnqueueUnmapMemObject(
                    buffer.context.getCommandQueue(),
                    buffer.clMem,
                    mappedMemory,
                    null,
                    null
            );

            if (!OpenCLErrorUtils.isSuccess(errCode)) {
                String message = String.format(
                        "OpenCL write image buffer failed of unmapping memory for buffer '%s': error - %s",
                        buffer.getName(), OpenCLErrorUtils.getCLErrorString(errCode));
                logger.error(message);
            }
        }
    }

    @SuppressWarnings("unchecked")
    default ClEvent writeMapAsyncByte(CopyableImageBuffer.ImageRegion region, int rowPitch, int slicePitch, ClEventList events, byte[] array) {
        T buffer = (T) this;

        buffer.checkNotClosed();

        if (array == null) {
            String message = String.format(
                    "The passed array for writing the buffer '%s', can`t be null.",
                    buffer.getName());
            logger.error(message);
            throw new NullPointerException(message);
        }

        if (region == null) {
            String message = String.format(
                    "The region for writing the buffer '%s', can`t be null.",
                    buffer.getName());
            logger.error(message);
            throw new NullPointerException(message);
        }

        if (region.buffer != this) {
            throw new IllegalArgumentException(String.format(
                    "Region belongs to '%s', but writing called on '%s'.",
                    region.buffer.getName(), buffer.getName()));
        }

        buffer.validateSrc(region);

        int pixelSize = buffer.imageChannelOrder.getNumOfChannel() * buffer.imageChannelDataType.getByteSize();

        if (rowPitch < 0) {
            String message = String.format(
                    "To write data from a buffer, the 'row pitch' cannot be negative: 'row pitch'=%d, for buffer '%s'",
                    rowPitch, buffer.getName());
            logger.error(message);
            throw new IllegalArgumentException(message);
        }

        if (rowPitch > 0 && rowPitch < region.getRegionX() * pixelSize) {
            String message = String.format(
                    "To write data from a buffer, the 'row pitch' cannot be less than region width: 'row pitch'=%d, width=%d for buffer '%s'",
                    rowPitch, region.getRegionX() * pixelSize, buffer.getName());
            logger.error(message);
            throw new IllegalArgumentException(message);
        }

        long effectiveRowPitch = rowPitch == 0 ? (long) region.getRegionX() * pixelSize : rowPitch;

        if (slicePitch < 0) {
            String message = String.format(
                    "To write data from a buffer, the 'slice pitch' cannot be negative: 'slice pitch'=%d, for buffer '%s'",
                    slicePitch, buffer.getName());
            logger.error(message);
            throw new IllegalArgumentException(message);
        }

        if (slicePitch > 0 && slicePitch < region.getRegionY() * effectiveRowPitch) {
            String message = String.format(
                    "To write data from a buffer, the 'slice pitch' cannot be less than region height: 'slice pitch'=%d, height=%d for buffer '%s'",
                    slicePitch, region.getRegionY() * effectiveRowPitch, buffer.getName());
            logger.error(message);
            throw new IllegalArgumentException(message);
        }


        long effectiveSlicePitch = slicePitch == 0 ? region.getRegionY() * effectiveRowPitch : slicePitch;


        long dataLength = effectiveSlicePitch * region.getRegionZ();

        if (dataLength > Integer.MAX_VALUE) {
            String message = String.format(
                    "Write size exceeds byte[] limit (2GB). Try to read %d byte from buffer '%s'",
                    dataLength, buffer.getName());
            logger.error(message);
            throw new IllegalArgumentException(message);
        }
        
        if (array.length < dataLength) {
            String message = String.format(
                    "The size of the data to be written is smaller than the size of the buffer into which it needs to be written: 'data size'=%d, 'buffer size'=%d for buffer '%s'",
                    dataLength, buffer.dataProcessor.getSizeArray(array), buffer.getName());
            logger.error(message);
            throw new IllegalArgumentException(message);
        }
        
        if (pixelSize != buffer.dataProcessor.getSizeStruct()
                && buffer.imageChannelDataType.getByteSize() != buffer.dataProcessor.getSizeStruct()) {
            String message = String.format(
                    "Pixel/channel size does not match data processor size: 'pixel size'=%d, 'channel size'=%d, 'data size'=%d for buffer '%s'",
                    pixelSize, buffer.imageChannelDataType.getByteSize(), buffer.dataProcessor.getSizeStruct(), buffer.getName());
            logger.warn(message);
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer rowEvent = stack.mallocPointer(1);
            ClCustomEvent customEvent = new ClCustomEvent(buffer.context);
            PointerBuffer row = stack.mallocPointer(1);
            PointerBuffer slice = stack.mallocPointer(1);
            IntBuffer errorCode = stack.mallocInt(1);

            ByteBuffer mappedMemory = CL10.clEnqueueMapImage(
                    buffer.context.getCommandQueue(),
                    buffer.clMem,
                    false,
                    CL10.CL_MAP_WRITE,
                    region.getOrigin(stack),
                    region.getRegion(stack),
                    row,
                    slice,
                    events != null ? events.getEventList(stack) : null,
                    rowEvent,
                    errorCode,
                    null
            );

            if (events != null) {
                events.releaseEvents();
            }

            if (!OpenCLErrorUtils.isSuccess(errorCode.get(0))) {
                customEvent.setError(errorCode.get(0));
                String message = String.format(
                        "OpenCL read buffer failed for buffer '%s': error - %s",
                        buffer.getName(), OpenCLErrorUtils.getCLErrorString(errorCode.get(0)));
                logger.error(message);
                throw new BufferOperationException(message, errorCode.get(0));
            }

            int bufferRowPitch = (int) row.get(0);
            int bufferSlicePitch = (int) slice.get(0);

            ClEvent thisEvent = new ClEvent(rowEvent.get(0));
            thisEvent.onComplete((long event, int status) -> {
                try {
                    if (OpenCLErrorUtils.isSuccess(status)) {
                        if(bufferRowPitch == 0 && bufferSlicePitch == 0 && rowPitch == 0 && slicePitch == 0){
                            mappedMemory.put(array);
                        } else {
                            int structureSize = buffer.dataProcessor.getSizeStruct();

                            int effectiveReadRowPitch = (rowPitch == 0) ?
                                    region.getRegionX() * structureSize : rowPitch;
                            int effectiveReadSlicePitch = (slicePitch == 0) ?
                                    region.getRegionY() * effectiveReadRowPitch : slicePitch;
                            int effectiveWriteRowPitch = (bufferRowPitch == 0) ?
                                    region.getRegionX() * structureSize : bufferRowPitch;
                            int effectiveWriteSlicePitch = (bufferSlicePitch == 0) ?
                                    region.getRegionY() * effectiveWriteRowPitch: bufferSlicePitch;

                            for (int z = 0; z < region.getRegionZ(); z++){
                                int readSlicePitch = effectiveReadSlicePitch * z;
                                int writeSlicePitch = effectiveWriteSlicePitch * z;
                                for(int y = 0; y < region.getRegionY(); y++){
                                    int readRowPitch = effectiveReadRowPitch * y;
                                    int writeRowPitch = effectiveWriteRowPitch * y;

                                    mappedMemory.position(writeSlicePitch + writeRowPitch);
                                    mappedMemory.put(
                                            array,
                                            readSlicePitch + readRowPitch,
                                            region.getRegionX() * structureSize
                                    );
                                }
                            }
                        }
                        customEvent.setComplete();
                    } else {
                        customEvent.setError(status);
                    }
                } finally {
                    int errCode = CL10.clEnqueueUnmapMemObject(
                            buffer.context.getCommandQueue(),
                            buffer.clMem,
                            mappedMemory,
                            null,
                            null
                    );

                    if (!OpenCLErrorUtils.isSuccess(errCode)) {
                        String message = String.format(
                                "OpenCL write image buffer failed of unmapping memory for buffer '%s': error - %s",
                                buffer.getName(), OpenCLErrorUtils.getCLErrorString(errCode));
                        logger.error(message);
                    }
                }
            });

            return customEvent;
        }
    }

    @SuppressWarnings("unchecked")
    default void writeMapSyncByte(CopyableImageBuffer.ImageRegion region, int rowPitch, int slicePitch, ClEventList events, byte[] array) {
        T buffer = (T) this;

        buffer.checkNotClosed();

        if (array == null) {
            String message = String.format(
                    "The passed array for writing the buffer '%s', can`t be null.",
                    buffer.getName());
            logger.error(message);
            throw new NullPointerException(message);
        }

        if (region == null) {
            String message = String.format(
                    "The region for writing the buffer '%s', can`t be null.",
                    buffer.getName());
            logger.error(message);
            throw new NullPointerException(message);
        }

        if (region.buffer != this) {
            throw new IllegalArgumentException(String.format(
                    "Region belongs to '%s', but writing called on '%s'.",
                    region.buffer.getName(), buffer.getName()));
        }

        buffer.validateSrc(region);

        int pixelSize = buffer.imageChannelOrder.getNumOfChannel() * buffer.imageChannelDataType.getByteSize();

        if (rowPitch < 0) {
            String message = String.format(
                    "To write data from a buffer, the 'row pitch' cannot be negative: 'row pitch'=%d, for buffer '%s'",
                    rowPitch, buffer.getName());
            logger.error(message);
            throw new IllegalArgumentException(message);
        }

        if (rowPitch > 0 && rowPitch < region.getRegionX() * pixelSize) {
            String message = String.format(
                    "To write data from a buffer, the 'row pitch' cannot be less than region width: 'row pitch'=%d, width=%d for buffer '%s'",
                    rowPitch, region.getRegionX() * pixelSize, buffer.getName());
            logger.error(message);
            throw new IllegalArgumentException(message);
        }

        long effectiveRowPitch = rowPitch == 0 ? (long) region.getRegionX() * pixelSize : rowPitch;

        if (slicePitch < 0) {
            String message = String.format(
                    "To write data from a buffer, the 'slice pitch' cannot be negative: 'slice pitch'=%d, for buffer '%s'",
                    slicePitch, buffer.getName());
            logger.error(message);
            throw new IllegalArgumentException(message);
        }

        if (slicePitch > 0 && slicePitch < region.getRegionY() * effectiveRowPitch) {
            String message = String.format(
                    "To write data from a buffer, the 'slice pitch' cannot be less than region height: 'slice pitch'=%d, height=%d for buffer '%s'",
                    slicePitch, region.getRegionY() * effectiveRowPitch, buffer.getName());
            logger.error(message);
            throw new IllegalArgumentException(message);
        }


        long effectiveSlicePitch = slicePitch == 0 ? region.getRegionY() * effectiveRowPitch : slicePitch;


        long dataLength = effectiveSlicePitch * region.getRegionZ();

        if (dataLength > Integer.MAX_VALUE) {
            String message = String.format(
                    "Write size exceeds byte[] limit (2GB). Try to read %d byte from buffer '%s'",
                    dataLength, buffer.getName());
            logger.error(message);
            throw new IllegalArgumentException(message);
        }
        
        if (array.length < dataLength) {
            String message = String.format(
                    "The size of the data to be written is smaller than the size of the buffer into which it needs to be written: 'data size'=%d, 'buffer size'=%d for buffer '%s'",
                    dataLength, buffer.dataProcessor.getSizeArray(array), buffer.getName());
            logger.error(message);
            throw new IllegalArgumentException(message);
        }

        if (pixelSize != buffer.dataProcessor.getSizeStruct()
                && buffer.imageChannelDataType.getByteSize() != buffer.dataProcessor.getSizeStruct()) {
            String message = String.format(
                    "Pixel/channel size does not match data processor size: 'pixel size'=%d, 'channel size'=%d, 'data size'=%d for buffer '%s'",
                    pixelSize, buffer.imageChannelDataType.getByteSize(), buffer.dataProcessor.getSizeStruct(), buffer.getName());
            logger.warn(message);
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer row = stack.mallocPointer(1);
            PointerBuffer slice = stack.mallocPointer(1);
            IntBuffer errorCode = stack.mallocInt(1);

            ByteBuffer mappedMemory = CL10.clEnqueueMapImage(
                    buffer.context.getCommandQueue(),
                    buffer.clMem,
                    true,
                    CL10.CL_MAP_WRITE,
                    region.getOrigin(stack),
                    region.getRegion(stack),
                    row,
                    slice,
                    events != null ? events.getEventList(stack) : null,
                    null,
                    errorCode,
                    null
            );

            if (events != null) {
                events.releaseEvents();
            }

            if (!OpenCLErrorUtils.isSuccess(errorCode.get(0))) {
                String message = String.format(
                        "OpenCL write buffer failed for buffer '%s': error - %s",
                        buffer.getName(), OpenCLErrorUtils.getCLErrorString(errorCode.get(0)));
                logger.error(message);
                throw new BufferOperationException(message, errorCode.get(0));
            }

            int bufferRowPitch = (int) row.get(0);
            int bufferSlicePitch = (int) slice.get(0);

            if(bufferRowPitch == 0 && bufferSlicePitch == 0 && rowPitch == 0 && slicePitch == 0){
                mappedMemory.put(array);
            } else {
                int structureSize = buffer.dataProcessor.getSizeStruct();

                int effectiveReadRowPitch = (rowPitch == 0) ?
                        region.getRegionX() * structureSize : rowPitch;
                int effectiveReadSlicePitch = (slicePitch == 0) ?
                        region.getRegionY() * effectiveReadRowPitch : slicePitch;
                int effectiveWriteRowPitch = (bufferRowPitch == 0) ?
                        region.getRegionX() * structureSize : bufferRowPitch;
                int effectiveWriteSlicePitch = (bufferSlicePitch == 0) ?
                        region.getRegionY() * effectiveWriteRowPitch: bufferSlicePitch;

                for (int z = 0; z < region.getRegionZ(); z++){
                    int readSlicePitch = effectiveReadSlicePitch * z;
                    int writeSlicePitch = effectiveWriteSlicePitch * z;
                    for(int y = 0; y < region.getRegionY(); y++){
                        int readRowPitch = effectiveReadRowPitch * y;
                        int writeRowPitch = effectiveWriteRowPitch * y;

                        mappedMemory.position(writeSlicePitch + writeRowPitch);
                        mappedMemory.put(
                                array,
                                readSlicePitch + readRowPitch,
                                region.getRegionX() * structureSize
                        );
                    }
                }
            }

            int errCode = CL10.clEnqueueUnmapMemObject(
                    buffer.context.getCommandQueue(),
                    buffer.clMem,
                    mappedMemory,
                    null,
                    null
            );

            if (!OpenCLErrorUtils.isSuccess(errCode)) {
                String message = String.format(
                        "OpenCL write image buffer failed of unmapping memory for buffer '%s': error - %s",
                        buffer.getName(), OpenCLErrorUtils.getCLErrorString(errCode));
                logger.error(message);
            }
        }
    }
}
