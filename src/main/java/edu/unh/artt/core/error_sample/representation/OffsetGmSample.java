package edu.unh.artt.core.error_sample.representation;

import org.apache.commons.codec.binary.Hex;

public class OffsetGmSample implements TimeErrorSample {
    final long offset_from_gm;
    final byte [] clock_identity;

    public OffsetGmSample(long offsetFromGm, byte [] clockId) {
        offset_from_gm = offsetFromGm;
        clock_identity = clockId;
    }

    @Override
    public double[] getSample() {
        return new double[] {offset_from_gm};
    }

    @Override
    public String getIdentifier() {
        return Hex.encodeHexString(clock_identity);
    }

    @Override
    public int getNumDimensions() {
        return 1;
    }
}
