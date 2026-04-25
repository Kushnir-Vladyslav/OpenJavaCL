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

package io.github.kushnirvladyslav.memory.buffer.typedBuffer.globalBuffers;
import io.github.kushnirvladyslav.memory.buffer.GlobalStaticBuffer;
import io.github.kushnirvladyslav.memory.buffer.Readable;
import io.github.kushnirvladyslav.memory.buffer.Writable;

public class GlobalReadWriteStaticBuffer
        extends GlobalStaticBuffer
        implements Writable<GlobalReadWriteStaticBuffer>,
        Readable<GlobalReadWriteStaticBuffer> {

    public GlobalReadWriteStaticBuffer(GlobalReadWriteStaticBufferBuilder builder){
        super(builder);
    }
}
