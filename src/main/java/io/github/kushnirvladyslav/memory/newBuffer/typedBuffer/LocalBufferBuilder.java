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

package io.github.kushnirvladyslav.memory.newBuffer.typedBuffer;

import io.github.kushnirvladyslav.ClContext;
import io.github.kushnirvladyslav.memory.data.Data;
import io.github.kushnirvladyslav.memory.newBuffer.KernelAwareBufferBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LocalBufferBuilder
        extends KernelAwareBufferBuilder<LocalBufferBuilder, LocalBuffer> {
    private static final Logger logger = LoggerFactory.getLogger(LocalBufferBuilder.class);

    private int size = 1;

    public <B extends Data> LocalBuffer setup (Class<B> dataClass, ClContext context, int size) {
        withDataClass(dataClass);
        withOpenClContext(context);
        withSize(size);

        logger.debug("Setting up {} with parameter of type {}",
                getClass().getSimpleName(),
                dataClass.getSimpleName());

        return build();
    }

    public <B extends Data> LocalBuffer setup (String bufferName, Class<B> dataClass, ClContext context, int size) {
        withName(bufferName);

        return setup(dataClass, context, size);
    }

    public LocalBufferBuilder withSize(int size) {
        if(size < 1) {
            String message = String.format(
                    "Size must be positive for LocalBuffer. Got: %d", size);
            logger.error(message);
            throw new IllegalArgumentException(message);
        }
        this.size = size;
        return this;
    }

    protected int getSize(){
        return size;
    }

    @Override
    public LocalBuffer build() {
        LocalBuffer buffer = new LocalBuffer(this);
        registerBuffer(buffer);
        return buffer;
    }
}
