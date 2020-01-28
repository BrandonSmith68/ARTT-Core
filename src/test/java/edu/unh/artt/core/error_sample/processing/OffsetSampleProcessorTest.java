package edu.unh.artt.core.error_sample.processing;

import edu.unh.artt.core.error_sample.representation.AMTLVData;
import edu.unh.artt.core.error_sample.representation.OffsetGmSample;
import edu.unh.artt.core.error_sample.representation.PTPTimestamp;
import edu.unh.artt.core.error_sample.representation.SyncData;
import org.junit.Test;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.Assert.*;

public class OffsetSampleProcessorTest {

    @Test
    public void computeTimeError() {
        AtomicLong gmT1 = new AtomicLong(100), gmT2 = new AtomicLong(200);
        AtomicLong gmCor = new AtomicLong(50);
        byte [] gmId = new byte[]{0,(byte) 0xff,(byte) 0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, 0};

        AtomicLong devT1 = new AtomicLong(10), devT2 = new AtomicLong(15);
        AtomicLong devCor = new AtomicLong(5);
        byte [] devId = new byte[]{0,(byte) 0x0a,(byte) 0x0a, (byte)0x0a, (byte)0x0a, (byte)0x0a, (byte)0x0a, 0};

        AtomicLong downstreamPdelay = new AtomicLong(5), upstreamPdelay = new AtomicLong(50);

        Runnable checkOffset = () -> {
            ByteBuffer gmCorrection = ByteBuffer.allocate(10);
            gmCorrection.putLong(gmCor.get()); //Byte buffer fills up to byte 8, so scaled ns conversion not needed

            ByteBuffer devCorrection = ByteBuffer.allocate(10);
            devCorrection.putLong(devCor.get());

            SyncData gmData = new SyncData(new PTPTimestamp(gmT1.get()), new PTPTimestamp(gmT2.get()), gmCorrection.array(), gmId, null);
            SyncData devData = new SyncData(new PTPTimestamp(devT1.get()), new PTPTimestamp(devT2.get()), devCorrection.array(), devId, null);

            OffsetSampleProcessor proc = new OffsetSampleProcessor();
            OffsetGmSample sample = proc.computeTimeError(gmData, upstreamPdelay.get(), devData, downstreamPdelay.get());

            long realOffset = (devT1.get() - gmT1.get()) + (gmT2.get() - devT2.get());
            realOffset += (downstreamPdelay.get() + devCor.get()) - (upstreamPdelay.get() + gmCor.get());
            assertEquals(realOffset, (long)sample.getSample()[0]);
        };

        checkOffset.run();

        gmCor.set(0);
        devCor.set(-5);

        checkOffset.run();

        devT2.set(2000);
        downstreamPdelay.set(1000);

        checkOffset.run();

        devT2.set(gmT2.get());
        devT1.set(gmT1.get());
        downstreamPdelay.set(upstreamPdelay.get());
        devCor.set(gmCor.get());

        checkOffset.run();

        devT2.set(Long.MAX_VALUE >> 16);
        devT1.set(Long.MIN_VALUE >> 16);
        downstreamPdelay.set(0);
        devCor.set(0);

        gmT2.set(Integer.MAX_VALUE);
        gmT1.set(Integer.MIN_VALUE);
        upstreamPdelay.set((long)1e9);
        gmCor.set(-1000);

        checkOffset.run();
    }

    @Test
    public void processAMTLVData() {
        int outlWeight = 1, smplWt = 1;
        byte [] outlierId = new byte[]{0,(byte) 0xff,(byte) 0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, 0};
        byte [] amtlvId = new byte[]{0,(byte) 0x0a,(byte) 0x0a, (byte)0x0a, (byte)0x0a, (byte)0x0a, (byte)0x0a, 0};

        int [] sizeOpts = new int[] {100, 1500, 0, 4095};
        long [] offsetOpts = new long[] {-1000L, Integer.MAX_VALUE, Long.MAX_VALUE>>16, Long.MIN_VALUE>>16};

        //Mind as well try all permutations :)
        for(int numSamp : sizeOpts) {
            for(int numOutl : sizeOpts) {
                if(numOutl+numSamp == 0) continue;
                for(long smplOff : offsetOpts) {
                    for(long outlOff : offsetOpts) {
                        byte[] tlv = testAmtlvToBytesHelper(numOutl, numSamp, outlWeight, outlOff, smplOff, smplWt, outlierId).get(0);
                        OffsetSampleProcessor sampleProcessor = new OffsetSampleProcessor();
                        AMTLVData<OffsetGmSample> amtlvData = sampleProcessor.processAMTLVData(0, amtlvId, tlv);

                        assertTrue(amtlvData.subnetwork_samples.stream().allMatch(s->s.getSample().length==1 && s.getSample()[0] == smplOff));
                        assertTrue(amtlvData.subnetwork_samples.stream().allMatch(s->s.getWeight() == smplWt));
                        assertTrue(amtlvData.subnetwork_outliers.stream().allMatch(s->s.getSample().length==1 && s.getSample()[0] == outlOff));
                        assertTrue(amtlvData.subnetwork_outliers.stream().allMatch(s->s.getWeight() == outlWeight));
                        assertTrue(amtlvData.subnetwork_outliers.stream().allMatch(s->Arrays.equals(s.getClockIdentity(), outlierId)));

                        assertArrayEquals(amtlvId, amtlvData.clock_id);
                        assertEquals(smplWt, amtlvData.weight);
                    }
                }
            }
        }
    }

    @Test
    public void packageAMTLVData() {
        int numOutl = 100, numSamp = 1000, outlWeight = 1, smplWt = 1;
        long outlOff = -1000, smplOff = 10;
        byte [] outlierId = new byte[]{0,(byte) 0xff,(byte) 0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, 0};
        List<OffsetGmSample> outliers = IntStream.range(0, numOutl).mapToObj(i -> new OffsetGmSample(0, outlWeight, outlOff, outlierId)).collect(Collectors.toList());
        double[][] samples = IntStream.range(0, numSamp).mapToObj(i -> new double[]{smplOff}).toArray(double[][]::new);

        OffsetSampleProcessor processor = new OffsetSampleProcessor();
        AMTLVData<OffsetGmSample> amtlvData = processor.packageAMTLVData(smplWt, outliers, samples);

        assertTrue(amtlvData.subnetwork_samples.stream().allMatch(s->s.getSample().length==1 && s.getSample()[0] == smplOff));
        assertTrue(amtlvData.subnetwork_samples.stream().allMatch(s->s.getWeight() == smplWt));
        assertTrue(amtlvData.subnetwork_outliers.stream().allMatch(s->s.getSample().length==1 && s.getSample()[0] == outlOff));
        assertTrue(amtlvData.subnetwork_outliers.stream().allMatch(s->s.getWeight() == outlWeight));
        assertTrue(amtlvData.subnetwork_outliers.stream().allMatch(s->Arrays.equals(s.getClockIdentity(), outlierId)));

        assertArrayEquals(new byte[8], amtlvData.clock_id);
        assertEquals(smplWt, amtlvData.weight);
    }

    @Test
    public void amtlvToBytes() {
        byte [] outlierId = new byte[]{0,(byte) 0xff,(byte) 0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, 0};
        testAmtlvToBytesHelper(1, 182, 0, 2, -1, 1, outlierId);
        testAmtlvToBytesHelper(1, 1, 0, 2, -1, 45, outlierId);
        testAmtlvToBytesHelper(91, 1, 0, 2, -1, 10000, outlierId);
        testAmtlvToBytesHelper(99, 1000, 0, Long.MAX_VALUE>>16, -1, 33, outlierId);
        testAmtlvToBytesHelper(1000, 1000, 0, 2, -1, Integer.MAX_VALUE, outlierId);
        testAmtlvToBytesHelper(50, 50, -1, -500, 0, 1, outlierId);
        testAmtlvToBytesHelper(50, 50, -1, -500, 1000L + Integer.MAX_VALUE, 1, outlierId);
        testAmtlvToBytesHelper(100, 2000, 0, 2, Long.MIN_VALUE >> 16, 1, outlierId);
        testAmtlvToBytesHelper(1, 182, Integer.MIN_VALUE, 2, -1, 1, outlierId);

        testAmtlvToBytesMultiHelper(1500, 1500, 1500, 0, 2, -1, 45, outlierId);
        testAmtlvToBytesMultiHelper(32, 64, 64, 0, 2, 100L + Integer.MIN_VALUE, -1, outlierId);
        testAmtlvToBytesMultiHelper(100, 800, 1, 0, Long.MIN_VALUE >> 16, 10, 0, outlierId);
        testAmtlvToBytesMultiHelper(100, 1, 1600, 0, Long.MAX_VALUE >> 16, -1, 0, outlierId);
    }

    private List<byte[]> testAmtlvToBytesHelper(int numOutl, int numSamp, int outlWeight, long outlOff, long smplOff, int smplWt, byte [] outlierId) {
        int totalSize = numOutl * 16 + numSamp * 8 + 8;
        List<OffsetGmSample> outliers = IntStream.range(0, numOutl).mapToObj(i -> new OffsetGmSample(0, outlWeight, outlOff, outlierId)).collect(Collectors.toList());
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
        assertEquals(numSamp * 8, 0xffff&new BigInteger(Arrays.copyOfRange(data, 4, 6)).longValue());
        assertEquals(numOutl * 16, 0xffff&new BigInteger(Arrays.copyOfRange(data, 6, 8)).longValue());

        for(int i = 8; i < numSamp * 8; i+= 8)
            assertEquals(smplOff, (long)PTPTimestamp.fromScaledNs(new BigInteger(Arrays.copyOfRange(data, i, i+8)).longValue()));

        for(int i = numSamp * 8 + 8; i < totalSize; i+=16) {
            assertEquals(outlOff, (long)PTPTimestamp.fromScaledNs(new BigInteger(Arrays.copyOfRange(data, i, i+8)).longValue()));
            assertArrayEquals(outlierId, Arrays.copyOfRange(data, i+8, i+16));
        }
        return networkData;
    }

    private void testAmtlvToBytesMultiHelper(int maxFrameSize, int numOutl, int numSamp, int outlWeight, long outlOff, long smplOff, int smplWt, byte [] outlierId) {
        int sampleSize = numSamp * 8;
        int outlSize = numOutl * 16;
        List<OffsetGmSample> outliers = IntStream.range(0, numOutl).mapToObj(i -> new OffsetGmSample(0,outlWeight, outlOff, outlierId)).collect(Collectors.toList());
        double[][] samples = IntStream.range(0, numSamp).mapToObj(i -> new double[]{smplOff}).toArray(double[][]::new);

        OffsetSampleProcessor processor = new OffsetSampleProcessor();
        AMTLVData<OffsetGmSample> amtlvData = processor.packageAMTLVData(smplWt, outliers, samples);
        List<byte[]> networkData = processor.amtlvToBytes(amtlvData, maxFrameSize);

        assertTrue(networkData.stream().allMatch(b->b.length <= maxFrameSize));
        assertEquals(outlSize + sampleSize + networkData.size()*8, networkData.stream().mapToLong(b->b.length).sum());
        assertEquals(smplWt, networkData.stream().mapToLong(b->new BigInteger(Arrays.copyOfRange(b, 0, 4)).longValue()).distinct().sum());
        assertEquals(sampleSize, networkData.stream().mapToLong(b->new BigInteger(Arrays.copyOfRange(b, 4, 6)).longValue()).sum());
        assertEquals(outlSize, networkData.stream().mapToLong(b->new BigInteger(Arrays.copyOfRange(b, 6, 8)).longValue()).sum());

        for(byte [] data : networkData) {
            int smpls2Chk = new BigInteger(Arrays.copyOfRange(data, 4, 6)).intValue();
            for (int i = 8; i < smpls2Chk; i += 8)
                assertEquals(smplOff, (long)PTPTimestamp.fromScaledNs(new BigInteger(Arrays.copyOfRange(data, i, i + 8)).longValue()));
            int off = smpls2Chk + 8;

            int outls2Chck = new BigInteger(Arrays.copyOfRange(data, 6, 8)).intValue();
            for(int i = off; i < off + outls2Chck; i+=16) {
                assertEquals(outlOff, (long)PTPTimestamp.fromScaledNs(new BigInteger(Arrays.copyOfRange(data, i, i + 8)).longValue()));
                assertArrayEquals(outlierId, Arrays.copyOfRange(data, i+8, i+16));
            }
        }
    }
}