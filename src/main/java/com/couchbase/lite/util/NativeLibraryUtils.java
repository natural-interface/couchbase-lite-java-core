/**
 * Created by Pasin Suriyentrakorn on 9/26/15
 *
 * Copyright (c) 2015 Couchbase, Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */
package com.couchbase.lite.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class NativeLibraryUtils {
    private static final Map<String, Boolean> LOADED_LIBRARIES = new HashMap<String, Boolean>();

    public static boolean loadLibrary(String libraryName) {
        if (LOADED_LIBRARIES.containsKey(libraryName))
            return true;

        try {
            File libraryFile;
            String libraryPath = getConfiguredLibraryPath(libraryName);
            System.out.println("libraryPath : "+libraryPath);

            if (libraryPath != null)
                libraryFile = new File(libraryPath);
            else
                libraryFile = extractLibrary(libraryName);

            System.out.println("libraryFile : "+libraryFile);

            if (libraryFile != null) {
                System.load(libraryFile.getAbsolutePath());
                LOADED_LIBRARIES.put(libraryName, true);
            } else {
                Log.e(Log.TAG, "Cannot find library: " + libraryName);
                return false;
            }
        } catch (Exception e) {
            Log.e(Log.TAG, "Error loading library: " + libraryName, e);
            return false;
        }

        return true;
    }

    private static String getConfiguredLibraryPath(String libraryName) {
        String key = String.format(Locale.ENGLISH, "com.couchbase.lite.lib.%s.path", libraryName);
        System.out.println("key : "+key);

        return System.getProperty(key);
    }

    private static String getLibraryFullName(String libraryName) {
        System.out.println("libraryName : "+libraryName);

        String name = System.mapLibraryName(libraryName);
        System.out.println("name : "+name);

        // Workaround discrepancy issue between OSX Java6 (.jnilib)
        // and Java7 (.dylib) native library file extension.
        if (name.endsWith(".jnilib")) {
            name = name.replace(".jnilib", ".dylib");
        }
        String osName = System.getProperty("os.name");
        if (osName.contains("Linux")) {
            name = name.replace(".so", ".so.0");
        }
        System.out.println("name after renaming : "+name);

        return name;
    }

    private static File extractLibrary(String libraryName) throws IOException {
        String libraryResourcePath = getLibraryResourcePath(libraryName);
        System.out.println("libraryResourcePath xx: "+libraryResourcePath);

        String targetFolder = new File(System.getProperty("java.io.tmpdir")).getAbsolutePath();
        System.out.println("targetFolder : "+targetFolder);

        File targetFile = new File(targetFolder, getLibraryFullName(libraryName)).getCanonicalFile();
        System.out.println("targetFile : "+targetFile.getCanonicalPath().toString());

        // If the target already exists, and it's unchanged, then use it, otherwise delete it and
        // it will be replaced.
        if (targetFile.exists()) {
            // Remove old native library file:
            if (!targetFile.delete()) {
                // If we can't remove the old library file then log a warning and try to use it:
                Log.w(Log.TAG, "Failed to delete existing library file: " + targetFile.getAbsolutePath());
                return targetFile;
            }
        }

        // Extract the library to the target directory:
        Path file = Paths.get(libraryResourcePath);
        System.out.print("file : "+file.toString());
        InputStream libraryReader = new FileInputStream(file.toString()); //Files.newInputStream(file);//NativeLibraryUtils.class.getResourceAsStream(libraryResourcePath);
        if (libraryReader == null) {
            System.err.println("Library not found: " + libraryResourcePath);
            return null;
        }

        FileOutputStream libraryWriter = new FileOutputStream(targetFile);
        try {
            int i;

            byte[] buffer = new byte[1024];
            int bytesRead = 0;
            while ((bytesRead = libraryReader.read(buffer)) != -1) {
                libraryWriter.write(buffer, 0, bytesRead);
            }

        } finally {
            libraryWriter.close();
            libraryReader.close();
        }

        // On non-windows systems set up permissions for the extracted native library.
        if (!System.getProperty("os.name").toLowerCase().contains("windows")) {
            try {
                Runtime.getRuntime().exec(
                        new String[]{"sudo chmod", "755", targetFile.getAbsolutePath()}).waitFor();
            } catch (Throwable e) {
                Log.e(Log.TAG, "Error executing 'chmod 755' on extracted native library", e);
            }
        }
        return targetFile;
    }

    private static String getLibraryResourcePath(String libraryName) {
        // Root native folder.
        String path = "/native";

        // OS part of path.
        String osName = System.getProperty("os.name");
        if (osName.contains("Linux")) {
            path += "/linux";
        } else if (osName.contains("Mac")) {
            path += "/osx";
        } else if (osName.contains("Windows")) {
            path += "/windows";
        } else {
            path += '/' + osName.replaceAll("\\W", "").toLowerCase();
        }

        // Architecture part of path.
        String archName = System.getProperty("os.arch");
        archName = archName.replaceAll("\\W", "");
        archName = archName.replace("-", "_");
        path += '/' + archName;

        // Platform specific name part of path.
        if ((osName.contains("Linux")) && (archName.contains("arm"))) {
            path = "/usr/lib/arm-linux-gnueabihf";
        }
        path += '/' + getLibraryFullName(libraryName);
        System.out.println("path xx: "+path);

        return path;
    }
}
