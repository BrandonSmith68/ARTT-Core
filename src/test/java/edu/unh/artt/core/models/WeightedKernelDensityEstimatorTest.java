package edu.unh.artt.core.models;

import edu.unh.artt.core.error_sample.representation.OffsetGmSample;
import org.junit.Test;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.stream.LongStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class WeightedKernelDensityEstimatorTest {

    @Test
    public void computeMetrics() {
    }

    @Test
    public void resampleImpl() {
        long mean = 10;
        WeightedKernelDensityEstimator<OffsetGmSample> estimator = new WeightedKernelDensityEstimator<>(1000, 1);
        fillEstimator(estimator, mean, 1, estimator.getLocalWindowSize(), 1, 1);

        double[][] samples = estimator.resample(200);
        assertEquals(200, samples.length);
        estimator.shutdown();
    }

    @Test
    public void estimate() {
        long mean = 10;
        WeightedKernelDensityEstimator<OffsetGmSample> estimator = new WeightedKernelDensityEstimator<>(1000, 1);
        fillEstimator(estimator, mean, 1, estimator.getLocalWindowSize(), 1, 1);

        OffsetGmSample [] testSamples = LongStream.range(0, 2*mean).mapToObj(i -> new OffsetGmSample(0,1, i, new byte[8])).toArray(OffsetGmSample[]::new);
        double [] probs = estimator.estimate(testSamples);
        assertTrue(Arrays.stream(probs).sum() > 0.9);
        estimator.shutdown();
    }

    private static final Random r = new Random();

    public static void fillEstimator(WeightedKernelDensityEstimator<OffsetGmSample> estimator, double mean, double variance, int sampleCount, int modes, int modeDist) {
        List<OffsetGmSample> samples = new LinkedList<>();

        for(int i = 0; i < sampleCount*modes; i+=modes) {
            for(int m = 0; m < modes; m++) {
                long sample = Math.round(mean + (modeDist*m) + r.nextGaussian() * variance);
                samples.add(new OffsetGmSample(0, 1, sample, new byte[8]));
            }
        }
        estimator.addSamples(samples);
    }

    @Test
    public void compare() {
        long mean = 10;
        WeightedKernelDensityEstimator<OffsetGmSample> estimator1 = new WeightedKernelDensityEstimator<>(1000, 1);
        WeightedKernelDensityEstimator<OffsetGmSample> estimator2 = new WeightedKernelDensityEstimator<>(1000, 1);

        fillEstimator(estimator1, mean, 1, estimator1.getLocalWindowSize(), 1, 1);
        fillEstimator(estimator2, mean, 1, estimator2.getLocalWindowSize(), 1, 1);

        var res = WeightedKernelDensityEstimator.compare(estimator1, estimator2, new double[]{1.});
        assertTrue(res.js_divergence < 0.05);

        fillEstimator(estimator2, mean * 10000, 1, estimator2.getLocalWindowSize(), 1, 1);

        res = WeightedKernelDensityEstimator.compare(estimator1, estimator2, new double[]{1.});
        assertTrue(res.js_divergence > 1);

        fillEstimator(estimator2, mean + 1, 1, estimator2.getLocalWindowSize(), 1, 1);

        res = WeightedKernelDensityEstimator.compare(estimator1, estimator2, new double[]{1.});
        assertTrue(res.js_divergence < 0.5);

        fillEstimator(estimator2, mean + 1, 6, estimator2.getLocalWindowSize(), 1, 1);

        res = WeightedKernelDensityEstimator.compare(estimator1, estimator2, new double[]{1.});
        assertTrue(res.js_divergence < 0.5);
    }
}