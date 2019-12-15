package models.gaussian;

import error_sample.representation.TimeErrorSample;
import models.ErrorModel;

public class MultivariateGaussianModel extends ErrorModel {

    @Override
    public void addSample(TimeErrorSample sample) {
        //Todo this
    }

    @Override
    public void processAMTLV(byte [] amtlv) {

    }

    @Override
    public ErrorModel duplicate() {
        return this;
    }

    @Override
    public void merge(ErrorModel model) {

    }
}
