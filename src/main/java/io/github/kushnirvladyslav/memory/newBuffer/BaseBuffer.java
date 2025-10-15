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

public abstract class BaseBuffer implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(BaseBuffer.class);

    protected final String name;
    protected StatusCL status;

    protected final OpenClContext context;
    protected final Data dataObject;

    protected BaseBuffer(BaseBufferBuilder builder) {
        this.name = builder.getName();
        this.context = builder.getContext();
        this.dataObject = builder.getDataObject();

        this.status = StatusCL.RUNNING;
    }

    public String getName() {
        checkNotClosed();
        return name;
    }

    public boolean isClosed() {
        return status == StatusCL.CLOSED;
    }

    public OpenClContext getContext() {
        checkNotClosed();
        return context;
    }

    public boolean inSameContext(OpenClContext context) {
        checkNotClosed();
        return this.context.equals(context);
    }

    public Class<? extends Data> getDataClass(){
        checkNotClosed();
        return dataObject.getClass();
    }

    protected void checkNotClosed() {
        if (isClosed()) {
            throw new BufferDestructionException(
                    String.format("Buffer '%s' has been closed and cannot be used", name));
        }
    }

    public final void destroy () {
        if(status == StatusCL.CLOSED) {
            throw new BufferDestructionException("Buffer \"" + name + "\" has been closed.");
        }

        performDestroy();
        unregisterFromContext();

        status = StatusCL.CLOSED;
    }

    protected void performDestroy() {
    }

    private void  unregisterFromContext() {
        try {
            if (context != null && !context.isClosed() && context.getBufferManager() != null) {
                context.getBufferManager().remove(this);
                logger.debug("Unregistered buffer '{}' from context", name);
            }
        } catch (Exception e) {
            logger.error("Error unregistering buffer '{}' from context", name, e);
        }
    }

    @Override
    public String toString() {
        return String.format("%s[name='%s', status=%s, dataClass=%s]",
                getClass().getSimpleName(),
                name,
                status,
                dataObject.getClass().getSimpleName());
    }

    @Override
    public void close(){
        destroy();
    }

}
