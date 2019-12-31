package edu.unh.artt.core.error_sample.processing;

import edu.unh.artt.core.error_sample.representation.OffsetGmSample;
import edu.unh.artt.core.error_sample.representation.SyncData;
import edu.unh.artt.core.error_sample.representation.AMTLVData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class OffsetSampleProcessor extends SampleProcessor<OffsetGmSample> {
    private final static Logger logger = LoggerFactory.getLogger(OffsetSampleProcessor.class);

    @Override
    OffsetGmSample computeTimeError(SyncData gmSync, double upstrmPdelay, SyncData revSync, double dwnstrmPdelay) {
        BigInteger t1Gm = gmSync.origin_timestamp.getTimestamp();
        BigInteger t1Peer = revSync.origin_timestamp.getTimestamp();

        BigInteger t2Gm = gmSync.sync_receipt.getTimestamp();
        BigInteger t2Peer = revSync.sync_receipt.getTimestamp();

        BigInteger upstrmCorr = new BigInteger(gmSync.correction_field).add(BigInteger.valueOf(Math.round(upstrmPdelay)));
        BigInteger dwnstrmCorr = new BigInteger(revSync.correction_field).add(BigInteger.valueOf(Math.round(dwnstrmPdelay)));

        long offsetFromGm = (t1Peer.subtract(t1Gm)).add((t2Gm.subtract(t2Peer))).add(dwnstrmCorr.subtract(upstrmCorr)).longValue();
        return new OffsetGmSample(offsetFromGm, revSync.clock_identity);
    }

    @Override
    protected AMTLVData<OffsetGmSample> processAMTLVData(byte[] amtlv) {
        int sampleLen = 0xffff & new BigInteger(Arrays.copyOfRange(amtlv, 0, 2)).intValue();
        int outlierLen = 0xffff & new BigInteger(Arrays.copyOfRange(amtlv, 2, 4)).intValue();

        if((amtlv.length-4) != outlierLen + sampleLen || (amtlv.length - 4) % 16 != 0) {
            logger.error("Failed to process offsetFromGm amtlv because it was incorrectly formatted.");
            return null;
        }

        List<OffsetGmSample> samples = new LinkedList<>();
        List<OffsetGmSample> outliers = new LinkedList<>();
        for(int i = 0; i < amtlv.length; i += 16) {
            long offset = new BigInteger(Arrays.copyOfRange(amtlv, i, i+8)).longValue();
            byte [] clockId = Arrays.copyOfRange(amtlv, i+8, i+16);
            OffsetGmSample sample = new OffsetGmSample(offset, clockId);
            ((i < sampleLen) ? samples : outliers).add(sample);
        }

        return new AMTLVData<>(samples, outliers);
    }

    @Override
    public String toString() {
        return "Offset from GM Sample Processor";
    }
}
