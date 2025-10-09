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
import io.github.kushnirvladyslav.exceptions.BufferDestructionException;
import io.github.kushnirvladyslav.memory.data.Data;
import io.github.kushnirvladyslav.util.StatusCL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;

public abstract class BaseBuffer {
    private static final Logger logger = LoggerFactory.getLogger(BaseBuffer.class);

    protected String name;
    protected StatusCL status;

    protected OpenClContext context;
    protected Data dataObject;

    protected BaseBuffer(BaseBufferBuilder builder) {
        this.name = builder.getName();
        this.context = builder.getContext();
        this.dataObject = builder.getDataObject();

        this.status = StatusCL.RUNNING;
    }

    public void destroy () {
        if(status == StatusCL.CLOSED) {
            throw new BufferDestructionException("Buffer \"" + name + "\" has been closed.");
        }

        context.getBufferManager().remove(this);

        name = null;
        context = null;
        dataObject = null;

        status = StatusCL.CLOSED;
    }
}
