package edu.unh.artt.core.outlier;

import edu.unh.artt.core.error_sample.representation.OffsetGmSample;
import edu.unh.artt.core.models.WeightedKernelDensityEstimator;
import edu.unh.artt.core.models.WeightedKernelDensityEstimatorTest;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DistanceOutlierDetectorTest {

    @Test
    public void isOutlier() {
        int [] sampleSizes = new int[]{100, 20, 1000, 8193};
        int [] variances = new int[]{2, 100, 500, 20};
        long [] means = new long[]{10, 2, 10000, -500, Integer.MIN_VALUE, Long.MAX_VALUE>>16};
        double likelihoodThresh = 0.1;

        var testimator = new WeightedKernelDensityEstimator<OffsetGmSample>(1, 1);
        for(int size : sampleSizes) {
            testimator.modifyWindowSize(size);
            for(int var : variances) {
                for(long mean : means) {
                    for(int modes = 1; modes < 4; modes++) {
                        WeightedKernelDensityEstimatorTest.fillEstimator(testimator, mean, var, size, modes, 2 * var);
                        DistanceOutlierDetector<OffsetGmSample> outlierDetector = new DistanceOutlierDetector<>(testimator, new double[]{1.0}, likelihoodThresh);

                        assertTrue(outlierDetector.isOutlier(new OffsetGmSample(0, 1000 * var + mean, new byte[8])));
                        assertTrue(outlierDetector.isOutlier(new OffsetGmSample(0, Integer.MAX_VALUE, new byte[8])));
                        assertTrue(outlierDetector.isOutlier(new OffsetGmSample(0, -3000 * var + mean, new byte[8])));
                        assertTrue(outlierDetector.isOutlier(new OffsetGmSample(0, Long.MIN_VALUE >> 16, new byte[8])));

                        assertFalse(outlierDetector.isOutlier(new OffsetGmSample(0, mean, new byte[8])));
                        assertFalse(outlierDetector.isOutlier(new OffsetGmSample(0, mean + var, new byte[8])));
                        assertFalse(outlierDetector.isOutlier(new OffsetGmSample(0, mean - var, new byte[8])));
                    }
                }
            }
        }
        testimator.shutdown();
    }
}