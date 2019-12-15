package error_sample.processing;

import error_sample.representation.SyncData;
import error_sample.representation.TimeErrorSample;
import org.apache.commons.codec.binary.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public abstract class SampleProcessor {
    private final static Logger logger = LoggerFactory.getLogger(SampleProcessor.class);

    private final SynchronousQueue<Consumer<TimeErrorSample>> sample_consumers = new SynchronousQueue<>();

    private final AtomicReference<GmData> most_recent_meas = new AtomicReference<>();
    private class GmData {
        final SyncData data;
        final double meanPathDelay;
        final byte [] gm_id;

        GmData(SyncData dat, double pdelay, byte [] id) {
            data = dat;
            meanPathDelay = pdelay;
            gm_id = id;
        }
    }

    public final void receivedGMSync(SyncData data, double meanPathDelay, byte [] gmIdentity) {
        GmData prev = most_recent_meas.getAndSet(new GmData(data, meanPathDelay, gmIdentity));
        if(!Arrays.equals(gmIdentity, prev.gm_id)) {
            logger.info("Observed the grandmasterIdentity change from {} to {}. Computed time error will now be in" +
                    " reference to the new grandmaster.", Hex.encodeHexString(prev.gm_id), Hex.encodeHexString(gmIdentity));
        }
    }

    public final void receivedReverseSync(SyncData data, double peerMeanPathDelay) {
        GmData dat = most_recent_meas.get();
        if(dat != null) {
            TimeErrorSample sample = process(dat.data, dat.meanPathDelay, data, peerMeanPathDelay);
            sample_consumers.parallelStream().forEach(action->action.accept(sample));
        }
    }

    public final void registerErrorComputeAction(Consumer<TimeErrorSample> sampleConsumer) {
        sample_consumers.add(sampleConsumer);
    }

    public final void unregisterErrorComputeAction(Consumer<TimeErrorSample> sampleConsumer) {
        sample_consumers.remove(sampleConsumer);
    }

    abstract TimeErrorSample process(SyncData gmSync, double upstrmPdelay, SyncData revSync, double dwnstrmPdelay);
}
