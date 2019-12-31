package edu.unh.artt.core.error_sample.processing;

import edu.unh.artt.core.error_sample.representation.TimeErrorSample;
import edu.unh.artt.core.error_sample.representation.AMTLVData;

import java.util.LinkedList;

public class SampleFactory {
    public static <T extends TimeErrorSample> LinkedList<T> processAMTLVData(AMTLVData<T> sample) {
        return new LinkedList<>();
    }
}
