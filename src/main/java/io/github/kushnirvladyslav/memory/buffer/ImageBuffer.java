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

import io.github.kushnirvladyslav.exceptions.BufferInitializationException;
import io.github.kushnirvladyslav.exceptions.BufferOperationException;
import io.github.kushnirvladyslav.memory.util.ImageChannelDataType;
import io.github.kushnirvladyslav.memory.util.ImageChannelOrder;
import io.github.kushnirvladyslav.memory.util.ImageType;
import io.github.kushnirvladyslav.util.CLVersion;
import io.github.kushnirvladyslav.util.OpenCLErrorUtils;
import org.lwjgl.opencl.CL12;
import org.lwjgl.opencl.CLImageDesc;
import org.lwjgl.opencl.CLImageFormat;
import org.lwjgl.system.MemoryStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.IntBuffer;

public abstract class ImageBuffer
        extends ClMemBuffer{
    private static final Logger logger = LoggerFactory.getLogger(ImageBuffer.class);

    private ImageChannelOrder imageChannelOrder;
    private ImageChannelDataType imageChannelDataType;

    private ImageType imageType;

    private int imageWidth;
    private int imageHeight;
    private int imageDepth;
    private int imageArraySize;
    private int imageRowPitch;
    private int imageSlicePitch;
    private int numMipLevels;
    private int numSamples;

    protected ImageBuffer(ImageBufferBuilder<?, ?> builder) {
        super(builder);

        if (!context.getDevice().getOpenCLVersion().isAtLeast(CLVersion.OPENCL_1_2)){
            String message = String.format("The device '%s' must support at least version OpenCl 1.2. Now its '%s'.",
                    context.getDevice().getName(),
                    context.getDevice().getOpenCLVersion());
            logger.error(message);
            throw new IllegalStateException(message);
        }

        imageChannelOrder = builder.getImageChannelOrder();
        imageChannelDataType = builder.getImageChannelDataType();

        imageType = builder.getImageType();

        imageWidth = builder.imageWidth;
        imageHeight = builder.imageHeight;
        imageDepth = builder.imageDepth;
        imageArraySize = builder.imageArraySize;
        imageRowPitch = builder.imageRowPitch;
        imageSlicePitch = builder.imageSlicePitch;
        numMipLevels = builder.numMipLevels;
        numSamples = builder.numSamples;

        clMem = createClMem();
    }

    protected abstract void validateDimensions();

    @Override
    protected long createClMem() {

        validateDimensions();

        long newClMem;
        try (MemoryStack stack = MemoryStack.stackPush()) {

            IntBuffer errorCode = stack.mallocInt(1);

            if(!isFormatSupported(stack)){
                String message = String.format(
                        "OpenCL device '%s' don't support the combination of '%s' and '%s' type in  buffer '%s'.",
                        context.getDevice().getName(),
                        imageChannelOrder.name(),
                        imageChannelDataType,
                        getName());
                logger.error(message);
                throw new BufferInitializationException(message);
            }

            CLImageFormat format = CLImageFormat.malloc(stack);

            format.image_channel_order(imageChannelOrder.getFlag());
            format.image_channel_data_type(imageChannelDataType.getFlag());

            CLImageDesc desc = CLImageDesc.malloc(stack)
                    .image_type(imageType.getFlag())
                    .image_width(imageWidth)
                    .image_height(imageHeight)
                    .image_depth(imageDepth)
                    .image_array_size(imageArraySize)
                    .image_row_pitch(imageRowPitch)
                    .image_slice_pitch(imageSlicePitch)
                    .num_mip_levels(numMipLevels)
                    .num_samples(numSamples);


            newClMem = CL12.clCreateImage(
                    context.getContext(),
                    flags,
                    format,
                    desc,
                    (IntBuffer) null,
                    errorCode
            );

            if (!OpenCLErrorUtils.isSuccess(errorCode.get(0))) {
                String message = String.format(
                        "Failed to create OpenCL image buffer '%s' with error: '%s'",
                        getName(), OpenCLErrorUtils.getCLErrorString(errorCode.get(0)));
                logger.error(message);
                throw new BufferInitializationException(message);
            }

            if (newClMem == 0) {
                String message = String.format("Failed to create OpenCL image buffer for '%s'", getName());
                logger.error(message);
                throw new BufferInitializationException(message);
            }
        }

        return newClMem;
    }

    private boolean isFormatSupported(MemoryStack stack){
        IntBuffer numFormatsBuffer = stack.mallocInt(1);

        int errorCode = CL12.clGetSupportedImageFormats(context.getContext(), flags, imageType.getFlag(), null, numFormatsBuffer);

        if (!OpenCLErrorUtils.isSuccess(errorCode)) {
            String message = String.format(
                    "OpenCL cannot check support image format for buffer '%s': error - %s",
                    getName(), OpenCLErrorUtils.getCLErrorString(errorCode));
            logger.error(message);
            throw new BufferOperationException(message, errorCode);
        }

        int count = numFormatsBuffer.get(0);

        if (count == 0) return false;

        CLImageFormat.Buffer formats = CLImageFormat.malloc(count, stack);
        errorCode = CL12.clGetSupportedImageFormats(context.getContext(), flags, imageType.getFlag(), formats, (IntBuffer)null);

        if (!OpenCLErrorUtils.isSuccess(errorCode)) {
            String message = String.format(
                    "OpenCL cannot check support image format for buffer '%s': error - %s",
                    getName(), OpenCLErrorUtils.getCLErrorString(errorCode));
            logger.error(message);
            throw new BufferOperationException(message, errorCode);
        }

        for (int i = 0; i < count; i++) {
            if (formats.get(i).image_channel_order() == imageChannelOrder.getFlag() &&
                    formats.get(i).image_channel_data_type() == imageChannelDataType.getFlag()) {
                return true;
            }
        }

        return false;
    }
}
