package models.kernel_estimator;

import error_sample.representation.OffsetGmSample;
import models.KernelDensityEstimator;
import org.junit.Test;
import smile.stat.distribution.KernelDensity;

import java.util.Arrays;
import java.util.Random;

public class TestKernelDensityEstimator {

    @Test
    public void testNormal() {
        KernelDensityEstimator<OffsetGmSample> estimator = new KernelDensityEstimator<>(98);
        Random r = new Random();
        double mean = 10.;
        double variance = 2.;
        long [] samples = new long[100];

        System.out.println("Samples: ");
        for(int i = 0; i < 100; i ++) {
            samples[i] = Math.round(mean + r.nextGaussian()*variance);
            estimator.addSample(new OffsetGmSample(samples[i], new byte [8]));
            System.out.println(samples[i]);
        }

        double [] dsamsp = Arrays.stream(samples).asDoubleStream().toArray();
        KernelDensity estimator1 = new KernelDensity(dsamsp);

        System.out.println("Distribution");
        for(int i = 0; i < 100; i++)
            System.out.println(  i + "  :  " + estimator1.p(i));
    }
}
