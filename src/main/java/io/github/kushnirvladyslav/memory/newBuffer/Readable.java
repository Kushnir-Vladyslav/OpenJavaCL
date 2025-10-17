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
import io.github.kushnirvladyslav.memory.data.ConvertFromByteBuffer;
import io.github.kushnirvladyslav.memory.data.Data;
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

        Data dataObject = buffer.dataObject;

        int len = dataObject.getSizeArray(targetArray);

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

        try (MemoryStack stack = MemoryStack.stackPush()){
            PointerBuffer rowEvent = stack.mallocPointer(1);
            ClCustomEvent customEvent = new ClCustomEvent(buffer.context);
            ByteBuffer tempNativeBuffer;
            if(buffer.stagingBuffer == null) {
                tempNativeBuffer = MemoryUtil.memAlloc(len * dataObject.getSizeStruct());
            } else {
                tempNativeBuffer = (ByteBuffer) buffer.stagingBuffer.rewind().limit(len * dataObject.getSizeStruct()).slice();
            }

            int errorCode = CL10.clEnqueueReadBuffer(
                    buffer.context.getCommandQueue(),
                    buffer.clMem,
                    false,
                    (long) offset * dataObject.getSizeStruct(),
                    tempNativeBuffer,
                    events != null ? events.getEventList(stack) : null,
                    rowEvent
            );

            if( events != null) {
                events.releaseEvents();
            }

            if (!OpenCLErrorUtils.isSuccess(errorCode)) {
                String message = String.format(
                        "OpenCL read buffer failed for buffer '%s': error - %s",
                        buffer.getName(), OpenCLErrorUtils.getCLErrorString(errorCode));
                logger.error(message);
                throw new BufferOperationException(message, errorCode);
            }

            ClEvent thisEvent = new ClEvent(rowEvent.get(0));
            thisEvent.onComplete((long event, int status) ->{
                if (OpenCLErrorUtils.isSuccess(status)) {
                    ((ConvertFromByteBuffer) dataObject).convertFromByteBuffer((ByteBuffer) tempNativeBuffer.rewind(), targetArray);
                    customEvent.setComplete();
                } else {
                    customEvent.setError(status);
                }
                if(buffer.stagingBuffer == null){
                    MemoryUtil.memFree(tempNativeBuffer);
                }
            });

            return customEvent;
        }
    }

}
