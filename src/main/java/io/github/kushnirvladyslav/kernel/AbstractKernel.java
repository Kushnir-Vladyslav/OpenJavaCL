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

package io.github.kushnirvladyslav.kernel;

import io.github.kushnirvladyslav.ClContext;
import io.github.kushnirvladyslav.memory.buffer.KernelAwareBuffer;
import org.lwjgl.PointerBuffer;

public abstract class AbstractKernel {
    protected long kernel;
    protected long program;

    protected ClContext context;

    protected PointerBuffer global;
    protected PointerBuffer local;

    private int dimension;

    protected KernelAwareBuffer[] buffers;

    private int numberBuffers;

    protected void createProgram () {

    }

    public void init () {

    }

    public void run () {

    }

    public void destroy () {

    }
}
