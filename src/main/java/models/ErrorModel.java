package models;

import error_sample.representation.TimeErrorSample;

public interface ErrorModel {
    void addSample(TimeErrorSample sample);
}
