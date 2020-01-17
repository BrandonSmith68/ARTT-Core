package edu.unh.artt.core.error_sample.representation;

import java.util.List;

public interface TimeErrorSample {
    double [] getSample();
    long getWeight();
    String getIdentifier();
    int getNumDimensions();
}
