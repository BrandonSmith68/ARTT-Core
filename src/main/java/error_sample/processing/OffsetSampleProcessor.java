package error_sample.processing;

import error_sample.representation.OffsetGmSample;
import error_sample.representation.SyncData;
import error_sample.representation.TimeErrorSample;

import java.math.BigInteger;

public class OffsetSampleProcessor extends SampleProcessor {
    @Override
    TimeErrorSample process(SyncData gmSync, double upstrmPdelay, SyncData revSync, double dwnstrmPdelay) {
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
    public String toString() {
        return "Offset from GM Sample Processor";
    }
}
