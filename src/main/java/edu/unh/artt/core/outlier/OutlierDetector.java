package edu.unh.artt.core.outlier;

import edu.unh.artt.core.error_sample.representation.TimeErrorSample;
import edu.unh.artt.core.models.ErrorModel;

/**
 * Outlier detection mechanism to use when adding new samples and when filtering outliers from the downstream network.
 * @param <Sample> Sample type to operate over.
 */
public abstract class OutlierDetector<Sample extends TimeErrorSample> {

    /* Model used to characterize normal behavior */
    protected final ErrorModel<Sample> reference_model;

    /**
     * @param refModel Model used to characterize normal behavior
     */
    public OutlierDetector(ErrorModel<Sample> refModel) {
        reference_model = refModel;
    }

    /**
     * Determines whether or not the sample is an outlier w.r.t. to the supplied reference model
     * @param sample Sample to check
     * @return Whether or not the sample is considered an outlier
     */
    public abstract boolean isOutlier(Sample sample);
}
