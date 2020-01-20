package edu.unh.artt.core.error_sample.representation;

import org.apache.commons.codec.binary.Hex;

import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * Represents the offsetFromGm time error measurement. This is the result of a comparison between received Sync messages
 * from the grandmaster and link partners being monitored.
 */
public class OffsetGmSample implements TimeErrorSample {
    /* The computed offsetFromGm time error metric */
    final double offset_from_gm;

    /* ID of the node this metric was generated for. Note that this will be 'empty' for re-sampled data since the
    * clockId of a sample is not a computed metric. This field is only useful for outliers. */
    final byte [] clock_identity;

    /* Network representation of this sample */
    final long weight;

    /**
     * @see OffsetGmSample#OffsetGmSample(long, double, byte[] empty)
     */
    public OffsetGmSample(long weight, double offsetFromGmScaled) {
        this(weight, offsetFromGmScaled, new byte[8]);
    }

    /**
     * @param weight Network representation of the sample
     * @param offsetFromGmScaled Computed offset metric
     * @param clockId clock identity that the sample is associated with
     */
    public OffsetGmSample(long weight, double offsetFromGmScaled, byte [] clockId) {
        offset_from_gm = offsetFromGmScaled;
        clock_identity = clockId;
        this.weight = weight;
    }

    @Override
    public List<OffsetGmSample> parseSamples(List<double[]> sampleData) {
        return sampleData.stream().map(darr -> new OffsetGmSample(1, darr[0])).collect(Collectors.toList());
    }

    /**
     * @return Computed offsetFromGm sample
     */
    @Override
    public double[] getSample() {
        return new double[] {offset_from_gm};
    }

    /**
     * @return Network representation of the sample
     */
    @Override
    public long getWeight() {
        return weight;
    }

    /**
     * @return Identification for the source of the data
     */
    @Override
    public String getIdentifier() {
        return Hex.encodeHexString(clock_identity) + "; " + offset_from_gm +"ns; " + weight + "nodes;";
    }

    /**
     * @return Clock identity associated with this
     */
    public byte [] getClockIdentity() {
        return clock_identity;
    }

    /**
     * @return 1 dimension
     */
    @Override
    public int getNumDimensions() {
        return 1;
    }
}
