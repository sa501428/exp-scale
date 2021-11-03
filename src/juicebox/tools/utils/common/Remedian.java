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

package juicebox.tools.utils.common;


import java.util.ArrayList;
import java.util.List;

public class Remedian {
    private final int numValsPerSet;
    private final List<List<Number>> medianPyramid;
    private Float theMainMedian = null;

    public Remedian(int numValsPerSet) { // try to use odd number of entries
        this.numValsPerSet = numValsPerSet;
        medianPyramid = new ArrayList<>(3);
        medianPyramid.add(new ArrayList<>(numValsPerSet));
    }

    public void addVal(double value) {
        addVal(value, 0);
    }

    public void addVal(Number value, int layer) {
        while (medianPyramid.size() < layer + 1) {
            medianPyramid.add(new ArrayList<>(numValsPerSet));
        }
        medianPyramid.get(layer).add(value);
        checkLayer(layer);
        theMainMedian = null;
    }

    private void checkLayer(int k) {
        if (medianPyramid.get(k).size() >= numValsPerSet) {
            float median = QuickMedian.fastMedian(medianPyramid.get(k));
            if (medianPyramid.size() < k + 2) {
                medianPyramid.add(new ArrayList<>(numValsPerSet));
            }
            medianPyramid.get(k + 1).add(median);
            medianPyramid.get(k).clear();
            checkLayer(k + 1);
        }
    }

    public float getMedian() {
        if (theMainMedian == null) {
            theMainMedian = QuickMedian.fastMedian(medianPyramid.get(medianPyramid.size() - 1));
        }
        return theMainMedian;
    }

    public Remedian deepClone() {
        Remedian clone = new Remedian(numValsPerSet);
        for (int l = 0; l < medianPyramid.size(); l++) {
            for (Number value : medianPyramid.get(l)) {
                clone.addVal(value, l);
            }
        }
        return clone;
    }

    public void merge(Remedian other) {
        for (int l = 0; l < other.medianPyramid.size(); l++) {
            for (Number value : other.medianPyramid.get(l)) {
                addVal(value, l);
            }
        }
    }

    public int topTierSize() {
        int s = medianPyramid.size();
        return (int) (medianPyramid.get(s - 1).size() * Math.pow(numValsPerSet, s - 1));
    }
}

