/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2011-2015 Broad Institute, Aiden Lab
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
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 */

package juicebox.tools.clt.juicer;

import com.google.common.primitives.Doubles;
import com.google.common.primitives.Floats;
import jargs.gnu.CmdLineParser;
import juicebox.HiCGlobals;
import juicebox.data.*;
import juicebox.tools.clt.CommandLineParserForJuicer;
import juicebox.tools.clt.JuicerCLT;
import juicebox.tools.utils.common.ArrayTools;
import juicebox.tools.utils.juicer.hiccups.GPUController;
import juicebox.tools.utils.juicer.hiccups.GPUOutputContainer;
import juicebox.tools.utils.juicer.hiccups.HiCCUPSConfiguration;
import juicebox.tools.utils.juicer.hiccups.HiCCUPSUtils;
import juicebox.track.feature.Feature2DList;
import juicebox.windowui.HiCZoom;
import juicebox.windowui.NormalizationType;
import org.broad.igv.Globals;
import org.broad.igv.feature.Chromosome;

import java.awt.*;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.List;

/**
 * HiC Computational Unbiased Peak Search
 *
 * @Created by muhammadsaadshamim on 1/20/15.
 *
 */
public class HiCCUPS extends JuicerCLT {

    public static final int regionMargin = 20;
    public static final int krNeighborhood = 5;
    public static final Color defaultPeakColor = Color.cyan;
    public static final boolean shouldColorBeScaledByFDR = false;
    private static final int totalMargin = 2 * regionMargin;
    private static final int w1 = 40;      // TODO dimension should be variably set
    private static final int w2 = 10000;   // TODO dimension should be variably set
    public static double fdrsum = 0.02;
    public static double oeThreshold1 = 1.5;
    public static double oeThreshold2 = 1.75;
    public static double oeThreshold3 = 2;
    private static boolean dataShouldBePostProcessed = true;
    private static int matrixSize = 512;// 540 original
    private static int regionWidth = matrixSize - totalMargin;
    private boolean chrSpecified = false;
    private Set<String> chromosomesSpecified = new HashSet<String>();
    private String inputHiCFileName;
    private String outputFDRFileName;
    private String outputEnrichedFileName;
    private String outputFinalLoopListFileName;
    private HiCCUPSConfiguration[] configurations;
    private NormalizationType preferredNormalization = NormalizationType.KR;

    //public static final int originalPixelClusterRadius = 20000; //TODO --> 10000? original 20000
    // w1 (40) corresponds to the number of expected bins (so the max allowed expected is 2^(40/3))
    // w2 (10000) corresponds to the number of reads (so it can't handle pixels with more than 10,000 reads)
    //private static final int fdr = 10;
    //private static final int peakWidth = 1;
    //private static final int window = 3;
    // defaults are set based on GM12878/IMR90

    /*
     * Reasonable Commands
     *
     * fdr = 10 for all resolutions
     * peak width = 1 for 25kb, 2 for 10kb, 4 for 5kb
     * window = 3 for 25kb, 5 for 10kb, 7 for 5kb
     *
     * cluster radius is 20kb for 5kb and 10kb res and 50kb for 25kb res
     * fdrsumthreshold is 0.02 for all resolutions
     * oeThreshold1 = 1.5 for all res
     * oeThreshold2 = 1.75 for all res
     * oeThreshold3 = 2 for all res
     *
     * published GM12878 looplist was only generated with 5kb and 10kb resolutions
     * same with published IMR90 looplist
     * published CH12 looplist only generated with 10kb
     */

    public HiCCUPS() {
        super("hiccups [-m matrixSize] [-c chromosome(s)] [-r resolution(s)] [-f fdr] [-p peak width] [-i window] " +
                "[-t thresholds] [-d centroid distances] <hicFile(s)> <finalLoopsList>\n" +
                "hiccups [-m matrixSize] [-c chromosome(s)] [-r resolution(s)] [-f fdr] [-p peak width] [-i window] \" +\n" +
                "                \"<hicFile(s)> <fdrThresholds> <enrichedPixelsList>");
        // also  hiccups [-r resolution] [-c chromosome] [-m matrixSize] <hicFile> <outputFDRThresholdsFileName>
    }

    @Override
    public void readArguments(String[] args, CmdLineParser parser) {

        CommandLineParserForJuicer juicerParser = (CommandLineParserForJuicer) parser;

        if (args.length == 4) {
            dataShouldBePostProcessed = false;
        } else if (!(args.length == 3)) {
            printUsage();
        }

        inputHiCFileName = args[1];
        if (dataShouldBePostProcessed) {
            outputFinalLoopListFileName = args[2];
        } else {
            outputFDRFileName = args[2];
            outputEnrichedFileName = args[3];
        }

        determineValidMatrixSize(juicerParser);
        determineValidChromosomes(juicerParser);
        determineValidConfigurations(juicerParser);
    }



    @Override
    public void run() {

        Dataset ds = HiCFileTools.extractDatasetForCLT(Arrays.asList(inputHiCFileName.split("\\+")), true);

        List<Chromosome> commonChromosomes = ds.getChromosomes();
        if (chrSpecified)
            commonChromosomes = new ArrayList<Chromosome>(HiCFileTools.stringToChromosomes(chromosomesSpecified,
                    commonChromosomes));

        Map<Integer, Feature2DList> loopLists = new HashMap<Integer, Feature2DList>();

        List<HiCCUPSConfiguration> filteredConfigurations = HiCCUPSConfiguration.filterConfigurations(configurations, ds);
        for (HiCCUPSConfiguration conf : filteredConfigurations) {
            Feature2DList enrichedPixels = runHiccupsProcessing(ds, conf, commonChromosomes);
            if (enrichedPixels != null) {
                loopLists.put(conf.getResolution(), enrichedPixels);
            }
        }

        if (dataShouldBePostProcessed) {
            HiCCUPSUtils.postProcess(loopLists, ds, commonChromosomes, outputFinalLoopListFileName,
                    filteredConfigurations, preferredNormalization);
        }
        // else the thresholds and raw pixels were already saved when hiccups was run
    }

    /**
     * Actual run of the HiCCUPS algorithm
     *
     * @param ds                dataset from hic file
     * @param conf              configuration of hiccups inputs
     * @param commonChromosomes list of chromosomes to run hiccups on
     * @return list of enriched pixels
     */
    private Feature2DList runHiccupsProcessing(Dataset ds, HiCCUPSConfiguration conf, List<Chromosome> commonChromosomes) {

        long begin_time = System.currentTimeMillis();

        HiCZoom zoom = ds.getZoomForBPResolution(conf.getResolution());
        if (zoom == null) {
            System.err.println("Data not available at " + conf.getResolution() + " resolution");
            return null;
        }

        PrintWriter outputFDR = null;
        if (outputFDRFileName != null)
            outputFDR = HiCFileTools.openWriter(outputFDRFileName + "_" + conf.getResolution());

        int[][] histBL = new int[w1][w2];
        int[][] histDonut = new int[w1][w2];
        int[][] histH = new int[w1][w2];
        int[][] histV = new int[w1][w2];
        float[][] fdrLogBL = new float[w1][w2];
        float[][] fdrLogDonut = new float[w1][w2];
        float[][] fdrLogH = new float[w1][w2];
        float[][] fdrLogV = new float[w1][w2];
        float[] thresholdBL = ArrayTools.newValueInitializedFloatArray(w1, (float) w2);
        float[] thresholdDonut = ArrayTools.newValueInitializedFloatArray(w1, (float) w2);
        float[] thresholdH = ArrayTools.newValueInitializedFloatArray(w1, (float) w2);
        float[] thresholdV = ArrayTools.newValueInitializedFloatArray(w1, (float) w2);
        float[] boundRowIndex = new float[1];
        float[] boundColumnIndex = new float[1];

        GPUController gpuController = new GPUController(conf.getWindowWidth(), matrixSize,
                conf.getPeakWidth(), conf.divisor());

        // to hold all enriched pixels found in second run
        Feature2DList globalList = new Feature2DList();

        // two runs, 1st to build histograms, 2nd to identify loops
        for (int runNum : new int[]{0, 1}) {
            for (Chromosome chromosome : commonChromosomes) {

                // skip these matrices
                if (chromosome.getName().equals(Globals.CHR_ALL)) continue;
                Matrix matrix = ds.getMatrix(chromosome, chromosome);
                if (matrix == null) continue;

                // get matrix data access
                long start_time = System.currentTimeMillis();
                MatrixZoomData zd = matrix.getZoomData(zoom);

                //NormalizationType preferredNormalization = HiCFileTools.determinePreferredNormalization(ds);
                NormalizationVector norm = ds.getNormalizationVector(chromosome.getIndex(), zoom, preferredNormalization);
                if (norm != null) {
                    double[] normalizationVector = norm.getData();
                    double[] expectedVector = HiCFileTools.extractChromosomeExpectedVector(ds, chromosome.getIndex(),
                            zoom, preferredNormalization);

                    // need overall bounds for the chromosome
                    int chrLength = chromosome.getLength();
                    int chrMatrixWdith = (int) Math.ceil((double) chrLength / conf.getResolution());
                    long load_time = System.currentTimeMillis();
                    if (HiCGlobals.printVerboseComments)
                        System.out.println("Time to load chr " + chromosome.getName() + " matrix: " + (load_time - start_time) + "ms");

                    for (int i = 0; i < Math.ceil(chrMatrixWdith * 1.0 / regionWidth) + 1; i++) {
                        int[] rowBounds = calculateRegionBounds(i, regionWidth, chrMatrixWdith);

                        if (rowBounds[4] < chrMatrixWdith - regionMargin) {
                            for (int j = i; j < Math.ceil(chrMatrixWdith * 1.0 / regionWidth) + 1; j++) {
                                int[] columnBounds = calculateRegionBounds(j, regionWidth, chrMatrixWdith);

                                if (columnBounds[4] < chrMatrixWdith - regionMargin) {
                                    try {
                                        GPUOutputContainer gpuOutputs = gpuController.process(zd, normalizationVector, expectedVector,
                                                rowBounds, columnBounds, matrixSize,
                                                thresholdBL, thresholdDonut, thresholdH, thresholdV,
                                                boundRowIndex, boundColumnIndex, preferredNormalization);

                                        int diagonalCorrection = (rowBounds[4] - columnBounds[4]) + conf.getPeakWidth() + 2;

                                        if (runNum == 0) {
                                            gpuOutputs.cleanUpBinNans();
                                            gpuOutputs.cleanUpBinDiagonal(diagonalCorrection);
                                            gpuOutputs.updateHistograms(histBL, histDonut, histH, histV, w1, w2);

                                        } else if (runNum == 1) {
                                            gpuOutputs.cleanUpPeakNaNs();
                                            gpuOutputs.cleanUpPeakDiagonal(diagonalCorrection);

                                            Feature2DList peaksList = gpuOutputs.extractPeaks(chromosome.getIndex(), chromosome.getName(),
                                                    w1, w2, rowBounds[4], columnBounds[4], conf.getResolution());
                                            peaksList.calculateFDR(fdrLogBL, fdrLogDonut, fdrLogH, fdrLogV);
                                            globalList.add(peaksList);
                                        }
                                    } catch (IOException e) {
                                        System.err.println("No data in map region");
                                    }
                                }
                            }
                        }
                    }

                    if (HiCGlobals.printVerboseComments) {
                        long segmentTime = System.currentTimeMillis();

                        if (runNum == 0) {
                            System.out.println("Time to calculate chr " + chromosome.getName() + " expecteds and add to hist: " + (segmentTime - load_time) + "ms");
                        } else { // runNum = 1
                            System.out.println("Time to print chr" + chromosome.getName() + " peaks: " + (segmentTime - load_time) + "ms");
                        }
                    }
                } else {
                    System.err.println("Data not available for " + chromosome + " at " + conf.getResolution() + " resolution");
                }

            }
            if (runNum == 0) {

                long thresh_time0 = System.currentTimeMillis();

                int[][] rcsHistBL = ArrayTools.makeReverse2DCumulativeArray(histBL);
                int[][] rcsHistDonut = ArrayTools.makeReverse2DCumulativeArray(histDonut);
                int[][] rcsHistH = ArrayTools.makeReverse2DCumulativeArray(histH);
                int[][] rcsHistV = ArrayTools.makeReverse2DCumulativeArray(histV);

                for (int i = 0; i < w1; i++) {
                    float[] unitPoissonPMF = Floats.toArray(Doubles.asList(ArrayTools.generatePoissonPMF(i, w2)));
                    HiCCUPSUtils.calculateThresholdAndFDR(i, w2, conf.getFDRThreshold(), unitPoissonPMF, rcsHistBL, thresholdBL, fdrLogBL);
                    HiCCUPSUtils.calculateThresholdAndFDR(i, w2, conf.getFDRThreshold(), unitPoissonPMF, rcsHistDonut, thresholdDonut, fdrLogDonut);
                    HiCCUPSUtils.calculateThresholdAndFDR(i, w2, conf.getFDRThreshold(), unitPoissonPMF, rcsHistH, thresholdH, fdrLogH);
                    HiCCUPSUtils.calculateThresholdAndFDR(i, w2, conf.getFDRThreshold(), unitPoissonPMF, rcsHistV, thresholdV, fdrLogV);
                }

                long thresh_time1 = System.currentTimeMillis();
                System.out.println("Time to calculate thresholds: " + (thresh_time1 - thresh_time0) + "ms");
            }

        }

        if (!dataShouldBePostProcessed) {
            globalList.exportFeatureList(outputEnrichedFileName + "_" + conf.getResolution(), true);
            if (outputFDR != null) {
                for (int i = 0; i < w1; i++) {
                    outputFDR.println(i + "\t" + thresholdBL[i] + "\t" + thresholdDonut[i] + "\t" + thresholdH[i] + "\t" + thresholdV[i]);
                }
            }
        }

        if (outputFDR != null) {
            outputFDR.close();
        }

        if (HiCGlobals.printVerboseComments) {
            long final_time = System.currentTimeMillis();
            System.out.println("Total time: " + (final_time - begin_time));
        }

        return globalList;
    }

    private int[] calculateRegionBounds(int index, int regionWidth, int chrMatrixWidth) {

        int bound1R = Math.min(regionMargin + (index * regionWidth), chrMatrixWidth - regionMargin);
        int bound1 = bound1R - regionMargin;
        int bound2R = Math.min(bound1R + regionWidth, chrMatrixWidth - regionMargin);
        int bound2 = bound2R + regionMargin;

        int diff1 = bound1R - bound1;
        int diff2 = bound2 - bound2R;

        return new int[]{bound1, bound2, diff1, diff2, bound1R, bound2R};
    }

    /**
     * @param juicerParser
     */
    private void determineValidConfigurations(CommandLineParserForJuicer juicerParser) {

        try {
            configurations = HiCCUPSConfiguration.extractConfigurationsFromCommandLine(juicerParser);

        } catch (Exception e) {
            System.out.println("Either no resolution specified or other error. Defaults being used.");
            configurations = new HiCCUPSConfiguration[]{new HiCCUPSConfiguration(10000, 10, 2, 5, 20000),
                    new HiCCUPSConfiguration(5000, 10, 4, 7, 20000)};
        }

        try {
            List<String> t = juicerParser.getThresholdOptions();
            if (t.size() > 1) {
                double[] thresholds = HiCCUPSUtils.extractDoubleValues(t, 4, -1f);
                fdrsum = thresholds[0];
                oeThreshold1 = thresholds[1];
                oeThreshold2 = thresholds[2];
                oeThreshold3 = thresholds[3];

            }
        } catch (Exception e) {
            // do nothing - use default postprocessing thresholds
        }
    }

    private void determineValidChromosomes(CommandLineParserForJuicer juicerParser) {
        List<String> specifiedChromosomes = juicerParser.getChromosomeOption();
        if (specifiedChromosomes != null) {
            chromosomesSpecified = new HashSet<String>(specifiedChromosomes);
            chrSpecified = true;
        }
    }

    private void determineValidMatrixSize(CommandLineParserForJuicer juicerParser) {
        int specifiedMatrixSize = juicerParser.getMatrixSizeOption();
        if (specifiedMatrixSize > 2 * regionMargin) {
            matrixSize = specifiedMatrixSize;
            regionWidth = specifiedMatrixSize - totalMargin;
        }
        System.out.println("Using Matrix Size " + matrixSize);
    }
}