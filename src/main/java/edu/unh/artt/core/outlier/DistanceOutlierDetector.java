package edu.unh.artt.core.outlier;

import edu.unh.artt.core.error_sample.representation.TimeErrorSample;
import edu.unh.artt.core.models.ErrorModel;

public class DistanceOutlierDetector<Sample extends TimeErrorSample> extends OutlierDetector<Sample> {

    public DistanceOutlierDetector(ErrorModel<Sample> refModel, double neighborDist) {
        super(refModel);
        //Todo do something with the neighborhood distance args
    }

    @Override
    public boolean isOutlier(Sample sample) {
        //Todo check distances using estimate
        return false;
    }
}
