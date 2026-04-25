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

package io.github.kushnirvladyslav.util.clEvent;

import io.github.kushnirvladyslav.exceptions.BufferOperationException;
import io.github.kushnirvladyslav.util.OpenCLErrorUtils;
import org.lwjgl.opencl.CL10;
import org.lwjgl.opencl.CL11;
import org.lwjgl.system.MemoryStack;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.IntBuffer;

public class ClEvent {
    private static final Logger logger = LoggerFactory.getLogger(ClEvent.class);

    protected long eventPointer;
    protected volatile boolean isDone = false;

    private final Object lock = new Object();

    public ClEvent(long eventPointer){

        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer status = stack.mallocInt(1);

            int errorCode = CL10.clGetEventInfo(eventPointer, CL10.CL_EVENT_REFERENCE_COUNT, status, null);

            if(!OpenCLErrorUtils.isSuccess(errorCode)) {
                String message = String.format(
                        "ClEvent pointer (%d) isn`t valid.",
                        eventPointer
                );
                logger.error(message);
                throw new IllegalArgumentException(message);
            }
        }

        this.eventPointer = eventPointer;

        CL11.clSetEventCallback(eventPointer, CL10.CL_COMPLETE, this::releasePointer, 0);

    }

    private void releasePointer(long pointer, long status, long userData){
        synchronized (lock){
            if(isDone) return;

            isDone = true;
            CL10.clReleaseEvent(eventPointer);
            eventPointer = 0;
        }
    }

    protected long getEventPointer(){
        synchronized (lock){
            if(isDone || eventPointer == 0){
                return 0;
            }

            CL10.clRetainEvent(eventPointer);

            return  eventPointer;
        }
    }

    protected boolean isDone(){
        return isDone;
    }

    public void waitForComplete() {
        synchronized (lock) {
            if (isDone) return;
        }
        long ptr = getEventPointer(); // clRetainEvent всередині
        if (ptr == 0) return;         // вже завершився між перевірками
        try (MemoryStack stack = MemoryStack.stackPush()) {
            int errorCode = CL10.clWaitForEvents(
                    stack.mallocPointer(1).put(0, ptr).rewind()
            );
            CL10.clReleaseEvent(ptr);  // балансуємо Retain з getEventPointer
            if (!OpenCLErrorUtils.isSuccess(errorCode)) {
                throw new BufferOperationException(
                        "Failed to wait for event: " +
                                OpenCLErrorUtils.getCLErrorString(errorCode));
            }
        }
    }

    public void setCallback(EventCallback callback, int callbackType) {
        synchronized (lock) {
            if (eventPointer == 0) {
                callback.onStatusReached(0, CL10.CL_COMPLETE);
                return;
            }

            int errorCode = CL11.clSetEventCallback(
                    eventPointer,
                    callbackType,
                    (event, status, userData) -> {
                        try {
                            callback.onStatusReached(event, status);
                        } catch (Exception e) {
                            logger.error("Exception in event callback", e);
                        }
                    },
                    0
            );

            if (!OpenCLErrorUtils.isSuccess(errorCode)) {
                throw new BufferOperationException(
                        "Failed to set callback: " +
                                OpenCLErrorUtils.getCLErrorString(errorCode)
                );
            }
        }
    }

    public void onComplete(EventCallback callback) {
        setCallback(callback, CL10.CL_COMPLETE);
    }

    public void onRunning(EventCallback callback) {
        setCallback(callback, CL10.CL_RUNNING);
    }

    public void onSubmitted(EventCallback callback) {
        setCallback(callback, CL10.CL_SUBMITTED);
    }

    @FunctionalInterface
    public interface EventCallback {
        void onStatusReached(long event, int status);
    }
}
