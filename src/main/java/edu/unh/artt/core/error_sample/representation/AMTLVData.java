package edu.unh.artt.core.error_sample.representation;

import edu.unh.artt.core.error_sample.representation.TimeErrorSample;

import java.util.Collections;
import java.util.List;

public class AMTLVData<Sample extends TimeErrorSample> {
    public final List<Sample> subnetwork_samples;
    public final List<Sample> subnetwork_outliers;

    public AMTLVData(List<Sample> samples, List<Sample> outliers) {
        subnetwork_samples = Collections.unmodifiableList(samples);
        subnetwork_outliers = Collections.unmodifiableList(outliers);
    }
}
