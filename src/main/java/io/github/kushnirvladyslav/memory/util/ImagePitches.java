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

package io.github.kushnirvladyslav.memory.util;

import io.github.kushnirvladyslav.ClContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ImagePitches {
    private static final Logger logger = LoggerFactory.getLogger(ImagePitches.class);

    protected long[] region;
    protected int readRowPitch;
    protected int readSlicePitch;
    protected int writeRowPitch;
    protected int writeSlicePitch;

    public ImagePitches(long[] region, int readRowPitch, int readSlicePitch, int writeRowPitch, int writeSlicePitch){
        if(region == null) {
            String message = "Region, can`t be null.";
            logger.error(message);
            throw new NullPointerException(message);
        }

        if(region.length != 3){
            String message = "Region, mast be '3' length.";
            logger.error(message);
            throw new NullPointerException(message);
        }

        this.region = region;


        if(readRowPitch < 0){
            String message = "readRowPitch, mast be positive.";
            logger.error(message);
            throw new NullPointerException(message);
        }

        this.readRowPitch = readRowPitch;


        if(readSlicePitch < 0){
            String message = "readSlicePitch, mast be positive.";
            logger.error(message);
            throw new NullPointerException(message);
        }

        this.readSlicePitch = readSlicePitch;


        if(writeRowPitch < 0){
            String message = "writeRowPitch, mast be positive.";
            logger.error(message);
            throw new NullPointerException(message);
        }

        this.writeRowPitch = writeRowPitch;


        if(writeSlicePitch < 0){
            String message = "writeSlicePitch, mast be positive.";
            logger.error(message);
            throw new NullPointerException(message);
        }

        this.writeSlicePitch = writeSlicePitch;
    }

    public int getRegionX(){
        return (int) region[0];
    }

    public int getRegionY(){
        return (int) region[1];
    }

    public int getRegionZ(){
        return (int) region[2];
    }

    public int getReadRowPitch() {
        return readRowPitch;
    }

    public int getReadSlicePitch() {
        return readSlicePitch;
    }

    public int getWriteRowPitch() {
        return writeRowPitch;
    }

    public int getWriteSlicePitch() {
        return writeSlicePitch;
    }
}
