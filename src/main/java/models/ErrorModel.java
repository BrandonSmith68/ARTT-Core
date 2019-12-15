package models;

import error_sample.representation.TimeErrorSample;

public abstract class ErrorModel {
    public abstract void addSample(TimeErrorSample sample);
    public abstract void processAMTLV(byte [] amtlv);
    public abstract ErrorModel duplicate();
}
