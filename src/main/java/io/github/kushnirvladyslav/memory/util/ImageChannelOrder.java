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
import org.lwjgl.opencl.CL12;

public enum ImageChannelOrder {
    RED_ONLY(CL12.CL_R),
    ALPHA_ONLY(CL12.CL_A),
    RED_GREEN(CL12.CL_RG),
    RED_ALPHA(CL12.CL_RA),
    RGBA(CL12.CL_RGBA),
    BGRA(CL12.CL_BGRA),
    ARGB(CL12.CL_ARGB),
    INTENSITY(CL12.CL_INTENSITY),
    LUMINANCE(CL12.CL_LUMINANCE),
    DEPTH(CL12.CL_IMAGE_DEPTH);

    ImageChannelOrder(int flag) {


        this.flag = flag;
    }

    private final int flag;

    public int getFlag() {
        return flag;
    }
}
