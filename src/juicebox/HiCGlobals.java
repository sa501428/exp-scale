/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2011-2021 Broad Institute, Aiden Lab, Rice University, Baylor College of Medicine
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 */

package juicebox;

import javastraw.reader.mzd.MatrixZoomData;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author Muhammad Shamim
 * @since 11/25/14
 */
public class HiCGlobals {

    public static final String versionNum = "2.13.07";
    public static final int minVersion = 6;
    public static final int writingVersion = 9;
    public static final int bufferSize = 2097152;
    public static int MAX_PEARSON_ZOOM = 50000;
    public static int MAX_EIGENVECTOR_ZOOM = 250000;
    public static boolean useCache = true;
    public static boolean allowDynamicBlockIndex = true;
    public static boolean printVerboseComments = false;
    public static boolean USE_ITERATOR_NOT_ALL_IN_RAM = false;
    public static boolean CHECK_RAM_USAGE = false;

    public static void verifySupportedHiCFileVersion(int version) throws RuntimeException {
        if (version < minVersion) {
            throw new RuntimeException("This file is version " + version +
                    ". Only versions " + minVersion + " and greater are supported at this time.");
        }
    }

    public static void verifySupportedHiCFileWritingVersion(int version) throws RuntimeException {
        if (version < writingVersion) {
            throw new RuntimeException("This file is version " + version +
                    ". Only versions " + writingVersion + " and greater can be edited using this jar.");
        }
    }

    public static int getIdealThreadCount() {
        return Math.max(1, Runtime.getRuntime().availableProcessors());
    }

    public static ExecutorService newFixedThreadPool() {
        return Executors.newFixedThreadPool(getIdealThreadCount());
    }

    public static void setMatrixZoomDataRAMUsage() {
        MatrixZoomData.useIteratorDontPutAllInRAM = USE_ITERATOR_NOT_ALL_IN_RAM;
        MatrixZoomData.shouldCheckRAMUsage = CHECK_RAM_USAGE;
    }
}
