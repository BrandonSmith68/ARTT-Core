package edu.unh.artt.core.error_sample.representation;

public interface TimeErrorSample {
    double [] getSample();
    int getWeight();
    String getIdentifier();
    int getNumDimensions();
}
