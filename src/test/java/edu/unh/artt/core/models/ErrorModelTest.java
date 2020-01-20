package edu.unh.artt.core.models;

import edu.unh.artt.core.error_sample.representation.OffsetGmSample;
import org.junit.Test;

import java.util.LinkedList;

import static org.junit.Assert.*;

public class ErrorModelTest {

    @Test
    public void addSample() {
        int sampleSize = 100;
        ErrorModel<OffsetGmSample> model = new ErrorModel<>(sampleSize, 1 ) {
            @Override
            public void shutdown() {}
            @Override
            protected void computeMetrics(LinkedList<OffsetGmSample> sampleIterator) {}
            @Override
            protected double[][] resampleImpl(int newWindow) {return new double[0][]; }
            @Override
            public double estimate(OffsetGmSample point) {return 0; }
            @Override
            public double[] estimate(OffsetGmSample[] pointWindow) {return new double[0];}
            @Override
            public double[] getMean() {return new double[0]; }
            @Override
            public double[] getVariance() {return new double[0];}
            @Override
            public double[] getStandardDeviation() {return new double[0];}
        };

        assertEquals(sampleSize, model.getLocalWindowSize());
        sampleSize = 200;
        model.modifyWindowSize(sampleSize);
        assertEquals(sampleSize, model.getLocalWindowSize());
        assertEquals(model.num_dimensions, 1);

        try {
            model.addSample(new OffsetGmSample(1,1) {
                @Override
                public int getNumDimensions() {
                    return 2;
                }
            });
            fail("Model added a sample data point that had a differing dimensionality");
        } catch (IllegalArgumentException ignored) {}

        assertFalse(model.shouldResample(null));

        for(int i = 0; i < sampleSize-2; i++) {
            model.addSample(new OffsetGmSample(1, 1));
            assertFalse(model.shouldResample(null));
        }
        model.addSample(new OffsetGmSample(1, 1));
        assertTrue(model.shouldResample(null));

        model.resample(100);

        for(int i = 0; i < sampleSize-1; i++) {
            model.addSample(new OffsetGmSample(1, 1));
            assertFalse(model.shouldResample(null));
        }
        model.addSample(new OffsetGmSample(1, 1));
        assertTrue(model.shouldResample(null));

        assertEquals(sampleSize, model.sample_window.size());
    }
}