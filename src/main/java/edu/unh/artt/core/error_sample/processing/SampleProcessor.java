package edu.unh.artt.core.error_sample.processing;

import edu.unh.artt.core.error_sample.representation.SyncData;
import edu.unh.artt.core.error_sample.representation.TimeErrorSample;
import edu.unh.artt.core.error_sample.representation.AMTLVData;
import org.apache.commons.codec.binary.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public abstract class SampleProcessor<Sample extends TimeErrorSample> {
    private final static Logger logger = LoggerFactory.getLogger(SampleProcessor.class);

    private final Vector<Consumer<Sample>> sample_consumers = new Vector<>();
    private final Vector<Consumer<AMTLVData<Sample>>> amtlv_consumers = new Vector<>();

    private final AtomicReference<GmData> most_recent_meas = new AtomicReference<>();
    private class GmData {
        final SyncData sync_data;
        final double mean_path_delay;
        final byte [] gm_id;

        GmData(SyncData dat, double pdelay, byte [] id) {
            sync_data = dat;
            mean_path_delay = pdelay;
            gm_id = id;
        }
    }

    public final void receivedGMSync(SyncData data, double meanPathDelay, byte [] gmIdentity) {
        GmData prev = most_recent_meas.getAndSet(new GmData(data, meanPathDelay, gmIdentity));
        if(prev != null && !Arrays.equals(gmIdentity, prev.gm_id)) {
            logger.info("Observed the grandmasterIdentity change from {} to {}. Computed time error will now be in" +
                    " reference to the new grandmaster.", Hex.encodeHexString(prev.gm_id), Hex.encodeHexString(gmIdentity));
        }
    }

    public final void receivedReverseSync(SyncData revSyncData, double peerMeanPathDelay) {
        GmData gmData = most_recent_meas.get();
        if(gmData != null) {
            Sample sample = computeTimeError(gmData.sync_data, gmData.mean_path_delay, revSyncData, peerMeanPathDelay);
            sample_consumers.parallelStream().forEach(action->action.accept(sample));
            amtlv_consumers.parallelStream().forEach(action->action.accept(processAMTLVData(revSyncData.amtlv)));
        }
    }

    public final void registerErrorComputeAction(Consumer<Sample> sampleConsumer) {
        sample_consumers.add(sampleConsumer);
    }

    public final void unregisterErrorComputeAction(Consumer<Sample> sampleConsumer) {
        sample_consumers.remove(sampleConsumer);
    }

    protected abstract AMTLVData<Sample> processAMTLVData(byte [] AMTLV);

    public final void onAMTLVReceipt(Consumer<AMTLVData<Sample>> amtlvConsumer) {
        amtlv_consumers.add(amtlvConsumer);
    }

    abstract Sample computeTimeError(SyncData gmSync, double upstrmPdelay, SyncData revSync, double dwnstrmPdelay);
}
