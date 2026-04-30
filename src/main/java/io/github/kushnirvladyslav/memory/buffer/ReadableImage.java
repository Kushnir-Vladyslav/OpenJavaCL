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

public interface ReadableImage <T extends CopyableImageBuffer & ReadableImage<T>> {
    Logger logger = LoggerFactory.getLogger(ReadableImage.class);

    @SuppressWarnings("unchecked")
    default ClEvent readAsync (CopyableImageBuffer.ImageRegion region, int rowPitch, int slicePitch, ClEventList events, Object targetArray){
        T buffer = (T) this;

        buffer.checkNotClosed();

        if(targetArray == null) {
            String message = String.format(
                    "The passed array for reading the buffer '%s', can`t be null.",
                    buffer.getName());
            logger.error(message);
            throw new NullPointerException(message);
        }

        if(region == null){
            String message = String.format(
                    "The region for reading the buffer '%s', can`t be null.",
                    buffer.getName());
            logger.error(message);
            throw new NullPointerException(message);
        }

        if (region.buffer != this){
            throw new IllegalArgumentException(String.format(
                    "Region belongs to '%s', but reading called on '%s'.",
                    region.buffer.getName(), buffer.getName()));
        }

        buffer.validateSrc(region);

        int pixelSize = buffer.imageChannelOrder.getNumOfChannel() * buffer.imageChannelDataType.getByteSize();

        if(rowPitch < 0){
            String message = String.format(
                    "To read data from a buffer, the 'row pitch' cannot be negative: 'row pitch'=%d, for buffer '%s'",
                    rowPitch, buffer.getName());
            logger.error(message);
            throw new IllegalArgumentException(message);
        }

        if(rowPitch > 0 && rowPitch < region.getRegionX() * pixelSize){
            String message = String.format(
                    "To read data from a buffer, the 'row pitch' cannot be less than region width: 'row pitch'=%d, width=%d for buffer '%s'",
                    rowPitch, region.getRegionX() * pixelSize, buffer.getName());
            logger.error(message);
            throw new IllegalArgumentException(message);
        }

        long effectiveRowPitch = rowPitch == 0 ? (long) region.getRegionX() * pixelSize : rowPitch;

        if(slicePitch < 0){
            String message = String.format(
                    "To read data from a buffer, the 'slice pitch' cannot be negative: 'slice pitch'=%d, for buffer '%s'",
                    slicePitch, buffer.getName());
            logger.error(message);
            throw new IllegalArgumentException(message);
        }

        if(slicePitch > 0 && slicePitch < region.getRegionY() * effectiveRowPitch){
            String message = String.format(
                    "To read data from a buffer, the 'slice pitch' cannot be less than region height: 'slice pitch'=%d, height=%d for buffer '%s'",
                    slicePitch, region.getRegionY() * effectiveRowPitch, buffer.getName());
            logger.error(message);
            throw new IllegalArgumentException(message);
        }


        long effectiveSlicePitch = slicePitch == 0 ? region.getRegionY() * effectiveRowPitch : slicePitch;


        long dataLength = effectiveSlicePitch * region.getRegionZ();

        if (buffer.dataProcessor.getSizeArray(targetArray) < dataLength){
            String message = String.format(
                    "The size of the data to be read exceeds the size of the buffer into which it must be written: 'data size'=%d, 'buffer size'=%d for buffer '%s'",
                    dataLength, buffer.dataProcessor.getSizeArray(targetArray), buffer.getName());
            logger.error(message);
            throw new IllegalArgumentException(message);
        }

        if (dataLength > Integer.MAX_VALUE) {
            String message = String.format(
                    "Read size exceeds byte[] limit (2GB). Try to read %d byte from buffer '%s'",
                    dataLength, buffer.getName());
            logger.error(message);
            throw new IllegalArgumentException(message);
        }

        if(pixelSize != buffer.dataProcessor.getSizeStruct()
                && buffer.imageChannelDataType.getByteSize() != buffer.dataProcessor.getSizeStruct()){
            String message = String.format(
                    "Pixel/channel size does not match data processor size: 'pixel size'=%d, 'channel size'=%d, 'data size'=%d for buffer '%s'",
                    pixelSize, buffer.imageChannelDataType.getByteSize(), buffer.dataProcessor.getSizeStruct(), buffer.getName());
            logger.warn(message);
        }

        try (MemoryStack stack = MemoryStack.stackPush()){
            PointerBuffer rowEvent = stack.mallocPointer(1);
            ClCustomEvent customEvent = new ClCustomEvent(buffer.context);
            ByteBuffer tempNativeBuffer = MemoryUtil.memAlloc((int)dataLength);

            int errorCode = CL10.clEnqueueReadImage(
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
                        ((FromByteBuffer) buffer.dataProcessor).convertFromByteBuffer((ByteBuffer) tempNativeBuffer.rewind(), targetArray);
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
    default void readSync (CopyableImageBuffer.ImageRegion region, int rowPitch, int slicePitch, ClEventList events, Object targetArray){
        T buffer = (T) this;

        buffer.checkNotClosed();

        if(targetArray == null) {
            String message = String.format(
                    "The passed array for reading the buffer '%s', can`t be null.",
                    buffer.getName());
            logger.error(message);
            throw new NullPointerException(message);
        }

        if(region == null){
            String message = String.format(
                    "The region for reading the buffer '%s', can`t be null.",
                    buffer.getName());
            logger.error(message);
            throw new NullPointerException(message);
        }

        if (region.buffer != this){
            throw new IllegalArgumentException(String.format(
                    "Region belongs to '%s', but reading called on '%s'.",
                    region.buffer.getName(), buffer.getName()));
        }

        buffer.validateSrc(region);

        int pixelSize = buffer.imageChannelOrder.getNumOfChannel() * buffer.imageChannelDataType.getByteSize();

        if(rowPitch < 0){
            String message = String.format(
                    "To read data from a buffer, the 'row pitch' cannot be negative: 'row pitch'=%d, for buffer '%s'",
                    rowPitch, buffer.getName());
            logger.error(message);
            throw new IllegalArgumentException(message);
        }

        if(rowPitch > 0 && rowPitch < region.getRegionX() * pixelSize){
            String message = String.format(
                    "To read data from a buffer, the 'row pitch' cannot be less than region width: 'row pitch'=%d, width=%d for buffer '%s'",
                    rowPitch, region.getRegionX() * pixelSize, buffer.getName());
            logger.error(message);
            throw new IllegalArgumentException(message);
        }

        long effectiveRowPitch = rowPitch == 0 ? (long) region.getRegionX() * pixelSize : rowPitch;

        if(slicePitch < 0){
            String message = String.format(
                    "To read data from a buffer, the 'slice pitch' cannot be negative: 'slice pitch'=%d, for buffer '%s'",
                    slicePitch, buffer.getName());
            logger.error(message);
            throw new IllegalArgumentException(message);
        }

        if(slicePitch > 0 && slicePitch < region.getRegionY() * effectiveRowPitch){
            String message = String.format(
                    "To read data from a buffer, the 'slice pitch' cannot be less than region height: 'slice pitch'=%d, height=%d for buffer '%s'",
                    slicePitch, region.getRegionY() * effectiveRowPitch, buffer.getName());
            logger.error(message);
            throw new IllegalArgumentException(message);
        }


        long effectiveSlicePitch = slicePitch == 0 ? region.getRegionY() * effectiveRowPitch : slicePitch;


        long dataLength = effectiveSlicePitch * region.getRegionZ();

        if (buffer.dataProcessor.getSizeArray(targetArray) < dataLength){
            String message = String.format(
                    "The size of the data to be read exceeds the size of the buffer into which it must be written: 'data size'=%d, 'buffer size'=%d for buffer '%s'",
                    dataLength, buffer.dataProcessor.getSizeArray(targetArray), buffer.getName());
            logger.error(message);
            throw new IllegalArgumentException(message);
        }

        if (dataLength > Integer.MAX_VALUE) {
            String message = String.format(
                    "Read size exceeds byte[] limit (2GB). Try to read %d byte from buffer '%s'",
                    dataLength, buffer.getName());
            logger.error(message);
            throw new IllegalArgumentException(message);
        }

        if(pixelSize != buffer.dataProcessor.getSizeStruct()
                && buffer.imageChannelDataType.getByteSize() != buffer.dataProcessor.getSizeStruct()){
            String message = String.format(
                    "Pixel/channel size does not match data processor size: 'pixel size'=%d, 'channel size'=%d, 'data size'=%d for buffer '%s'",
                    pixelSize, buffer.imageChannelDataType.getByteSize(), buffer.dataProcessor.getSizeStruct(), buffer.getName());
            logger.warn(message);
        }

        ByteBuffer tempNativeBuffer = null;

        try (MemoryStack stack = MemoryStack.stackPush()){


            CL10.clEnqueueMapImage()

            tempNativeBuffer = MemoryUtil.memAlloc((int)dataLength);

            int errorCode = CL10.clEnqueueReadImage(
                    buffer.context.getCommandQueue(),
                    buffer.clMem,
                    false,
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
                if(buffer.stagingBuffer == null){
                    MemoryUtil.memFree(tempNativeBuffer);
                }

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
                        ((FromByteBuffer) buffer.dataProcessor).convertFromByteBuffer((ByteBuffer) tempNativeBuffer.rewind(), targetArray);
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

}
