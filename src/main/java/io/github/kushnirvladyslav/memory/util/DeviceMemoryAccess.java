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

import org.lwjgl.opencl.CL10;

public enum DeviceMemoryAccess {
    READ_WRITE(CL10.CL_MEM_READ_WRITE ),
    READ_ONLY (CL10.CL_MEM_READ_ONLY ),
    WRITE_ONLY(CL10.CL_MEM_WRITE_ONLY );

    DeviceMemoryAccess(int flag) {
        this.flag = flag;
    }

    private final int flag;

    public int getFlag() {
        return flag;
    }
}
