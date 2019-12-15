package models;

import sample_representation.TimeErrorSample;

public interface ErrorModel {
    void addSample(TimeErrorSample sample);
}
