package edu.unh.artt.core.models.kernel_estimator;

import edu.unh.artt.core.error_sample.representation.OffsetGmSample;
import edu.unh.artt.core.models.KernelDensityEstimator;
import org.junit.Assert;
import org.junit.Test;

import java.util.Random;

public class TestKernelDensityEstimator {
    private class DistTriple {
        final KernelDensityEstimator<OffsetGmSample> estimator;
        final double [] samples;
        final double [][] probability_dist;

        DistTriple(KernelDensityEstimator<OffsetGmSample> est, double [] samps, double [][] pdist) {
            estimator = est;
            samples = samps;
            probability_dist = pdist;
        }
    }

    private static final Random r = new Random();

    private DistTriple createEstimator(int windowSize, double mean, double variance, int sampleCount, int modes, int modeDist) {

        KernelDensityEstimator<OffsetGmSample> estimator = new KernelDensityEstimator<>(windowSize);
        double [] samples = new double[modes*sampleCount];

        for(int i = 0; i < sampleCount*modes; i+=modes) {
            for(int m = 0; m < modes; m++) {
                long sample = Math.round(mean + (modeDist*m) + r.nextGaussian() * variance);
                samples[i+m] = sample;
                estimator.addSample(new OffsetGmSample(sample, new byte[8]));
            }
        }

        int range = modeDist*(modes+1);
        double[][] dist = new double[range][2];//XY coords
        for(int i = 0; i < range; i++) {
            dist[i][0] = i;
            dist[i][1] = estimator.estimate(new OffsetGmSample(i, new byte[8]));
        }

        return new DistTriple(estimator, samples, dist);
    }

    @Test
    public void testNormal() {
        long mean = 10;
        DistTriple data = createEstimator(1000, mean, 1, 1000, 1, 1);
        double est = data.estimator.estimate(new OffsetGmSample(mean, new byte[8]));
        Assert.assertTrue(Math.abs(est - 0.5) < 0.1);
    }
}
