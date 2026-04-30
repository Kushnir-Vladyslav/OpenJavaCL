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

public enum ImageChannelDataType {
    UNORM_INT8(CL12.CL_UNORM_INT8, 1),
    UNORM_INT16(CL12.CL_UNORM_INT16, 2),
    SNORM_INT8(CL12.CL_SNORM_INT8, 1),
    SNORM_INT16(CL12.CL_SNORM_INT16, 2),
    UNSIGNED_INT8(CL12.CL_UNSIGNED_INT8, 1),
    UNSIGNED_INT16(CL12.CL_UNSIGNED_INT16, 2),
    UNSIGNED_INT32(CL12.CL_UNSIGNED_INT32, 4),
    SIGNED_INT8(CL12.CL_SIGNED_INT8, 1),
    SIGNED_INT16(CL12.CL_SIGNED_INT16, 2),
    SIGNED_INT32(CL12.CL_SIGNED_INT32, 4),
    HALF_FLOAT(CL12.CL_HALF_FLOAT, 2),
    FLOAT(CL12.CL_FLOAT, 4);

    ImageChannelDataType(int flag, int byteSize) {
        this.flag = flag;
        this.byteSize = byteSize;
    }

    private final int flag;
    private final int byteSize;

    public int getFlag() {
        return flag;
    }

    public int getByteSize(){
        return byteSize;
    }
}

