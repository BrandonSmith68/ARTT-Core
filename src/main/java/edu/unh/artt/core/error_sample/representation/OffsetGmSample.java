package edu.unh.artt.core.error_sample.representation;

import org.apache.commons.codec.binary.Hex;

import java.util.Random;

public class OffsetGmSample implements TimeErrorSample {
    final long offset_from_gm;
    final byte [] clock_identity;
    final int weight;

    public OffsetGmSample(int weight, long offsetFromGm, byte [] clockId) {
        offset_from_gm = offsetFromGm;
        clock_identity = clockId;
        this.weight = weight;
    }

    Random r = new Random();

    @Override
    public double[] getSample() {
        return new double[] {r.nextFloat(), offset_from_gm};
    }

    @Override
    public int getWeight() {
        return weight;
    }

    @Override
    public String getIdentifier() {
        return Hex.encodeHexString(clock_identity);
    }

    @Override
    public int getNumDimensions() {
        return 2;
    }
}
