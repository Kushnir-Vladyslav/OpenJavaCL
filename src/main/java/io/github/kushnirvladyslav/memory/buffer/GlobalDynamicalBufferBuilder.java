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

import io.github.kushnirvladyslav.ClContext;
import io.github.kushnirvladyslav.memory.data.DataProcessor;
import io.github.kushnirvladyslav.memory.util.DeviceMemoryAccess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class GlobalDynamicalBufferBuilder
        <T extends GlobalDynamicalBufferBuilder<T, B>, B extends GlobalDynamicalBuffer>
        extends CopyableGlobalBufferBuilder<T, B> {
    private static final Logger logger = LoggerFactory.getLogger(GlobalDynamicalBufferBuilder.class);

    public GlobalDynamicalBufferBuilder(){
        this.withCapacity(GlobalDynamicalBuffer.minCapacity);
        this.withDeviceMemoryAccess(DeviceMemoryAccess.READ_WRITE);
    }

    public <D extends DataProcessor> B setup(
            Class<D> newClass, ClContext clContext,
            int capacity){
        withOpenClContext(clContext);
        withDataClass(newClass);
        withCapacity(capacity);;

        return build();
    }

    public <D extends DataProcessor> B setup(
            String name, Class<D> newClass, ClContext clContext,
            int capacity) {
        withName(name);

        return setup(newClass, clContext, capacity);
    }

    @Override
    @SuppressWarnings("unchecked")
    public T withDeviceMemoryAccess(DeviceMemoryAccess deviceMemoryAccess) {
        if (deviceMemoryAccess == null) {
            String message =  "DeviceMemoryAccess cannot be null for building buffer";
            logger.error(message);
            throw new IllegalArgumentException(message);
        }

        if(deviceMemoryAccess != DeviceMemoryAccess.READ_WRITE){
            logger.warn("DynamicalGlobalBuffer forced to READ_WRITE to support internal reallocations. " +
                    "DeviceMemoryAccess change ignored.");
        }

        return (T) this;
    }

    @SuppressWarnings("unchecked")
    @Override
    public T withCapacity(int capacity){
        if(capacity < 1) {
            String message = String.format(
                    "Capacity must be positive for GlobalBuffer. Got: %d", capacity);
            logger.error(message);
            throw new IllegalArgumentException(message);
        }

        int dynamicCapacity = (int)(capacity * GlobalDynamicalBuffer.capacityMultiplier);

        if (dynamicCapacity < GlobalDynamicalBuffer.minCapacity) {
            String message = String.format(
                    "Capacity %d is too small for GlobalDynamicalBuffer. The capacity will be increased to a minimum of %d.",
                    capacity, GlobalDynamicalBuffer.minCapacity);
            logger.warn(message);

            dynamicCapacity = GlobalDynamicalBuffer.minCapacity;
        }

        super.withCapacity(dynamicCapacity);

        return (T) this;
    }

}
