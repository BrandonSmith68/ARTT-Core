package edu.unh.artt.core.error_sample.representation;

import org.apache.commons.codec.binary.Hex;

import java.util.Random;

/**
 * Represents the offsetFromGm time error measurement.
 */
public class OffsetGmSample implements TimeErrorSample {
    final double offset_from_gm;
    final byte [] clock_identity;
    final long weight;

    public OffsetGmSample(long weight, double offsetFromGmScaled) {
        this(weight, offsetFromGmScaled, null);
    }

    public OffsetGmSample(long weight, double offsetFromGmScaled, byte [] clockId) {
        offset_from_gm = offsetFromGmScaled;
        clock_identity = clockId;
        this.weight = weight;
    }

    @Override
    public double[] getSample() {
        return new double[] {offset_from_gm};
    }

    @Override
    public long getWeight() {
        return weight;
    }

    @Override
    public String getIdentifier() {
        return Hex.encodeHexString(clock_identity);
    }

    public byte [] getClockIdentity() {
        return clock_identity;
    }

    @Override
    public int getNumDimensions() {
        return 1;
    }
}
