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

package io.github.kushnirvladyslav.kernel;

import io.github.kushnirvladyslav.OpenClContext;
//import org.example.Library.LibraryManager;
//import org.example.OldBuffers.BufferManager;
import org.lwjgl.PointerBuffer;
import org.lwjgl.opencl.CL10;
import org.lwjgl.system.MemoryUtil;

/**
 * Абстрактний клас, який представляє OpenCL ядро.
 * Відповідає за створення, запуск та звільнення ресурсів ядра.
 */
public abstract class Kernel {
    protected long kernel;     // Ідентифікатор OpenCL ядра
    private long program;   // Ідентифікатор OpenCL програми

//    protected static LibraryManager libraryManager = LibraryManager.getInstance();
//
//    // Менеджер що відповідає за створення, зміну та видалення io.github.kushnirvladyslav.OpenCl буферів
//    protected BufferManager bufferManager;

    // Змінні що потрібні для роботи з буферами OpenCL та ядрами
    protected OpenClContext openClContext;

    protected PointerBuffer global;   // Буфер для передачі загальної кількості задач
    protected PointerBuffer local;    // Буфер для передачі кількості задач в одній робочій групі

    protected int err;      // Код помилки, що повертається після виконання ядра

    /**
     * Створює OpenCL ядро з вихідного коду.
     *
     * @param kernelName                Назва ядра, та файлу з ядром
     * @param libraries                 Бібліотеки, які потрібно підключити до ядра.
     * @throws IllegalStateException    Якщо файл з кодом ядра не знайдено.
     * @throws RuntimeException         Якщо не вдається створити або скомпілювати ядро.
     */
    protected Kernel (String kernelName, String kernelFile, String... libraries) {
//        bufferManager = BufferManager.getInstance();
//
//        openClContext = OpenClContext.getInstance();
//
//        String kernelSource = "";
//
//        for (String library : libraries) {
//            kernelSource += libraryManager.getLibrary(library);
//        }
//
//        URL URLKernelSource = getClass().getResource(kernelFile);
//
//        if(URLKernelSource == null) {
//            throw new IllegalStateException("The kernel code file was not found.");
//        }
//
//        try {
//            kernelSource += Files.readString(Paths.get(URLKernelSource.toURI()));
//        } catch (IOException | URISyntaxException e) {
//            throw new RuntimeException(e);
//        }
//
//        modifyKernelSours(kernelSource);
//
//        // Компіляція та створення kernel
//        program = CL10.clCreateProgramWithSource(openClContext.context, kernelSource, null);
//        if (program == 0) {
//            throw new RuntimeException("Failed to create OpenCL program");
//        }
//
//        int buildStatus = CL10.clBuildProgram(program, openClContext.device, "", null, 0);
//        if (buildStatus != CL10.CL_SUCCESS) {
//            // Отримання журналу компіляції
//            PointerBuffer sizeBuffer = MemoryStack.stackMallocPointer(1);
//            CL10.clGetProgramBuildInfo(program, openClContext.device, CL10.CL_PROGRAM_BUILD_LOG, (ByteBuffer) null, sizeBuffer);
//
//            ByteBuffer buildLogBuffer = MemoryStack.stackMalloc((int) sizeBuffer.get(0));
//            CL10.clGetProgramBuildInfo(program, openClContext.device, CL10.CL_PROGRAM_BUILD_LOG, buildLogBuffer, null);
//
//            String buildLog = MemoryUtil.memUTF8(buildLogBuffer);
//            System.err.println("Build log:\n" + buildLog);
//            throw new RuntimeException("Failed to build OpenCL program.");
//        }
//
//        IntBuffer errorBuffer = MemoryUtil.memAllocInt(1);
//
//        kernel = CL10.clCreateKernel(program, kernelName, errorBuffer);
//
//        int error = errorBuffer.get(0);
//        MemoryUtil.memFree(errorBuffer);
//
//        // Перевірка чи правельно пройшла уомпіляція
//        if (kernel == 0) {
//            throw new RuntimeException("Failed to create kernel: " + kernelName + ".\n Error code: " + error);
//        }
    }

    /**
     * Повертає ідентифікатор OpenCL ядра.
     *
     * @return Ідентифікатор ядра.
     */
    public long getClKernel() {
        return kernel;
    }


    /**
     * Метод для внесення правок до вихідного коду ядра перед компіляцією.
     *
     * @param kernelSours Вихідний код ядра.
     */
    protected void modifyKernelSours (String kernelSours) {}

    /**
     * Абстрактний метод для запуску ядра.
     */
    public abstract void run ();

    /**
     * Перевіряє, чи виникла помилка під час виконання ядра.
     */
    protected void checkError() {
        if (err != CL10.CL_SUCCESS) {
            System.err.println(
                    "Method \"run()\" of class \"" +
                            this.getClass().getSimpleName() +
                            "\" finished with error:" +
                            err
            );
        }
    }

    /**
     * Звільняє ресурси ядра.
     */
    public void destroy (){
        if (kernel != 0) {
            CL10.clReleaseKernel(kernel);
            kernel = 0;
        }
        if (program != 0) {
            CL10.clReleaseProgram(program);
            program = 0;
        }
        if (local != null){
            MemoryUtil.memFree(local);
            local = null;
        }
        if (global != null) {
            MemoryUtil.memFree(global);
            local = null;
        }
    }
}
