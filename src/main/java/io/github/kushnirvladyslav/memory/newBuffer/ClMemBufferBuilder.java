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
import io.github.kushnirvladyslav.memory.util.HostMemoryAccess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class ClMemBufferBuilder
        <T extends ClMemBufferBuilder<T, B>, B extends ClMemBuffer>
        extends KernelAwareBufferBuilder<T, B>
{
    private static final Logger logger = LoggerFactory.getLogger(ClMemBufferBuilder.class);

    private DeviceMemoryAccess deviceMemoryAccess = DeviceMemoryAccess.READ_WRITE;
    private HostMemoryAccess hostMemoryAccess = HostMemoryAccess.READ_WRITE;

    @SuppressWarnings("unchecked")
    public T withDeviceMemoryAccess(DeviceMemoryAccess deviceMemoryAccess) {
        if (deviceMemoryAccess == null) {
            String message =  "DeviceMemoryAccess cannot be null for building buffer";
            logger.error(message);
            throw new IllegalArgumentException(message);
        }

        this.deviceMemoryAccess = deviceMemoryAccess;

        return (T) this;
    }

    @SuppressWarnings("unchecked")
    protected T withHostMemoryAccess(HostMemoryAccess hostMemoryAccess) {
        if (hostMemoryAccess == null) {
            String message =  "DeviceMemoryAccess cannot be null for building buffer";
            logger.error(message);
            throw new IllegalArgumentException(message);
        }

        this.hostMemoryAccess = hostMemoryAccess;

        return (T) this;
    }

    protected DeviceMemoryAccess getDeviceMemoryAccess(){
        return deviceMemoryAccess;
    }

    protected HostMemoryAccess getHostMemoryAccess(){
        return hostMemoryAccess;
    }
}
