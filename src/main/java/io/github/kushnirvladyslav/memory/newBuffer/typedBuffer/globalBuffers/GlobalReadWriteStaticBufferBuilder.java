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

package io.github.kushnirvladyslav.memory.newBuffer.typedBuffer.globalBuffers;
import io.github.kushnirvladyslav.memory.newBuffer.GlobalStaticBufferBuilder;
import io.github.kushnirvladyslav.memory.util.HostMemoryAccess;

public class GlobalReadWriteStaticBufferBuilder
        extends GlobalStaticBufferBuilder<GlobalReadWriteStaticBufferBuilder, GlobalReadWriteStaticBuffer>
{

    public GlobalReadWriteStaticBufferBuilder(){
        withHostMemoryAccess(HostMemoryAccess.READ_WRITE);
    }

    @Override
    public GlobalReadWriteStaticBuffer build() {
        GlobalReadWriteStaticBuffer buffer = new GlobalReadWriteStaticBuffer(this);
        registerBuffer(buffer);
        return buffer;
    }
}
