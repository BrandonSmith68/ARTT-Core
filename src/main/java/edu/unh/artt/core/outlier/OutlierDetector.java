package edu.unh.artt.core.outlier;

import edu.unh.artt.core.error_sample.representation.TimeErrorSample;
import edu.unh.artt.core.models.ErrorModel;

public abstract class OutlierDetector<Sample extends TimeErrorSample> {
    protected final ErrorModel<Sample> reference_model;

    public OutlierDetector(ErrorModel<Sample> refModel) {
        reference_model = refModel;
    }

    public abstract boolean isOutlier(Sample sample);
}
