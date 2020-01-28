package edu.unh.artt.core.error_sample.representation;

import java.util.Collections;
import java.util.List;

/**
 * Represents the data needed to fill the data field of an AMTLV. Note that this class can represent multiple AMTLVs
 * depending on the max frame size of transmitted packets.
 * @param <Sample> Sample type to represent
 */
public class AMTLVData<Sample extends TimeErrorSample> {
    /* Network representation of the AMTLV */
    public final long weight;
    /* Clock ID of the device generating the AMTLV. */
    public final byte [] clock_id;
    /* Timestamp of when the TLV was received */
    public final long timestamp;

    /* List of samples represented by the AMTLV. Size should match the network sample size */
    public final List<Sample> subnetwork_samples;
    /* List of outliers represented by the AMTLV. Can be any size */
    public final List<Sample> subnetwork_outliers;

    public AMTLVData(long timestamp, long weight, byte [] clockId, List<Sample> samples, List<Sample> outliers) {
        this.weight = weight;
        clock_id = clockId;
        subnetwork_samples = Collections.unmodifiableList(samples);
        subnetwork_outliers = Collections.unmodifiableList(outliers);
        this.timestamp = timestamp;
    }
}
