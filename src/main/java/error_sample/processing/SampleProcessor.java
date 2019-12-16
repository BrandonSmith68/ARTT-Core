package error_sample.processing;

import error_sample.representation.SyncData;
import error_sample.representation.TimeErrorSample;
import org.apache.commons.codec.binary.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Vector;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public abstract class SampleProcessor {
    private final static Logger logger = LoggerFactory.getLogger(SampleProcessor.class);

    private final Vector<Consumer<TimeErrorSample>> sample_consumers = new Vector<>();
    private final Vector<Consumer<byte[]>> amtlv_consumers = new Vector<>();

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
            TimeErrorSample sample = computeTimeError(gmData.sync_data, gmData.mean_path_delay, revSyncData, peerMeanPathDelay);
            sample_consumers.parallelStream().forEach(action->action.accept(sample));
            amtlv_consumers.parallelStream().forEach(action->action.accept(revSyncData.amtlv));
        }
    }

    public final void registerErrorComputeAction(Consumer<TimeErrorSample> sampleConsumer) {
        sample_consumers.add(sampleConsumer);
    }

    public final void unregisterErrorComputeAction(Consumer<TimeErrorSample> sampleConsumer) {
        sample_consumers.remove(sampleConsumer);
    }

    public final void onAMTLVReceipt(Consumer<byte[]> amtlvConsumer) {
        amtlv_consumers.add(amtlvConsumer);
    }

    abstract TimeErrorSample computeTimeError(SyncData gmSync, double upstrmPdelay, SyncData revSync, double dwnstrmPdelay);
}
