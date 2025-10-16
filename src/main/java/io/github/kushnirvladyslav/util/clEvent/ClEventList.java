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
import org.lwjgl.PointerBuffer;
import org.lwjgl.opencl.CL10;
import org.lwjgl.opencl.CL12;
import org.lwjgl.system.MemoryStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class ClEventList {
    private static final Logger logger = LoggerFactory.getLogger(ClEventList.class);

    private List<ClEvent> eventList = new ArrayList<>();
    private List<Long> activeEvents = new ArrayList<>();

    public void addEvent(ClEvent event){
        if(event == null){
            String message = "ClEvent cannot be null.";
            logger.error(message);
            throw new IllegalArgumentException(message);
        }

        if(!event.isDone()){
            eventList.add(event);
        }
    }

    public PointerBuffer getEventList(MemoryStack stack){
        if (!activeEvents.isEmpty()){
            logger.warn("ClEvents were not released. It`s can lead to leak memory.");
            releaseEvents();
        }

        eventList.removeIf(ClEvent::isDone);

        for (ClEvent event : eventList){
            long eventPointer = event.getEventPointer();
            if (eventPointer != 0){
                activeEvents.add(eventPointer);
            }
        }

        if(activeEvents.isEmpty()) {
            return null;
        }

        PointerBuffer pointerBuffer = stack.mallocPointer(activeEvents.size());


        for (Long active: activeEvents){
            pointerBuffer.put(active);
        }

        return pointerBuffer.rewind();
    }

    public void releaseEvents(){
        if(activeEvents.isEmpty()){
            return;
        }

        for (Long event: activeEvents){
            int errorCode = CL10.clReleaseEvent(event);
            if (!OpenCLErrorUtils.isSuccess(errorCode)) {
                logger.warn("Failed to release event {}: {}",
                        event, OpenCLErrorUtils.getCLErrorString(errorCode));
            }
        }

        activeEvents.clear();
    }

    public void waitForEvents() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer events = getEventList(stack);

            if (events == null || events.remaining() == 0) {
                return;
            }

            int errorCode = CL10.clWaitForEvents(events);

            if (!OpenCLErrorUtils.isSuccess(errorCode)) {
                String message = String.format(
                        "Failed to wait for events: %s",
                        OpenCLErrorUtils.getCLErrorString(errorCode)
                );
                logger.error(message);
                throw new BufferOperationException(message);
            }

            releaseEvents();
        }
    }

    public ClEvent enqueueBarrier(long commandQueue) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer events = getEventList(stack);
            PointerBuffer barrierEvent = stack.mallocPointer(1);

            int errorCode;

            if (events == null || events.remaining() == 0) {
                errorCode = CL12.clEnqueueBarrierWithWaitList(
                        commandQueue,
                        null,
                        barrierEvent
                );
            } else {
                errorCode = CL12.clEnqueueBarrierWithWaitList(
                        commandQueue,
                        events,
                        barrierEvent
                );
            }

            if (!OpenCLErrorUtils.isSuccess(errorCode)) {
                String message = String.format(
                        "Failed to enqueue barrier: %s",
                        OpenCLErrorUtils.getCLErrorString(errorCode)
                );
                logger.error(message);
                throw new BufferOperationException(message);
            }

            releaseEvents();

            long barrierEventHandle = barrierEvent.get(0);
            logger.trace("Barrier enqueued with event: {}", barrierEventHandle);

            return new ClEvent(barrierEventHandle);
        }
    }

    public ClEvent enqueueMarker(long commandQueue) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer events = getEventList(stack);
            PointerBuffer markerEvent = stack.mallocPointer(1);

            int errorCode;

            if (events == null || events.remaining() == 0) {
                errorCode = CL12.clEnqueueMarkerWithWaitList(
                        commandQueue,
                        null,
                        markerEvent
                );
            } else {
                errorCode = CL12.clEnqueueMarkerWithWaitList(
                        commandQueue,
                        events,
                        markerEvent
                );
            }

            if (!OpenCLErrorUtils.isSuccess(errorCode)) {
                String message = String.format(
                        "Failed to enqueue marker: %s",
                        OpenCLErrorUtils.getCLErrorString(errorCode)
                );
                logger.error(message);
                throw new BufferOperationException(message);
            }

            releaseEvents();

            long markerEventHandle = markerEvent.get(0);
            logger.trace("Marker enqueued with event: {}", markerEventHandle);

            return new ClEvent(markerEventHandle);
        }
    }

    public void clear() {
        releaseEvents();
        eventList.clear();
    }
}
