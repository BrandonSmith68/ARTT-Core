package error_sample.processing;

import error_sample.representation.TimeErrorSample;

import java.util.LinkedList;

public class SampleFactory {
    public static <T extends TimeErrorSample> LinkedList<T> processAMTLVData(byte amtlvID, byte [] amtlvData) {
        return new LinkedList<>();
    }
}
