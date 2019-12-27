package models.kernel_estimator;

import error_sample.representation.OffsetGmSample;
import models.KernelDensityEstimator;
import org.junit.Test;

import java.util.Random;

public class TestKernelDensityEstimator {

    @Test
    public void testNormal() {
        KernelDensityEstimator<OffsetGmSample> estimator = new KernelDensityEstimator<>(198);
        Random r = new Random();
        double mean = 10.;
        double variance = 2.;
        int sampleCount = 200;
        int modes = 3;
        int modeDist = 20;

        System.out.println("Samples: ");
        for(int i = 0; i < sampleCount; i ++) {
            for(int m = 0; m < modes; m++) {
                long sample = Math.round(mean + (modeDist*m) + r.nextGaussian() * variance);
                estimator.addSample(new OffsetGmSample(sample, new byte[8]));
                System.out.println(sample);
            }
        }

        System.out.println("Distribution");
        for(int i = 0; i < modeDist*(modes + 1); i++)
            System.out.println(  estimator.estimate(new OffsetGmSample(i, new byte [8])));
    }
}
