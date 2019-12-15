package models;

import error_sample.representation.TimeErrorSample;

public abstract class ErrorModel {
    //todo may want to maintain the moving window from this class
    public abstract void addSample(TimeErrorSample sample);
    public abstract void processAMTLV(byte [] amtlv);
    public abstract ErrorModel duplicate();
    public abstract void merge(ErrorModel model);
}
