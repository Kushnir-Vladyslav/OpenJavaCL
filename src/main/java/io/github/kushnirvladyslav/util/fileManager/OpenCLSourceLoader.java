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

package io.github.kushnirvladyslav.util.fileManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for loading and caching OpenCL source code files.
 * This class provides static methods to load both kernel source code and OpenCL library files
 * from various locations and manage their cache. The loader supports both bundled resources
 * and filesystem locations.
 *
 * <p>OpenCL source files can be:
 * <ul>
 *   <li>Kernel files - containing main computational kernels</li>
 *   <li>Library files - containing reusable functions, constants, and data structures</li>
 *   <li>Header files - containing type definitions and function declarations</li>
 * </ul>
 *
 * <p>The loader supports several source locations:
 * <ul>
 *   <li>Java resources (bundled with the application)</li>
 *   <li>Specific filesystem paths</li>
 *   <li>Multiple search paths</li>
 * </ul>
 *
 * <p>Example usage:
 * <pre>{@code
 * // Load from resources
 * String kernelCode = OpenCLSourceLoader.getSource("matrix_ops.cl");
 *
 * // Load from specific path
 * String libCode = OpenCLSourceLoader.findSourceInSystem("/opencl/libs", "vector_ops.cl");
 *
 * // Load with search paths
 * List<String> paths = Arrays.asList("/libs", "/usr/local/opencl");
 * String code = OpenCLSourceLoader.findSourceInSystem(paths, "utilities.cl");
 * }</pre>
 *
 * <p>The loader implements caching to improve performance when the same file
 * is requested multiple times. Cache management methods are provided to control
 * memory usage.
 *
 * @author Vladyslav Kushnir
 * @version 1.0
 * @since 1.0
 */
public class OpenCLSourceLoader {
    private static final Logger logger = LoggerFactory.getLogger(OpenCLSourceLoader.class);
    private static final Map<String, String> cachedSources = new HashMap<>();

    /**
     * Retrieves OpenCL source code from resources with caching enabled.
     * The source OpenCL source should be available in the application's resources.
     *
     * @param fileName the name of the OpenCL source file to load
     * @return the source code as a string
     * @throws IOException if the OpenCL source cannot be found or read
     * @throws IllegalArgumentException if the OpenCL source name is invalid
     */
    public static String getSource(String fileName) throws IOException {
        return getSource(fileName, true);
    }

    /**
     * Retrieves OpenCL source code from resources with optional caching.
     * The source OpenCL source should be available in the application's resources.
     *
     * @param fileName the name of the OpenCL source file to load
     * @param cache whether to cache the source code
     * @return the source code as a string
     * @throws IOException if the OpenCL source cannot be found or read
     * @throws IllegalArgumentException if the OpenCL source name is invalid
     */
    public static String getSource(String fileName, boolean cache) throws IOException {
        validateFileName(fileName);
        logger.debug("Attempting to get OpenCL source for: {}", fileName);

        String source = cachedSources.getOrDefault(fileName, null);
        if (source != null) {
            logger.debug("Retrieved OpenCL source '{}' from cache", fileName);
            return source;
        }

        InputStream inputStream = FileLoader.fileResource(fileName);
        if (inputStream == null) {
            logger.error("OpenCL source '{}' not found in resources", fileName);
            throw new IOException("OpenCL source \"" + fileName + "\" not found.");
        }

        logger.debug("Loading OpenCL source '{}' from resources", fileName);
        return toString(inputStream, fileName, cache);
    }

    /**
     * Searches for and loads OpenCL source code from a specific path.
     * The method will search the specified path for the source file.
     *
     * @param path the directory path to search in
     * @param fileName the name of the OpenCL source file to find
     * @return the source code as a string
     * @throws IOException if the OpenCL source cannot be found or read
     * @throws IllegalArgumentException if path or OpenCL source name is invalid
     */
    public static String findSourceInSystem(String path, String fileName) throws IOException {
        return findSourceInSystem(path, fileName, true);
    }

    /**
     * Searches for and loads OpenCL source code from a specific path with optional caching.
     * The method will search the specified path for the source file.
     *
     * @param path the directory path to search in
     * @param fileName the name of the OpenCL source file to find
     * @param cache whether to cache the source code
     * @return the source code as a string
     * @throws IOException if the OpenCL source cannot be found or read
     * @throws IllegalArgumentException if path or OpenCL source name is invalid
     */
    public static String findSourceInSystem(String path, String fileName, boolean cache) throws IOException {
        validatePath(path);
        validateFileName(fileName);
        logger.debug("Searching for OpenCL source '{}' in path: {}", fileName, path);

        String source = cachedSources.getOrDefault(fileName, null);
        if (source != null) {
            logger.debug("Retrieved OpenCL source '{}' from cache", fileName);
            return source;
        }

        try {
            InputStream inputStream = FileLoader.fileInFileSystem(path, fileName);
            logger.debug("Found OpenCL source '{}' in path: {}", path, fileName);
            return toString(inputStream, fileName, cache);
        } catch (Exception e) {
            logger.error("Failed to find OpenCL source '{}' in path {}: {}", fileName, path, e.getMessage());
            throw new IOException("OpenCL source \"" + fileName + "\" not found.");
        }
    }

    /**
     * Searches for and loads OpenCL source code from multiple paths.
     * The method will search each path in order until the OpenCL source is found.
     *
     * @param paths list of directory paths to search in
     * @param fileName the name of the OpenCL source file to find
     * @return the source code as a string
     * @throws IOException if the OpenCL source cannot be found or read
     * @throws IllegalArgumentException if paths list or OpenCL source name is invalid
     */
    public static String findSourceInSystem(List<String> paths, String fileName) throws IOException {
        return findSourceInSystem(paths, fileName, true);
    }

    /**
     * Searches for and loads OpenCL source code from multiple paths with optional caching.
     * The method will search each path in order until the OpenCL source is found.
     *
     * @param paths list of directory paths to search in
     * @param fileName the name of the OpenCL source file to find
     * @param cache whether to cache the source code
     * @return the source code as a string
     * @throws IOException if the OpenCL source cannot be found or read
     * @throws IllegalArgumentException if paths list or OpenCL source name is invalid
     */
    public static String findSourceInSystem(List<String> paths, String fileName, boolean cache) throws IOException {
        validatePaths(paths);
        validateFileName(fileName);
        logger.debug("Searching for OpenCL source '{}' in multiple paths: {}", fileName, paths);

        String source = cachedSources.getOrDefault(fileName, null);
        if (source != null) {
            logger.debug("Retrieved OpenCL source '{}' from cache", fileName);
            return source;
        }

        try {
            InputStream inputStream = FileLoader.fileInFileSystem(paths, fileName);
            logger.debug("Found OpenCL source '{}' in one of the paths", fileName);
            return toString(inputStream, fileName, cache);
        } catch (Exception e) {
            logger.error("Failed to find OpenCL source '{}' in any of the paths: {}", fileName, e.getMessage());
            throw new IOException("OpenCL source \"" + fileName + "\" not found.");
        }
    }

    /**
     * Removes a specific source OpenCL source from the cache.
     * If the OpenCL source is not in cache, returns false.
     *
     * @param fileName the name of the source file to remove from cache
     * @return true if the OpenCL source was in cache and was removed, false otherwise
     * @throws IllegalArgumentException if the OpenCL source name is invalid
     */
    public static boolean removeFromCache(String fileName) {
        validateFileName(fileName);
        logger.debug("Removing OpenCL source '{}' from cache", fileName);
        return cachedSources.remove(fileName) != null;
    }

    /**
     * Clears all source files from the cache.
     * This can be useful to free memory or ensure fresh loading of files.
     */
    public static void clearCache() {
        logger.debug("Clearing entire OpenCL source cache");
        cachedSources.clear();
    }

    /**
     * Clears all source files from the cache except those specified in the keepList.
     * If keepList is null or empty, clears the entire cache.
     *
     * @param keepList list of OpenCL source names to keep in cache
     * @throws IllegalArgumentException if keepList contains invalid OpenCL source names
     */
    public static void clearCacheExcept(List<String> keepList) {
        if (keepList == null || keepList.isEmpty()) {
            clearCache();
            return;
        }

        logger.debug("Clearing OpenCL source cache except for files: {}", keepList);
        Set<String> keysToRemove = cachedSources.keySet().stream()
                .filter(key -> !keepList.contains(key))
                .collect(Collectors.toSet());

        keysToRemove.forEach(cachedSources::remove);
    }

    /**
     * Converts an InputStream to a String and optionally caches the result.
     *
     * @param inputStream the input stream to convert
     * @param fileName the name of the OpenCL source (used for caching)
     * @param cache whether to cache the converted source
     * @return the contents of the input stream as a string
     * @throws IOException if the input stream cannot be read
     */
    private static String toString(InputStream inputStream, String fileName, boolean cache) throws IOException {
        logger.debug("Converting input stream to string for OpenCL source: {}", fileName);

        String source;
        try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream))) {
            source = bufferedReader.lines().collect(Collectors.joining("\n"));
        }

        if (cache) {
            logger.debug("Caching OpenCL source for: {}", fileName);
            cachedSources.put(fileName, source);
        }

        return source;
    }

    /**
     * Validates OpenCL source name parameter.
     *
     * @param fileName the name of the OpenCL source to validate
     * @throws IllegalArgumentException if the OpenCL source name is invalid
     */
    private static void validateFileName(String fileName) {
        if (fileName == null) {
            logger.error("OpenCL source name cannot be null");
            throw new IllegalArgumentException("OpenCL source name cannot be null");
        }
        if (fileName.trim().isEmpty()) {
            logger.error("OpenCL source name cannot be empty");
            throw new IllegalArgumentException("OpenCL source name cannot be empty");
        }
        if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            logger.error("OpenCL source name contains invalid characters: {}", fileName);
            throw new IllegalArgumentException("OpenCL source name contains invalid characters: " + fileName);
        }
    }

    /**
     * Validates path parameter.
     *
     * @param path the path to validate
     * @throws IllegalArgumentException if the path is invalid
     */
    private static void validatePath(String path) {
        if (path == null) {
            logger.error("Path cannot be null");
            throw new IllegalArgumentException("Path cannot be null");
        }
        if (path.trim().isEmpty()) {
            logger.error("Path cannot be empty");
            throw new IllegalArgumentException("Path cannot be empty");
        }
    }

    /**
     * Validates list of paths.
     *
     * @param paths the list of paths to validate
     * @throws IllegalArgumentException if the paths list is invalid
     */
    private static void validatePaths(List<String> paths) {
        if (paths == null) {
            logger.error("Paths list cannot be null");
            throw new IllegalArgumentException("Paths list cannot be null");
        }
        if (paths.isEmpty()) {
            logger.error("Paths list cannot be empty");
            throw new IllegalArgumentException("Paths list cannot be empty");
        }
        for (String path : paths) {
            if (path == null) {
                logger.error("Path in list cannot be null");
                throw new IllegalArgumentException("Path in list cannot be null");
            }
            if (path.trim().isEmpty()) {
                logger.error("Path in list cannot be empty");
                throw new IllegalArgumentException("Path in list cannot be empty");
            }
        }
    }
}
