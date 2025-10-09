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

import io.github.kushnirvladyslav.OpenClContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;

public class BaseBufferBuilder <T extends BaseBufferBuilder<T, B>, B extends BaseBuffer>{
    private static final Logger logger = LoggerFactory.getLogger(BaseBuffer.class);

    private static final AtomicInteger nameCounter = new AtomicInteger(0);

    private String name;

    private OpenClContext context;
    private Class<?> clazz;

    public T withName(String name) {
        if (name == null || name.trim().isEmpty()) {
            String message = "Buffer name cannot be null or empty";
            logger.error(message);
            throw new IllegalArgumentException(message);
        }

        this.name = name;

        return (T) this;
    }

    public T withOpenClContext(OpenClContext clContext) {
        if (clContext == null) {
            String message;
            if (name != null) {
                message = String.format("OpenCL context cannot be null for building buffer '%s'", name);
            } else {
                message = String.format("OpenCL context cannot be null for building buffer");
            }
            logger.error(message);
            throw new IllegalArgumentException(message);
        }
        this.context = clContext;

        return (T) this;
    }

    public T withDataClass(Class<T> newClass) {
        if (newClass == null) {
            String message;
            if (name != null) {
                message = String.format("Data class cannot be null for building buffer '%s'", name);
            } else {
                message = String.format("Data class cannot be null for building buffer");
            }

            logger.error(message);
            throw new IllegalArgumentException(message);
        }

        this.clazz = newClass;
        return (T) this;
    }

}
