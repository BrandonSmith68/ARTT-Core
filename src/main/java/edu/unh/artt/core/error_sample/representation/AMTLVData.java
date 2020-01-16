package edu.unh.artt.core.error_sample.representation;

import java.util.Collections;
import java.util.List;

public class AMTLVData<Sample extends TimeErrorSample> {
    public final long weight;
    public final byte [] clock_id;
    public final List<Sample> subnetwork_samples;
    public final List<Sample> subnetwork_outliers;

    public AMTLVData(long weight, byte [] clockId, List<Sample> samples, List<Sample> outliers) {
        this.weight = weight;
        clock_id = clockId;
        subnetwork_samples = Collections.unmodifiableList(samples);
        subnetwork_outliers = Collections.unmodifiableList(outliers);
    }
}
