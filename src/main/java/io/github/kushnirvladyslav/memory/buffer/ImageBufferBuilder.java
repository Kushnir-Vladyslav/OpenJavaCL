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
import io.github.kushnirvladyslav.memory.util.ImageChannelDataType;
import io.github.kushnirvladyslav.memory.util.ImageChannelOrder;
import io.github.kushnirvladyslav.memory.util.ImageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class ImageBufferBuilder
        <T extends ImageBufferBuilder<T, B>, B extends ImageBuffer>
        extends ClMemBufferBuilder<T, B>
{
    private static final Logger logger = LoggerFactory.getLogger(ImageBufferBuilder.class);

    private ImageChannelOrder imageChannelOrder = null;
    private ImageChannelDataType imageChannelDataType = null;

    private ImageType imageType = null;

    protected int imageWidth = 0;
    protected int imageHeight = 0;
    protected int imageDepth = 0;
    protected int imageArraySize = 0;
    protected int imageRowPitch = 0;
    protected int imageSlicePitch = 0;
    protected int numMipLevels = 0;
    protected int numSamples = 0;
    protected long memObject = 0;

    public T withImageChannelOrder(ImageChannelOrder imageChannelOrder){
        if (imageChannelOrder == null) {
            String message =  "ImageChannelOrder cannot be null for building buffer";
            logger.error(message);
            throw new IllegalArgumentException(message);
        }

        this.imageChannelOrder = imageChannelOrder;

        return (T) this;
    }

    public T withImageChannelDataType(ImageChannelDataType imageChannelDataType){
        if (imageChannelDataType == null) {
            String message =  "ImageChannelDataType cannot be null for building buffer";
            logger.error(message);
            throw new IllegalArgumentException(message);
        }

        this.imageChannelDataType = imageChannelDataType;

        return (T) this;
    }

    public T withImageType(ImageType imageType){
        if (imageType == null) {
            String message =  "ImageType cannot be null for building buffer";
            logger.error(message);
            throw new IllegalArgumentException(message);
        }

        this.imageType = imageType;

        return (T) this;
    }

    public T withImageWidth(int imageWidth){
        this.imageWidth = imageWidth;

        return (T) this;
    }

    protected T hiddenWithImageHeight(int imageHeight){
        this.imageHeight = imageHeight;

        return (T) this;
    }

    protected T hiddenWithImageDepth(int imageDepth){
        this.imageDepth = imageDepth;

        return (T) this;
    }

    protected T hiddenWithImageArraySize(int imageArraySize){
        this.imageArraySize = imageArraySize;

        return (T) this;
    }

    protected T hiddenWithImageRowPitch(int imageRowPitch){
        this.imageRowPitch = imageRowPitch;

        return (T) this;
    }

    protected T hiddenWithImageSlicePitch(int imageSlicePitch){
        this.imageSlicePitch = imageSlicePitch;

        return (T) this;
    }

    protected T hiddenWithNumMipLevels(int numMipLevels){
        this.numMipLevels = numMipLevels;

        return (T) this;
    }

    protected T hiddenWithNumSamples(int numSamples){
        this.numSamples = numSamples;

        return (T) this;
    }

    protected T hiddenWithMemObject(long memObject){
        this.memObject = memObject;

        return (T) this;
    }

    protected ImageChannelOrder getImageChannelOrder(){
        if (imageChannelOrder == null) {
            String message =  "ImageChannelOrder cannot be null for building buffer";
            logger.error(message);
            throw new BufferInitializationException(message);
        }

        return imageChannelOrder;
    }

    protected ImageChannelDataType getImageChannelDataType(){
        if (imageChannelDataType == null) {
            String message =  "ImageChannelDataType cannot be null for building buffer";
            logger.error(message);
            throw new BufferInitializationException(message);
        }

        return imageChannelDataType;
    }

    protected ImageType getImageType(){
        if (imageType == null) {
            String message =  "ImageType cannot be null for building buffer";
            logger.error(message);
            throw new BufferInitializationException(message);
        }

        return imageType;
    }

}
