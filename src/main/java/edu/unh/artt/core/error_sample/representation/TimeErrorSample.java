package edu.unh.artt.core.error_sample.representation;

import java.util.List;

/**
 * Represents a single sample of the time error for a device.
 */
public interface TimeErrorSample {
    /**
     * @return A representation of the sample as a multi-dimensional double floating point value.
     */
    double [] getSample();

    /**
     * @return Network representation of the given sample
     */
    long getWeight();

    /**
     * @return A semi-unique identifier for the sample.
     */
    String getIdentifier();

    /**
     * @return The number of dimensions represented by the sample.
     */
    int getNumDimensions();

    List<? extends TimeErrorSample> parseSamples(List<double[]> sampleData);
}
