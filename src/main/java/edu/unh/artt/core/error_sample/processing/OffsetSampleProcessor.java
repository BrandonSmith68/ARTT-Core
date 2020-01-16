package edu.unh.artt.core.error_sample.processing;

import edu.unh.artt.core.error_sample.representation.AMTLVData;
import edu.unh.artt.core.error_sample.representation.OffsetGmSample;
import edu.unh.artt.core.error_sample.representation.SyncData;
import org.apache.commons.codec.binary.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

public class OffsetSampleProcessor extends SampleProcessor<OffsetGmSample> {
    private final static Logger logger = LoggerFactory.getLogger(OffsetSampleProcessor.class);

    private final HashMap<String, Long> network_rep = new HashMap<>();

    @Override
    OffsetGmSample computeTimeError(SyncData gmSync, double upstrmPdelay, SyncData revSync, double dwnstrmPdelay) {
        long t1Gm = gmSync.origin_timestamp.getTimestamp();
        long t1Peer = revSync.origin_timestamp.getTimestamp();

        long t2Gm = gmSync.sync_receipt.getTimestamp();
        long t2Peer = revSync.sync_receipt.getTimestamp();

        BigInteger upstrmCorr = new BigInteger(gmSync.correction_field).add(BigInteger.valueOf(Math.round(upstrmPdelay)));
        BigInteger dwnstrmCorr = new BigInteger(revSync.correction_field).add(BigInteger.valueOf(Math.round(dwnstrmPdelay)));

        long offsetFromGm = (t1Peer - t1Gm) + (t2Gm - t2Peer) + (dwnstrmCorr.subtract(upstrmCorr)).longValue();
        return new OffsetGmSample((short)1, offsetFromGm, revSync.clock_identity);
    }

    @Override
    protected AMTLVData<OffsetGmSample> processAMTLVData(byte [] rxClockId, byte[] amtlv) {
        long weight = new BigInteger(Arrays.copyOfRange(amtlv, 0, 4)).longValue(); //Want unsigned
        int sampleLen = 0xffff & new BigInteger(Arrays.copyOfRange(amtlv, 0, 2)).intValue();
        int outlierLen = 0xffff & new BigInteger(Arrays.copyOfRange(amtlv, 2, 4)).intValue();

        if((amtlv.length-8) != outlierLen + sampleLen || (amtlv.length - 8) % 16 != 0) {
            logger.error("Failed to process offsetFromGm amtlv because it was incorrectly formatted.");
            return null;
        }

        network_rep.put(Hex.encodeHexString(rxClockId), weight);

        List<OffsetGmSample> samples = new LinkedList<>();
        List<OffsetGmSample> outliers = new LinkedList<>();
        for(int i = 0; i < amtlv.length; i += 16) {
            long offset = new BigInteger(Arrays.copyOfRange(amtlv, i, i+8)).longValue();
            byte [] clockId = Arrays.copyOfRange(amtlv, i+8, i+16);
            OffsetGmSample sample = new OffsetGmSample((short)1, offset, clockId);
            ((i < sampleLen) ? samples : outliers).add(sample);
        }

        return new AMTLVData<>(weight, rxClockId, samples, outliers);
    }

    @Override
    public long getNetworkRepresentation() {
        return network_rep.values().stream().mapToLong(Long::longValue).sum(); //Sum of all reported representations
    }



    @Override
    public AMTLVData<OffsetGmSample> packageAMTLVData(long networkRep, List<OffsetGmSample> outliers, double[][] resampledData) {
        //TODO Package these into AMTLV byte arrays
        return null;
    }

    @Override
    public String toString() {
        return "Offset from GM Sample Processor";
    }
}
