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
import java.util.Collection;
import java.util.Map;

public interface WritableImage <T extends CopyableImageBuffer & WritableImage<T>>{
    Logger logger = LoggerFactory.getLogger(WritableImage.class);

    @SuppressWarnings("unchecked")
    default ClEvent writeAsync(CopyableImageBuffer.ImageRegion region, int rowPitch, int slicePitch, ClEventList events, Object array) {
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
            ByteBuffer tempNativeBuffer = MemoryUtil.memAlloc((int) dataLength);

            ((ToByteBuffer) buffer.dataProcessor)
                    .convertToByteBuffer((ByteBuffer) tempNativeBuffer.rewind(), array);

            tempNativeBuffer.rewind();

            int errorCode = CL10.clEnqueueWriteImage(
                    buffer.context.getCommandQueue(),
                    buffer.clMem,
                    false,
                    region.getOrigin(stack),
                    region.getRegion(stack),
                    rowPitch,
                    slicePitch,
                    tempNativeBuffer,
                    events != null ? events.getEventList(stack) : null,
                    rowEvent
            );

            if (events != null) {
                events.releaseEvents();
            }

            if (!OpenCLErrorUtils.isSuccess(errorCode)) {
                MemoryUtil.memFree(tempNativeBuffer);

                customEvent.setError(errorCode);
                String message = String.format(
                        "OpenCL write buffer failed for buffer '%s': error - %s",
                        buffer.getName(), OpenCLErrorUtils.getCLErrorString(errorCode));
                logger.error(message);
                throw new BufferOperationException(message, errorCode);
            }

            ClEvent thisEvent = new ClEvent(rowEvent.get(0));
            thisEvent.onComplete((long event, int status) -> {
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

    @SuppressWarnings("unchecked")
    default void writeSync(CopyableImageBuffer.ImageRegion region, int rowPitch, int slicePitch, ClEventList events, Object array) {
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
            ByteBuffer tempNativeBuffer = stack.malloc((int) dataLength);

            ((ToByteBuffer) buffer.dataProcessor)
                    .convertToByteBuffer((ByteBuffer) tempNativeBuffer.rewind(), array);

            tempNativeBuffer.rewind();

            int errorCode = CL10.clEnqueueWriteImage(
                    buffer.context.getCommandQueue(),
                    buffer.clMem,
                    true,
                    region.getOrigin(stack),
                    region.getRegion(stack),
                    rowPitch,
                    slicePitch,
                    tempNativeBuffer,
                    events != null ? events.getEventList(stack) : null,
                    null
            );

            if (events != null) {
                events.releaseEvents();
            }

            if (!OpenCLErrorUtils.isSuccess(errorCode)) {
                String message = String.format(
                        "OpenCL write buffer failed for buffer '%s': error - %s",
                        buffer.getName(), OpenCLErrorUtils.getCLErrorString(errorCode));
                logger.error(message);
                throw new BufferOperationException(message, errorCode);
            }
        }
    }

    @SuppressWarnings("unchecked")
    default ClEvent writeAsyncByte(CopyableImageBuffer.ImageRegion region, int rowPitch, int slicePitch, ClEventList events, byte[] array) {
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
            ByteBuffer tempNativeBuffer = MemoryUtil.memAlloc((int) dataLength);

            tempNativeBuffer.put(array).rewind();

            int errorCode = CL10.clEnqueueWriteImage(
                    buffer.context.getCommandQueue(),
                    buffer.clMem,
                    false,
                    region.getOrigin(stack),
                    region.getRegion(stack),
                    rowPitch,
                    slicePitch,
                    tempNativeBuffer,
                    events != null ? events.getEventList(stack) : null,
                    rowEvent
            );

            if (events != null) {
                events.releaseEvents();
            }

            if (!OpenCLErrorUtils.isSuccess(errorCode)) {
                MemoryUtil.memFree(tempNativeBuffer);

                customEvent.setError(errorCode);
                String message = String.format(
                        "OpenCL write buffer failed for buffer '%s': error - %s",
                        buffer.getName(), OpenCLErrorUtils.getCLErrorString(errorCode));
                logger.error(message);
                throw new BufferOperationException(message, errorCode);
            }

            ClEvent thisEvent = new ClEvent(rowEvent.get(0));
            thisEvent.onComplete((long event, int status) -> {
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

    @SuppressWarnings("unchecked")
    default void writeSyncByte(CopyableImageBuffer.ImageRegion region, int rowPitch, int slicePitch, ClEventList events, byte[] array) {
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
            ByteBuffer tempNativeBuffer = stack.malloc((int) dataLength);

            tempNativeBuffer.put(array).rewind();

            int errorCode = CL10.clEnqueueWriteImage(
                    buffer.context.getCommandQueue(),
                    buffer.clMem,
                    true,
                    region.getOrigin(stack),
                    region.getRegion(stack),
                    rowPitch,
                    slicePitch,
                    tempNativeBuffer,
                    events != null ? events.getEventList(stack) : null,
                    null
            );

            if (events != null) {
                events.releaseEvents();
            }

            if (!OpenCLErrorUtils.isSuccess(errorCode)) {
                String message = String.format(
                        "OpenCL write buffer failed for buffer '%s': error - %s",
                        buffer.getName(), OpenCLErrorUtils.getCLErrorString(errorCode));
                logger.error(message);
                throw new BufferOperationException(message, errorCode);
            }
        }
    }
}
