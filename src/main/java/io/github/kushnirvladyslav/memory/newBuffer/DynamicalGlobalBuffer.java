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
import io.github.kushnirvladyslav.util.OpenCLErrorUtils;
import io.github.kushnirvladyslav.util.clEvent.ClEventList;
import org.lwjgl.PointerBuffer;
import org.lwjgl.opencl.CL10;
import org.lwjgl.system.MemoryStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class DynamicalGlobalBuffer
        extends CopyableGlobalBuffer{
    private static final Logger logger = LoggerFactory.getLogger(DynamicalGlobalBuffer.class);

    private static final double capacityMultiplier = 1.5;
    private static final int minCapacity = 10;
    private static final double shrinkFactor = 2.0;

    public DynamicalGlobalBuffer(DynamicalGlobalBufferBuilder<?, ?> builder) {
        super(builder);
    }

    @Override
    protected void changeCapacity(int newCapacity, ClEventList events){
        checkNotClosed();

        if (newCapacity < 0) {
            String message = String.format(
                    "Required capacity cannot be negative: %d",
                    newCapacity
            );
            logger.error(message);
            throw new IllegalArgumentException(message);
        }

        int oldCapacity = capacity;
        long oldClBuffer = clMem;

        long newClMem = 0;
        int targetCapacity = oldCapacity;

        int minReq = Math.max(newCapacity, minCapacity);

        if (minReq < capacity &&
                (double)capacity / minReq > shrinkFactor) {
            targetCapacity = minReq;

            try {
                capacity = targetCapacity;
                newClMem = createClMem();
            } catch (Exception e){
                capacity = oldCapacity;

                String message = String.format(
                        "Failed to decrease capacity of OpenCL buffer '%s' to %d.",
                        getName(), newCapacity);
                logger.error(message);
                throw new BufferOperationException(message, e);
            }
        } else if (newCapacity > capacity) {
            targetCapacity = (int) (newCapacity * capacityMultiplier);

            try {
                capacity = targetCapacity;
                newClMem = createClMem();
            } catch (Exception e) {
                targetCapacity = newCapacity;

                try {
                    capacity = targetCapacity;
                    newClMem = createClMem();
                } catch (Exception ex){
                    capacity = oldCapacity;

                    String message = String.format(
                            "Failed to increase capacity of OpenCL buffer '%s' to %d.",
                            getName(), newCapacity);
                    logger.error(message);
                    throw new BufferOperationException(message, ex);
                }
            }
        } else {
            return;
        }
        capacity = oldCapacity;


        try (MemoryStack stack = MemoryStack.stackPush()){
            PointerBuffer thisEvent = stack.mallocPointer(1);

            int dataSize = dataProcessor.getSizeStruct();

            int errorCode = CL10.clEnqueueCopyBuffer(
                    context.getCommandQueue(),
                    oldClBuffer,
                    newClMem,
                    0,
                    0,
                    (long) Math.min(oldCapacity, targetCapacity) * dataSize,
                    (events == null) ? null : events.getEventList(stack),
                    thisEvent.rewind()
            );

            if (events != null) events.releaseEvents();

            if (!OpenCLErrorUtils.isSuccess(errorCode)) {
                String message = String.format(
                        "Copying from old to new clBuffer, when decreasing size of buffer '%s', ended with an error: %s",
                        name, OpenCLErrorUtils.getCLErrorString(errorCode));
                logger.error(message);
                throw new BufferOperationException(message);
            }

            errorCode = CL10.clWaitForEvents(thisEvent);
            CL10.clReleaseEvent(thisEvent.get(0));
            if (!OpenCLErrorUtils.isSuccess(errorCode)) {
                String message = String.format(
                        "Copying from old to new clBuffer, when decreasing size of buffer '%s', ended with an error: %s",
                        name, OpenCLErrorUtils.getCLErrorString(errorCode));
                logger.error(message);
                throw new BufferOperationException(message);
            }

            errorCode = CL10.clReleaseMemObject(oldClBuffer);
            if (!OpenCLErrorUtils.isSuccess(errorCode)) {
                logger.warn("Failed to release old buffer memory object: OpenCL error - {}",
                        OpenCLErrorUtils.getCLErrorString(errorCode));
            }

            capacity = targetCapacity;
            clMem = newClMem;

            rebindAllKernels();
        } catch (Exception e) {
            CL10.clReleaseMemObject(newClMem);
            throw new BufferOperationException("Buffer resize failed during copy/sync.", e);
        }
    }

    public void resize (int newSize){
        resize(newSize, null);
    }

    public void resize (int newSize, ClEventList events){
        changeCapacity(newSize, events);
    }

    public void increase (int newSize){
        increase(newSize, null);
    }

    public void increase (int newSize, ClEventList events){
        if(newSize > capacity) {
            changeCapacity(newSize, events);
        }
    }

    public void decrease (int newSize) {
        decrease(newSize, null);
    }

    public void decrease (int newSize, ClEventList events){
        if(newSize < capacity / shrinkFactor) {
            changeCapacity(newSize, events);
        }
    }
}
