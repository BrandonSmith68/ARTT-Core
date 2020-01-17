package edu.unh.artt.core.error_sample.processing;

import edu.unh.artt.core.error_sample.representation.AMTLVData;
import edu.unh.artt.core.error_sample.representation.OffsetGmSample;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.Assert.*;

public class OffsetSampleProcessorTest {

    @Test
    public void computeTimeError() {

    }

    @Test
    public void processAMTLVData() {
    }

    @Test
    public void packageAMTLVData() {
    }

    @Test
    public void amtlvToBytes() {
        List<OffsetGmSample> outliers = IntStream.range(0, 1).mapToObj(i -> new OffsetGmSample(0, 2, new byte[]{0,(byte) 0xff,(byte) 0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, 0})).collect(Collectors.toList() );
        double[][] samples = IntStream.range(0, 182).mapToObj(i -> new double[]{1.}).toArray(double[][]::new);

        OffsetSampleProcessor processor = new OffsetSampleProcessor();
        AMTLVData<OffsetGmSample> amtlvData = processor.packageAMTLVData(1, outliers, samples);
        List<byte[]> networkData = processor.amtlvToBytes(amtlvData, 1480);
        System.out.println("Test");
    }
}