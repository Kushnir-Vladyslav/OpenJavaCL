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

import io.github.kushnirvladyslav.exceptions.BufferOperationException;
import io.github.kushnirvladyslav.kernel.Kernel;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class KernelAwareBuffer extends BaseBuffer {
    private static final Logger logger = LoggerFactory.getLogger(KernelAwareBuffer.class);

    protected final Map<Long, Integer> kernelBindings;

    protected KernelAwareBuffer(BaseBufferBuilder<?, ?> builder) {
        super(builder);

        kernelBindings = new ConcurrentHashMap<>();
    }

    public void bindToKernel(Kernel kernel, int argIndex) {
        if (kernel == null) {
            String message = String.format("Kernel cannot be null for buffer '%s'", name);
            logger.error(message);
            throw new IllegalArgumentException(message);
        }
        bindToKernel(kernel.getClKernel(), argIndex);
    }

    public void bindToKernel(long kernel, int argIndex) {
        checkNotClosed();

        if (kernel == 0) {
            String message = String.format("Invalid kernel (0) for buffer '%s'", name);
            logger.error(message);
            throw new IllegalArgumentException(message);
        }

        if (argIndex < 0) {
            String message = String.format("Invalid argument index (%d) for buffer '%s'", argIndex, getName());
            logger.error(message);
            throw new IllegalArgumentException(message);
        }

        if(kernelBindings.containsKey(kernel)){
            if (kernelBindings.get(kernel) == argIndex){
                logger.warn("Buffer {} is already bound to kernel {}.", getName(), kernel);
                return;
            }
            String message = String.format("Buffer '%s' cannot be bound to kernel (%d) by argument number (%d) because it is already bound to argument (%d).",
                    getName(), kernel, kernelBindings.get(kernel), argIndex);
            logger.error(message);
            throw new IllegalArgumentException(message);
        }

        try {
            setKernelArg(kernel, argIndex);
            kernelBindings.put(kernel, argIndex);
            logger.debug("Buffer '{}' bound to kernel {} at index {}", getName(), kernel, argIndex);
        } catch (Exception e) {
            String message = String.format("Failed to bind buffer '%s' to kernel", getName());
            logger.error(message, e);
            throw new BufferOperationException(message, e);
        }
    }

    protected abstract void setKernelArg (long targetKernel, int argIndex);

    protected void rebindAllKernels(){
        checkNotClosed();

        if (kernelBindings.isEmpty()) {
            logger.debug("No kernel bindings to update for buffer '{}'", name);
            return;
        }

        for (Map.Entry<Long, Integer> binding : kernelBindings.entrySet()) {
            long kernelPtr = binding.getKey();
            int argIndex = binding.getValue();

            try {
                setKernelArg(kernelPtr, argIndex);
            } catch (Exception e) {
                String message = String.format("Failed to update kernel argument for kernel (%d) at index (%d) for buffer '%s'",
                        kernelPtr, argIndex, name);
                logger.error(message);
                throw new IllegalStateException(message, e);
            }
        }
    }

    public boolean unbindKernel(Kernel kernel) {
        if (kernel == null) {
            logger.warn("Attempted to unbind from null kernel for buffer '{}'", getName());
            return false;
        }
        return unbindKernel(kernel.getClKernel());
    }

    public boolean unbindKernel(long kernel) {
        checkNotClosed();

        if (kernel == 0) {
            logger.warn("Attempted to unbind from invalid kernel (0) for buffer '{}'", getName());
            return false;
        }

        Integer removedIndex = kernelBindings.remove(kernel);
        if (removedIndex != null) {
            logger.debug("Buffer '{}' unbound from kernel {} (was at index {})",
                    getName(), kernel, removedIndex);
            return true;
        }

        logger.debug("Buffer '{}' was not bound to kernel {}", name, kernel);
        return false;
    }

    public int getKernelArgIndex(Kernel kernel) {
        if (kernel == null) {
            return -1;
        }
        return getKernelArgIndex(kernel.getClKernel());
    }

    public int getKernelArgIndex(long kernel) {
        return kernelBindings.getOrDefault(kernel, -1);
    }

    public boolean isBoundToKernel(Kernel kernel) {
        if (kernel == null) {
            return false;
        }
        return isBoundToKernel(kernel.getClKernel());
    }

    public boolean isBoundToKernel(long kernel) {
        return kernelBindings.containsKey(kernel);
    }

    @Override
    protected void performDestroy(){
        if(kernelBindings != null) {
            kernelBindings.clear();
        }

        super.performDestroy();
    }
}
