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

import org.lwjgl.system.MemoryUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;

public abstract class GlobalBuffer
        extends ClMemBuffer{
    private static final Logger logger = LoggerFactory.getLogger(GlobalBuffer.class);

    protected ByteBuffer stagingBuffer;

    protected long flags = 0;
    protected long size = 0;
    protected int capacity;

    protected GlobalBuffer(GlobalBufferBuilder builder) {
        super(builder);

        this.capacity = builder.getCapacity();
        this.capacity |= builder.getDeviceMemoryAccess().getFlag();

        setup();

        if(builder.getStagingBuffer()){
            stagingBuffer = MemoryUtil.memAlloc(capacity * dataObject.getSizeStruct());
        }

        createClMem();
    }

    protected abstract void setup();

    @Override
    protected void createClMem() {
        if (capacity < 1) {
            String message = String.format("Buffer capacity must be positive, got %d for '%s'",
                    capacity, getName());
            logger.error(message);
            throw new IllegalStateException(message);
        }



    }

}
