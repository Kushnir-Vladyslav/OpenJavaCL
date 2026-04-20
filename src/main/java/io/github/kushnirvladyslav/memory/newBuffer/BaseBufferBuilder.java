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

import io.github.kushnirvladyslav.ClContext;

import io.github.kushnirvladyslav.exceptions.BufferInitializationException;
import io.github.kushnirvladyslav.memory.data.DataProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;

public abstract class BaseBufferBuilder <T extends BaseBufferBuilder<T, B>, B extends BaseBuffer>{
    private static final Logger logger = LoggerFactory.getLogger(BaseBufferBuilder.class);

    private static final AtomicInteger nameCounter = new AtomicInteger(0);

    private String name;

    private ClContext context;
    private Class<?> dataClass;

    @SuppressWarnings("unchecked")
    public T withName(String name) {
        if (name == null || name.trim().isEmpty()) {
            String message = "Buffer name cannot be null or empty";
            logger.error(message);
            throw new IllegalArgumentException(message);
        }

        this.name = name;

        return (T) this;
    }

    @SuppressWarnings("unchecked")
    public T withOpenClContext(ClContext clContext) {
        if (clContext == null) {
            String message = name != null
                    ? String.format("OpenCL context cannot be null for building buffer '%s'", name)
                    : "OpenCL context cannot be null for building buffer";
            logger.error(message);
            throw new IllegalArgumentException(message);
        }
        if (clContext.isClosed()) {
            String message = name != null
                    ? String.format("OpenCL context is closed for building buffer '%s'", name)
                    : "OpenCL context is closed for building buffer";
            logger.error(message);
            throw new IllegalArgumentException(message);
        }

        this.context = clContext;

        return (T) this;
    }

    @SuppressWarnings("unchecked")
    public T withDataClass(Class<? extends DataProcessor> newClass) {
        if (newClass == null) {
            String message = name != null
                    ? String.format("DataProcessor class cannot be null for building buffer '%s'", name)
                    : "DataProcessor class cannot be null for building buffer";
            logger.error(message);
            throw new IllegalArgumentException(message);
        }

        this.dataClass = newClass;
        return (T) this;
    }

    protected String getName () {
        if (name == null) {
            return "Unnamed buffer " + nameCounter.getAndIncrement();
        } else {
            String tempName = name;
            name = null;
            return tempName;
        }
    }

    protected ClContext getContext(){
        if (context == null) {
            String message = "OpenCL context cannot be null for building buffer";
            logger.error(message);
            throw new BufferInitializationException(message);
        }
        if (context.isClosed()) {
            String message = "OpenCL context is closed for building buffer";
            logger.error(message);
            throw new IllegalArgumentException(message);
        }
        return context;
    }

    protected DataProcessor getDataObject(){
        try {
            return (DataProcessor) dataClass.getConstructor().newInstance();
        } catch (Exception e) {
            String message = "Failed to instantiate data class: " + e.getMessage();
            logger.error(message);
            throw new BufferInitializationException(message);
        }
    }

    protected void registerBuffer(B buffer) {
        try {
            context.getBufferManager().registerBuffer(buffer);
        } catch (Exception e) {
            String message = String.format("Failed to instantiate data class %s: %s",
                    dataClass != null ? dataClass.getSimpleName() : "null",
                    e.getMessage());
            logger.error(message);

            try {
                buffer.destroy();
            } catch (Exception cleanupEx) {
                logger.error("Failed to cleanup buffer after registration failure", cleanupEx);
            }

            throw new BufferInitializationException(message);
        }
    }

    public abstract B build();
}
