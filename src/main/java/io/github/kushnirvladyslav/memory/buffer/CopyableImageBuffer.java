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

import io.github.kushnirvladyslav.util.clEvent.ClEvent;
import org.lwjgl.system.MemoryStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.LongBuffer;

public abstract class CopyableImageBuffer
        extends ImageBuffer{
    private static final Logger logger = LoggerFactory.getLogger(CopyableImageBuffer.class);


    protected CopyableImageBuffer(ImageBufferBuilder<?, ?> builder) {
        super(builder);
    }

    /**
     * throw ...
     */
    protected abstract void validateSrc(SourceBuilder srcBuildr);

    /**
     * throw ...
     */
    protected abstract void validateDst(SourceBuilder srcBuildr, DestinationBuffer dstBuildr);


    public ClEvent copy(SourceBuilder src, DestinationBuffer dst){
        if (src == null){
            //throw
        }

        if (dst == null){
            //throw
        }

        CopyableImageBuffer srcBuffer = src.srcBuffer;
        CopyableImageBuffer dstBuffer = dst.dstBuffer;

        if (!srcBuffer.imageType.canBeCopiedTo(dstBuffer.imageType)){
            //throw
        }

        srcBuffer.validateSrc(src);
        dstBuffer.validateDst(src, dst);

        if (srcBuffer.imageChannelOrder != dstBuffer.imageChannelOrder){
            //throw
        }

        if(srcBuffer.imageChannelDataType != dstBuffer.imageChannelDataType){
            //throw
        }


        try(MemoryStack stack = MemoryStack.stackPush()){

            //copy logic

        }

    }

    protected abstract static class SourceBuilder{
        protected CopyableImageBuffer srcBuffer;

        protected long[] srcOrigin = {0, 0, 0};
        protected long[] region = {0, 0, 0};

        protected SourceBuilder(CopyableImageBuffer srcBuffer){
            this.srcBuffer = srcBuffer;
        }

        protected LongBuffer getOrigin(MemoryStack stack){
            return stack.mallocLong(3).put(srcOrigin);
        }

        protected LongBuffer getRegion(MemoryStack stack){
            return stack.mallocLong(3).put(region);
        }
    }

    protected abstract static class DestinationBuffer{
        protected CopyableImageBuffer dstBuffer;

        protected long[] srcOrigin = {0, 0, 0};

        protected DestinationBuffer(CopyableImageBuffer dstBuffer){
            this.dstBuffer = dstBuffer;
        }

        protected LongBuffer getOrigin(MemoryStack stack){
            return stack.mallocLong(3).put(srcOrigin);
        }
    }
}
