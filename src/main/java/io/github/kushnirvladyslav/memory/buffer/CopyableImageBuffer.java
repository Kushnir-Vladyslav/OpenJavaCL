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

import io.github.kushnirvladyslav.exceptions.BufferIndexOutOfBoundsException;
import io.github.kushnirvladyslav.exceptions.BufferOperationException;
import io.github.kushnirvladyslav.memory.util.DeviceMemoryAccess;
import io.github.kushnirvladyslav.util.OpenCLErrorUtils;
import io.github.kushnirvladyslav.util.clEvent.ClCustomEvent;
import io.github.kushnirvladyslav.util.clEvent.ClEvent;
import io.github.kushnirvladyslav.util.clEvent.ClEventList;
import org.lwjgl.PointerBuffer;
import org.lwjgl.opencl.CL12;
import org.lwjgl.system.MemoryStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class CopyableImageBuffer
        extends ImageBuffer{
    private static final Logger logger = LoggerFactory.getLogger(CopyableImageBuffer.class);


    protected CopyableImageBuffer(ImageBufferBuilder<?, ?> builder) {
        super(builder);
    }

    /**
     * throw ...
     */
    protected abstract void validateSrc(ImageRegion srcBuildr);

    /**
     * throw ...
     */
    protected abstract void validateDst(ImageRegion srcBuildr, ImagePoint dstBuildr);

    /**
     * throw ...
     */
    protected abstract boolean intersectionCheck(ImageRegion region, ImagePoint pointer);

    protected static ClEvent copy(ImageRegion srcRegion, ImagePoint dstPoint, ClEventList events){
        if (srcRegion == null){
            String message = "Source copy data object cannot be null.";
            logger.error(message);
            throw new BufferOperationException(message);
        }

        if (dstPoint == null){
            String message = "Destination copy data object cannot be null.";
            logger.error(message);
            throw new BufferOperationException(message);
        }

        if(dstPoint == srcRegion){
            String message = "Source and destination are the same object — returning no-op event.";
            logger.warn(message);
            ClCustomEvent event = new ClCustomEvent(srcRegion.buffer.context);
            event.setComplete();
            return event;
        }

        CopyableImageBuffer src = srcRegion.buffer;
        CopyableImageBuffer dst = dstPoint.buffer;

        if (src == null || src.isClosed()) {
            String message = "Buffer, source not initialized or already closed.";
            logger.error(message);
            throw new BufferOperationException(message);
        }

        if (dst == null || dst.isClosed()) {
            String message = "Buffer, destination not initialized or already closed.";
            logger.error(message);
            throw new BufferOperationException(message);
        }

        if (!src.inSameContext(dst.context)){
            String message = "Buffers are created in different OpenCl contexts.";
            logger.error(message);
            throw new BufferOperationException(message);
        }

        if (src == dst && src.intersectionCheck(srcRegion, dstPoint)){
            String message = "Read/write areas overlap, undefined behavior.";
            logger.warn(message);
        }

        if (!src.imageType.canBeCopiedTo(dst.imageType)){
            String message = String.format("Data from '%s' cannot be copied to '%s'.",
                    src.imageType.name(), dst.imageType.name());
            logger.error(message);
            throw new BufferOperationException(message);
        }

        if (src.imageChannelOrder != dst.imageChannelOrder){
            String message = String.format("Buffers are created with different 'image channel order'." +
                    "Source is '%s', and destination is '%s'.",
                    src.imageChannelOrder.name(), dst.imageChannelOrder.name());
            logger.error(message);
            throw new BufferOperationException(message);
        }

        if(src.imageChannelDataType != dst.imageChannelDataType){
            String message = String.format("Buffers are created with different 'image channel data type'." +
                            "Source is '%s', and destination is '%s'.",
                    src.imageChannelDataType.name(), dst.imageChannelDataType.name());
            logger.error(message);
            throw new BufferOperationException(message);
        }

        if (!src.dataProcessor.getClass().equals(dst.dataProcessor.getClass())){
            String message = String.format(
                    "Buffer data types mismatch: source is %s, destination is %s. " +
                            "This will likely cause data corruption or undefined behavior.",
                    src.dataProcessor.getClass().getSimpleName(),
                    dst.dataProcessor.getClass().getSimpleName()
            );
            logger.error(message);
            throw new BufferOperationException(message);
        }

        if ((src.flags & DeviceMemoryAccess.WRITE_ONLY.getFlag()) != 0) {
            String message = String.format("Buffer '%s' cannot be read by device for copying.",
                    src.getName());
            logger.error(message);
            throw new BufferOperationException(message);
        }

        if ((dst.flags & DeviceMemoryAccess.READ_ONLY.getFlag()) != 0) {
            String message = String.format("Buffer '%s' cannot be write by device for copying.",
                    dst.getName());
            logger.error(message);
            throw new BufferOperationException(message);
        }

        src.validateSrc(srcRegion);
        dst.validateDst(srcRegion, dstPoint);

        try(MemoryStack stack = MemoryStack.stackPush()){
            PointerBuffer thisEvent = stack.mallocPointer(1);

            PointerBuffer srcOrigin = srcRegion.getOrigin(stack);
            PointerBuffer dstOrigin = dstPoint.getOrigin(stack);
            PointerBuffer region = srcRegion.getRegion(stack);

            int errorCode = CL12.clEnqueueCopyImage(
                    src.context.getCommandQueue(),
                    src.clMem,
                    dst.clMem,
                    srcOrigin,
                    dstOrigin,
                    region,
                    (events == null) ? null : events.getEventList(stack),
                    thisEvent.rewind()
            );

            if (events != null) events.releaseEvents();

            if (!OpenCLErrorUtils.isSuccess(errorCode)) {
                String message = String.format(
                        "Copying image from buffer '%S' to '%s' ended with an error: %s",
                        src.name, dst.name, OpenCLErrorUtils.getCLErrorString(errorCode));
                logger.error(message);
                throw new BufferOperationException(message);
            }

            return new ClEvent(thisEvent.get(0));
        }

    }

    public ClEvent copyTo(ImageRegion srcRegion, ImagePoint dstPoint, ClEventList events){
        if (srcRegion.buffer != this){
            throw new IllegalArgumentException(String.format(
                    "srcRegion belongs to '%s', but copyTo called on '%s'.",
                    srcRegion.buffer.getName(), getName()));
        }

        return copy(srcRegion, dstPoint, events);
    }

    public ClEvent copyTo(ImageRegion srcRegion, ImagePoint dstPoint){
        return copyTo(srcRegion, dstPoint, null);
    }

    public ClEvent copyFrom(ImageRegion srcRegion, ImagePoint dstPoint, ClEventList events){
        if (dstPoint.buffer != this){
            throw new IllegalArgumentException(String.format(
                    "dstPoint belongs to '%s', but copyFrom called on '%s'.",
                    dstPoint.buffer.getName(), getName()));
        }

        return copy(srcRegion, dstPoint, events);
    }

    public ClEvent copyFrom(ImageRegion srcRegion, ImagePoint dstPoint){
        return copyFrom(srcRegion, dstPoint, null);
    }

    public void copyToSync(ImageRegion srcRegion, ImagePoint dstPoint, ClEventList events){
        copyTo(srcRegion, dstPoint, events).waitForComplete();
    }

    public void copyToSync(ImageRegion srcRegion, ImagePoint dstPoint){
        copyToSync(srcRegion, dstPoint, null);
    }

    public void copyFromSync(ImageRegion srcRegion, ImagePoint dstPoint, ClEventList events){
        copyFrom(srcRegion, dstPoint, events).waitForComplete();
    }

    public void copyFromSync(ImageRegion srcRegion, ImagePoint dstPoint){
        copyFrom(srcRegion, dstPoint, null).waitForComplete();
    }

    protected static ClEvent copyFromGlobal(GlobalBuffer srcGlobal, ImageRegion dstRegion, int offset, ClEventList events){
        if (dstRegion == null){
            String message = "Destination copy data object cannot be null.";
            logger.error(message);
            throw new BufferOperationException(message);
        }

        CopyableImageBuffer dstImage = dstRegion.buffer;

        if (dstImage == null || dstImage.isClosed()) {
            String message = "Buffer, destination not initialized or already closed.";
            logger.error(message);
            throw new BufferOperationException(message);
        }

        if (srcGlobal == null || srcGlobal.isClosed()) {
            String message = "Buffer, source not initialized or already closed.";
            logger.error(message);
            throw new BufferOperationException(message);
        }

        if (offset < 0) {
            String message = "Offsets must be non-negative.";
            logger.error(message);
            throw new BufferOperationException(message);
        }

        if (!dstImage.inSameContext(srcGlobal.context)){
            String message = "Buffers are created in different OpenCl contexts.";
            logger.error(message);
            throw new BufferOperationException(message);
        }

        if ((dstImage.flags & DeviceMemoryAccess.READ_ONLY.getFlag()) != 0) {
            String message = String.format("Buffer '%s' cannot be written by device for copying.",
                    dstImage.getName());
            logger.error(message);
            throw new BufferOperationException(message);
        }

        if ((srcGlobal.flags & DeviceMemoryAccess.WRITE_ONLY.getFlag()) != 0) {
            String message = String.format("Buffer '%s' cannot be write by device for copying.",
                    srcGlobal.getName());
            logger.error(message);
            throw new BufferOperationException(message);
        }

        int dataSize = (int) Math.ceil((double)dstRegion.getDataSize() / srcGlobal.dataProcessor.getSizeStruct());

        if(dataSize > srcGlobal.capacity - offset){
            String message = String.format(
                    "Attempt to read outside the source buffer while copying. Buffer capacity '%s' is %d, attempted to write to %d.",
                    srcGlobal.name, srcGlobal.capacity, offset + dataSize);
            logger.error(message);
            throw new BufferOperationException(message);
        }

        try(MemoryStack stack = MemoryStack.stackPush()){
            PointerBuffer thisEvent = stack.mallocPointer(1);

            PointerBuffer dstOrigin = dstRegion.getOrigin(stack);
            PointerBuffer region = dstRegion.getRegion(stack);

            int errorCode = CL12.clEnqueueCopyBufferToImage(
                    dstImage.context.getCommandQueue(),
                    srcGlobal.clMem,
                    dstImage.clMem,
                    (long) offset * srcGlobal.dataProcessor.getSizeStruct(),
                    dstOrigin,
                    region,
                    (events == null) ? null : events.getEventList(stack),
                    thisEvent.rewind()
            );

            if (events != null) events.releaseEvents();

            if (!OpenCLErrorUtils.isSuccess(errorCode)) {
                String message = String.format(
                        "Copying image from buffer '%S' to '%s' ended with an error: %s",
                        srcGlobal.name, dstImage.name, OpenCLErrorUtils.getCLErrorString(errorCode));
                logger.error(message);
                throw new BufferOperationException(message);
            }

            return new ClEvent(thisEvent.get(0));
        }
    }

    public ClEvent copyFromGlobalBuffer(GlobalBuffer srcGlobal, ImageRegion dstRegion, int offset, ClEventList events){
        if (dstRegion.buffer != this){
            throw new IllegalArgumentException(String.format(
                    "dstPoint belongs to '%s', but copyFrom called on '%s'.",
                    dstRegion.buffer.getName(), getName()));
        }

        return copyFromGlobal(srcGlobal, dstRegion, offset, events);
    }

    public ClEvent copyFromGlobalBuffer(GlobalBuffer srcGlobal, ImageRegion dstRegion, int offset){
        return copyFromGlobalBuffer(srcGlobal, dstRegion, offset, null);
    }

    public  ClEvent copyFromGlobalBuffer(GlobalBuffer srcGlobal, ImageRegion dstRegion, ClEventList events){
        return copyFromGlobalBuffer(srcGlobal, dstRegion, 0, events);
    }

    public ClEvent copyFromGlobalBuffer(GlobalBuffer srcGlobal, ImageRegion dstRegion){
        return copyFromGlobalBuffer(srcGlobal, dstRegion, 0, null);
    }

    public void copyFromGlobalBufferSync(GlobalBuffer srcGlobal, ImageRegion dstRegion, int offset, ClEventList events){
        copyFromGlobalBuffer(srcGlobal, dstRegion, offset, events).waitForComplete();
    }

    public void copyFromGlobalBufferSync(GlobalBuffer srcGlobal, ImageRegion dstRegion, int offset){
        copyFromGlobalBufferSync(srcGlobal, dstRegion, offset, null);
    }

    public void copyFromGlobalBufferSync(GlobalBuffer srcGlobal, ImageRegion dstRegion, ClEventList events){
        copyFromGlobalBufferSync(srcGlobal, dstRegion, 0, events);
    }

    public void copyFromGlobalBufferSync(GlobalBuffer srcGlobal, ImageRegion dstRegion){
        copyFromGlobalBufferSync(srcGlobal, dstRegion, 0, null);
    }

    protected static ClEvent copyToGlobal(ImageRegion srcRegion, GlobalBuffer dstGlobal, int offset, ClEventList events){
        if (srcRegion == null){
            String message = "Source copy data object cannot be null.";
            logger.error(message);
            throw new BufferOperationException(message);
        }

        CopyableImageBuffer srcImage = srcRegion.buffer;

        if (srcImage == null || srcImage.isClosed()) {
            String message = "Buffer, source not initialized or already closed.";
            logger.error(message);
            throw new BufferOperationException(message);
        }

        if (dstGlobal == null || dstGlobal.isClosed()) {
            String message = "Buffer, destination not initialized or already closed.";
            logger.error(message);
            throw new BufferOperationException(message);
        }

        if (offset < 0) {
            String message = "Offsets must be non-negative.";
            logger.error(message);
            throw new BufferOperationException(message);
        }

        if (!srcImage.inSameContext(dstGlobal.context)){
            String message = "Buffers are created in different OpenCl contexts.";
            logger.error(message);
            throw new BufferOperationException(message);
        }

        if ((srcImage.flags & DeviceMemoryAccess.WRITE_ONLY.getFlag()) != 0) {
            String message = String.format("Buffer '%s' cannot be read by device for copying.",
                    srcImage.getName());
            logger.error(message);
            throw new BufferOperationException(message);
        }

        if ((dstGlobal.flags & DeviceMemoryAccess.READ_ONLY.getFlag()) != 0) {
            String message = String.format("Buffer '%s' cannot be write by device for copying.",
                    dstGlobal.getName());
            logger.error(message);
            throw new BufferOperationException(message);
        }

        int dataSize = (int) Math.ceil((double)srcRegion.getDataSize() / dstGlobal.dataProcessor.getSizeStruct());

        if(dataSize > dstGlobal.capacity - offset){
            try{
            dstGlobal.changeCapacity(offset + dataSize, events);
            }
            catch (BufferOperationException e) {
                String message = String.format(
                        "Attempted to write outside destination buffer while copying. Buffer capacity '%s' is %d, attempted to write to %d.",
                        dstGlobal.name, dstGlobal.capacity, offset + dataSize);
                logger.error(message);
                throw new BufferIndexOutOfBoundsException(message);
            }
        }

        try(MemoryStack stack = MemoryStack.stackPush()){
            PointerBuffer thisEvent = stack.mallocPointer(1);

            PointerBuffer srcOrigin = srcRegion.getOrigin(stack);
            PointerBuffer region = srcRegion.getRegion(stack);

            int errorCode = CL12.clEnqueueCopyImageToBuffer(
                    srcImage.context.getCommandQueue(),
                    srcImage.clMem,
                    dstGlobal.clMem,
                    srcOrigin,
                    region,
                    (long) offset * dstGlobal.dataProcessor.getSizeStruct(),
                    (events == null) ? null : events.getEventList(stack),
                    thisEvent.rewind()
            );

            if (events != null) events.releaseEvents();

            if (!OpenCLErrorUtils.isSuccess(errorCode)) {
                String message = String.format(
                        "Copying image from buffer '%S' to '%s' ended with an error: %s",
                        srcImage.name, dstGlobal.name, OpenCLErrorUtils.getCLErrorString(errorCode));
                logger.error(message);
                throw new BufferOperationException(message);
            }

            return new ClEvent(thisEvent.get(0));
        }
    }

    public ClEvent copyToGlobalBuffer(ImageRegion srcRegion, GlobalBuffer dstGlobal, int offset, ClEventList events){
        if (srcRegion.buffer != this){
            throw new IllegalArgumentException(String.format(
                    "srcRegion belongs to '%s', but copyTo called on '%s'.",
                    srcRegion.buffer.getName(), getName()));
        }

        return copyToGlobal(srcRegion, dstGlobal, offset, events);
    }

    public ClEvent copyToGlobalBuffer(ImageRegion srcRegion, GlobalBuffer dstGlobal, int offset){
        return copyToGlobalBuffer(srcRegion, dstGlobal, offset, null);
    }

    public ClEvent copyToGlobalBuffer(ImageRegion srcRegion, GlobalBuffer dstGlobal, ClEventList events){
        return copyToGlobalBuffer(srcRegion, dstGlobal, 0, events);
    }

    public ClEvent copyToGlobalBuffer(ImageRegion srcRegion, GlobalBuffer dstGlobal){
        return copyToGlobalBuffer(srcRegion, dstGlobal, 0, null);
    }

    public void copyToGlobalBufferSync(ImageRegion srcRegion, GlobalBuffer dstGlobal, int offset, ClEventList events){
        copyToGlobalBuffer(srcRegion, dstGlobal, offset, events).waitForComplete();
    }

    public void copyToGlobalBufferSync(ImageRegion srcRegion, GlobalBuffer dstGlobal, int offset){
        copyToGlobalBufferSync(srcRegion, dstGlobal, offset, null);
    }

    public void copyToGlobalBufferSync(ImageRegion srcRegion, GlobalBuffer dstGlobal, ClEventList events){
        copyToGlobalBufferSync(srcRegion, dstGlobal, 0, events);
    }

    public void copyToGlobalBufferSync(ImageRegion srcRegion, GlobalBuffer dstGlobal){
        copyToGlobalBufferSync(srcRegion, dstGlobal, 0, null);
    }

    protected abstract static class ImagePoint {
        protected CopyableImageBuffer buffer;

        protected long[] origin = {0, 0, 0};

        protected ImagePoint(CopyableImageBuffer buffer){
            this.buffer = buffer;
        }

        protected PointerBuffer getOrigin(MemoryStack stack){
            return stack.mallocPointer(3).put(origin).rewind();
        }

        protected int getOriginX(){
            return (int) origin[0];
        }

        protected int getOriginY(){
            return (int) origin[1];
        }

        protected int getOriginZ(){
            return (int) origin[2];
        }
    }

    protected abstract static class ImageRegion
            extends ImagePoint {

        protected long[] region = {0, 0, 0};

        protected ImageRegion(CopyableImageBuffer buffer) {
            super(buffer);
        }

        protected PointerBuffer getRegion(MemoryStack stack){
            return stack.mallocPointer(3).put(region).rewind();
        }

        protected abstract long getDataSize();

        protected int getRegionX(){
            return (int) region[0];
        }

        protected int getRegionY(){
            return (int) region[1];
        }

        protected int getRegionZ(){
            return (int) region[2];
        }
    }
}
