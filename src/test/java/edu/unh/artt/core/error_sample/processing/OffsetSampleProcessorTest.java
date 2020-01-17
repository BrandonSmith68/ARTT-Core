package edu.unh.artt.core.error_sample.processing;

import edu.unh.artt.core.error_sample.representation.AMTLVData;
import edu.unh.artt.core.error_sample.representation.OffsetGmSample;
import org.junit.Test;

import java.math.BigInteger;
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
        byte [] outlierId = new byte[]{0,(byte) 0xff,(byte) 0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, 0};
        testAmtlvToBytesHelper(1, 182, 0, 2, -1, 1, outlierId);
        testAmtlvToBytesHelper(1, 1, 0, 2, -1, 45, outlierId);
        testAmtlvToBytesHelper(91, 1, 0, 2, -1, 10000, outlierId);
        testAmtlvToBytesHelper(99, 1000, 0, 2, -1, 33, outlierId);
        testAmtlvToBytesHelper(1000, 1000, 0, 2, -1, Integer.MAX_VALUE, outlierId);
        testAmtlvToBytesHelper(50, 50, -1, -500, 0, 1, outlierId);
        testAmtlvToBytesHelper(50, 50, -1, -500, 1000L + Integer.MAX_VALUE, 1, outlierId);
        testAmtlvToBytesHelper(100, 2000, 0, 2, 1000L-Integer.MIN_VALUE, 1, outlierId);
        testAmtlvToBytesHelper(1, 182, Integer.MIN_VALUE, 2, -1, 1, outlierId);

        testAmtlvToBytesMultiHelper(1500, 1500, 1500, 0, 2, -1, 45, outlierId);
    }

    private void testAmtlvToBytesHelper(int numOutl, int numSamp, int outlWeight, long outlOff, long smplOff, int smplWt, byte [] outlierId) {
        int totalSize = numOutl * 16 + numSamp * 8 + 8;
        List<OffsetGmSample> outliers = IntStream.range(0, numOutl).mapToObj(i -> new OffsetGmSample(outlWeight, outlOff, outlierId)).collect(Collectors.toList());
        double[][] samples = IntStream.range(0, numSamp).mapToObj(i -> new double[]{smplOff}).toArray(double[][]::new);

        OffsetSampleProcessor processor = new OffsetSampleProcessor();
        AMTLVData<OffsetGmSample> amtlvData = processor.packageAMTLVData(smplWt, outliers, samples);
        List<byte[]> networkData = processor.amtlvToBytes(amtlvData, totalSize);

        assertEquals(1, networkData.size());
        byte [] data = networkData.get(0);
        assertEquals(data.length, totalSize);

        //Local model so weight should always be 1
        assertEquals(smplWt, new BigInteger(Arrays.copyOfRange(data, 0, 4)).longValue());

        //Check length parsing
        assertEquals(numSamp * 8, new BigInteger(Arrays.copyOfRange(data, 4, 6)).longValue());
        assertEquals(numOutl * 16, new BigInteger(Arrays.copyOfRange(data, 6, 8)).longValue());

        for(int i = 8; i < numSamp * 8; i+= 8)
            assertEquals(smplOff, new BigInteger(Arrays.copyOfRange(data, i, i+8)).longValue() / OffsetSampleProcessor.SCALED_NS_CONVERSION);

        for(int i = numSamp * 8 + 8; i < totalSize; i+=16) {
            assertEquals(outlOff, new BigInteger(Arrays.copyOfRange(data, i, i+8)).longValue() / OffsetSampleProcessor.SCALED_NS_CONVERSION);
            assertArrayEquals(outlierId, Arrays.copyOfRange(data, i+8, i+16));
        }
    }

    private void testAmtlvToBytesMultiHelper(int maxFrameSize, int numOutl, int numSamp, int outlWeight, long outlOff, long smplOff, int smplWt, byte [] outlierId) {
        int sampleSize = numSamp * 8;
        int outlSize = numOutl * 16;
        int numTLVs = (sampleSize + outlSize) / maxFrameSize;
        int totalSize = sampleSize + outlSize + numTLVs * 8; //Add on the headers
        numTLVs = totalSize / maxFrameSize;
        if(maxFrameSize % totalSize > 0) {
            numTLVs++;
            totalSize += 8;
        }

        List<OffsetGmSample> outliers = IntStream.range(0, numOutl).mapToObj(i -> new OffsetGmSample(outlWeight, outlOff, outlierId)).collect(Collectors.toList());
        double[][] samples = IntStream.range(0, numSamp).mapToObj(i -> new double[]{smplOff}).toArray(double[][]::new);

        OffsetSampleProcessor processor = new OffsetSampleProcessor();
        AMTLVData<OffsetGmSample> amtlvData = processor.packageAMTLVData(smplWt, outliers, samples);
        List<byte[]> networkData = processor.amtlvToBytes(amtlvData, maxFrameSize);

        assertEquals(numTLVs, networkData.size());
        assertEquals(totalSize, networkData.stream().mapToInt(b->b.length).sum());
        assertEquals(smplWt, networkData.stream().mapToLong(b->new BigInteger(Arrays.copyOfRange(b, 0, 4)).longValue()).distinct().sum());
        assertEquals(sampleSize, networkData.stream().mapToLong(b->new BigInteger(Arrays.copyOfRange(b, 4, 6)).longValue()).sum());
        assertEquals(outlSize, networkData.stream().mapToLong(b->new BigInteger(Arrays.copyOfRange(b, 6, 8)).longValue()).sum());

        int remSmpls = sampleSize, remOutl = outlSize;
        for(byte [] data : networkData) {
            int smpls2Chk = Math.min(remSmpls, maxFrameSize - (maxFrameSize % 8));
            for (int i = 8; i < smpls2Chk; i += 8)
                assertEquals(smplOff, new BigInteger(Arrays.copyOfRange(data, i, i + 8)).longValue() / OffsetSampleProcessor.SCALED_NS_CONVERSION);
            remSmpls -= smpls2Chk;

            int smplDiff = maxFrameSize - smpls2Chk;
            int outls2Chck = Math.min(remOutl, smplDiff - (smplDiff % 16));
            int offs = smpls2Chk;
            for(int i = offs; i < offs + outls2Chck; i+=16) {
                assertEquals(outlOff, new BigInteger(Arrays.copyOfRange(data, i, i + 8)).longValue() / OffsetSampleProcessor.SCALED_NS_CONVERSION);
                assertArrayEquals(outlierId, Arrays.copyOfRange(data, i+8, i+16));
            }
        }
    }
}