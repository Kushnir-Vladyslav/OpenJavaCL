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

public abstract class GlobalStaticBufferBuilder
        <T extends GlobalStaticBufferBuilder<T, B>, B extends GlobalStaticBuffer>
        extends CopyableGlobalBufferBuilder<T, B> {

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

    public <D extends DataProcessor> B setup(
            Class<D> newClass, ClContext clContext,
            DeviceMemoryAccess deviceMemoryAccess,
            int capacity) {
        withDeviceMemoryAccess(deviceMemoryAccess);

        return setup(newClass, clContext, capacity);
    }

    public <D extends DataProcessor> B setup(
            Class<D> newClass, ClContext clContext,
            int capacity, boolean stagingBuffer) {
        withStagingBuffer(stagingBuffer);

        return setup(newClass, clContext, capacity);
    }

    public <D extends DataProcessor> B setup(
            String name, Class<D> newClass, ClContext clContext,
            DeviceMemoryAccess deviceMemoryAccess,
            int capacity) {
        withName(name);

        return setup(newClass, clContext, deviceMemoryAccess, capacity);
    }

    public <D extends DataProcessor> B setup(
            String name, Class<D> newClass, ClContext clContext,
            int capacity, boolean stagingBuffer) {
        withName(name);

        return setup(newClass, clContext, capacity, stagingBuffer);
    }

    public <D extends DataProcessor> B setup(
            Class<D> newClass, ClContext clContext,
            DeviceMemoryAccess deviceMemoryAccess,
            int capacity, boolean stagingBuffer) {
        withDeviceMemoryAccess(deviceMemoryAccess);

        return setup(newClass, clContext, capacity, stagingBuffer);
    }

    public <D extends DataProcessor> B setup(
            String name, Class<D> newClass, ClContext clContext,
            DeviceMemoryAccess deviceMemoryAccess,
            int capacity, boolean stagingBuffer){
        withName(name);

        return setup(newClass, clContext, deviceMemoryAccess, capacity, stagingBuffer);
    }
}
