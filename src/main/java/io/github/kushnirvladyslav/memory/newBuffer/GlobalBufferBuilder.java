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

import io.github.kushnirvladyslav.memory.util.DeviceMemoryAccess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class GlobalBufferBuilder
        <T extends GlobalBufferBuilder<T, B>, B extends GlobalBuffer>
        extends ClMemBufferBuilder<T, B> {
    private static final Logger logger = LoggerFactory.getLogger(GlobalBufferBuilder.class);

    private DeviceMemoryAccess deviceMemoryAccess = DeviceMemoryAccess.WRIGHT_ONLY;

    private int capacity = 1;

    private boolean stagingBuffer = false;

    public T withDeviceMemoryAccess(DeviceMemoryAccess deviceMemoryAccess) {
        if (deviceMemoryAccess == null) {
            String message =  "DeviceMemoryAccess cannot be null for building buffer";
            logger.error(message);
            throw new IllegalArgumentException(message);
        }

        this.deviceMemoryAccess = deviceMemoryAccess;

        return (T) this;
    }

    public T withCapacity(int capacity) {
        if(capacity < 1) {
            String message = String.format(
                    "Capacity must be positive for LocalBuffer. Got: %d", capacity);
            logger.error(message);
            throw new IllegalArgumentException(message);
        }
        this.capacity = capacity;
        return (T) this;
    }

    public T withStagingBuffer(boolean stagingBuffer){
        this.stagingBuffer = stagingBuffer;
        return (T) this;
    }

    protected DeviceMemoryAccess getDeviceMemoryAccess(){
        return deviceMemoryAccess;
    }

    protected int getCapacity(){
        return capacity;
    }

    protected boolean getStagingBuffer() {
        return stagingBuffer;
    }
}
