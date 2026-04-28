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

import org.lwjgl.opencl.CL12;

public enum ImageType {
    IMAGE2D(CL12.CL_MEM_OBJECT_IMAGE2D){
        @Override
        public boolean canBeCopiedTo(ImageType imageType){

            return imageType == IMAGE2D || imageType == IMAGE2D_ARRAY;
        }
    },
    IMAGE3D(CL12.CL_MEM_OBJECT_IMAGE3D){
        @Override
        public boolean canBeCopiedTo(ImageType imageType){

            return imageType == IMAGE3D;
        }
    },
    IMAGE2D_ARRAY(CL12.CL_MEM_OBJECT_IMAGE2D_ARRAY){
        @Override
        public boolean canBeCopiedTo(ImageType imageType){

            return imageType == IMAGE2D || imageType == IMAGE2D_ARRAY;
        }
    },
    IMAGE1D(CL12.CL_MEM_OBJECT_IMAGE1D){
        @Override
        public boolean canBeCopiedTo(ImageType imageType){

            return imageType == IMAGE1D;
        }
    };

    ImageType(int flag) {
        this.flag = flag;
    }

    private final int flag;

    public int getFlag() {
        return flag;
    }

    public abstract boolean canBeCopiedTo(ImageType imageType);
}
