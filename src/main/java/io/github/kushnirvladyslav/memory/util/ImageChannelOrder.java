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
import org.lwjgl.opencl.CL30;
import org.lwjgl.opencl.KHRGLDepthImages;

public enum ImageChannelOrder {
    RED_ONLY(CL12.CL_R, 1),
    ALPHA_ONLY(CL12.CL_A, 1),
    RED_GREEN(CL12.CL_RG, 2),
    RED_ALPHA(CL12.CL_RA, 2),
    RGBA(CL12.CL_RGBA, 4),
    BGRA(CL12.CL_BGRA, 4),
    ARGB(CL12.CL_ARGB, 4),
    INTENSITY(CL12.CL_INTENSITY, 1),
    LUMINANCE(CL12.CL_LUMINANCE, 1),
    DEPTH(KHRGLDepthImages.CL_DEPTH_STENCIL, 1);

    ImageChannelOrder(int flag, int numOfChannel) {
        this.flag = flag;
        this.numOfChannel = numOfChannel;
    }

    private final int flag;
    private final int numOfChannel;

    public int getFlag() {
        return flag;
    }

    public int getNumOfChannel(){
        return numOfChannel;
    }
}
