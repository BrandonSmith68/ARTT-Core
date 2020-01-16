package edu.unh.artt.core.error_sample.processing;

import edu.unh.artt.core.error_sample.representation.AMTLVData;
import edu.unh.artt.core.error_sample.representation.SyncData;
import edu.unh.artt.core.error_sample.representation.TimeErrorSample;
import org.apache.commons.codec.binary.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Handles processing of Sync messages received from the grandmaster and reverse Sync messages received from the
 * downstream network. This class utilizes a callback mechanism to notify when newly computed information is received.
 * This class is also responsible for parsing and encoding Sample & AMTLV information. When creating a new sample type
 * a SampleProcessor must be implemented that supports the new sample type.
 *
 * Note that this class does not handle the protocol-specific aspects of 1588, since this is a library meant to be used
 * by the protocol stack.
 *
 * @param <Sample> Sample type to process
 */
public abstract class SampleProcessor<Sample extends TimeErrorSample> {
    private final static Logger logger = LoggerFactory.getLogger(SampleProcessor.class);

    /* List of callbacks to run when a new sample is computed */
    private final Vector<Consumer<Sample>> sample_consumers = new Vector<>();

    /* List of callbacks to run when a new AMTLV is parsed */
    private final Vector<Consumer<AMTLVData<Sample>>> amtlv_consumers = new Vector<>();

    /* Represents the sync message received most recently from the grandmaster. */
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

    /**
     * Method to be called when new information from the grandmaster is received.
     * @param data Data parsed from a Sync message
     * @param meanPathDelay Observed mean path delay
     * @param gmIdentity Grandmaster identity associated with the Sync being received
     */
    public final void receivedGMSync(SyncData data, double meanPathDelay, byte [] gmIdentity) {
        GmData prev = most_recent_meas.getAndSet(new GmData(data, meanPathDelay, gmIdentity));
        if(prev != null && !Arrays.equals(gmIdentity, prev.gm_id)) {
            logger.info("Observed the grandmasterIdentity change from {} to {}. Computed time error will now be in" +
                    " reference to the new grandmaster.", Hex.encodeHexString(prev.gm_id), Hex.encodeHexString(gmIdentity));
        }
    }

    /**
     * Method to be called when new information from a downstream partner is received (reverse Sync).
     * @param revSyncData Data parsed from the reverse sync
     * @param peerMeanPathDelay Mean path delay between the current node and the direct link partner
     */
    public final void receivedReverseSync(SyncData revSyncData, double peerMeanPathDelay) {
        GmData gmData = most_recent_meas.get();
        if(gmData != null) {
            Sample sample = computeTimeError(gmData.sync_data, gmData.mean_path_delay, revSyncData, peerMeanPathDelay);
            sample_consumers.parallelStream().forEach(action->action.accept(sample));
            amtlv_consumers.parallelStream().forEach(action->action.accept(
                    processAMTLVData(revSyncData.clock_identity, revSyncData.amtlv)));
        }
    }

    /**
     * @return The number of nodes represented by the downstream network. These are parsed from received TLVs and
     * summed together.
     */
    public abstract long getNetworkRepresentation();

    /**
     * Processes the data field of a received AMTLV.
     * @param clockId Clock id of the device sending the AMTLV
     * @param AMTLV Data field of the AMTLV
     * @return A parsed representation of the information contained within the AMTLV
     */
    protected abstract AMTLVData<Sample> processAMTLVData(byte[] clockId, byte [] AMTLV);

    /**
     * Packages newly computed data into an AMTLV to be transmitted upstream
     * @param networkRep Number of nodes represented by the total network
     * @param outliers Contents of the outlier buffer
     * @param resampledData Data sampled from the time error models
     * @return AMTLV Java representation
     */
    public abstract AMTLVData<Sample> packageAMTLVData(long networkRep, List<Sample> outliers, double [][] resampledData);

    /**
     * Converts an AMTLV class to a byte representation for network transmission
     * @param amtlv AMTLV data
     * @return The data field of an AMTLV
     */
    public abstract byte [] amtlvToBytes(AMTLVData<Sample> amtlv);

    /**
     * The core method of the SampleProcessor, where information from the grandmaster is compared with the information
     * from a reverse sync message to compute the associated time error.
     * @param gmSync Sync data from the grandmaster
     * @param upstrmPdelay Upstream path delay
     * @param revSync reverse Sync data
     * @param dwnstrmPdelay Downstream path delay
     * @return A new time error sample
     */
    abstract Sample computeTimeError(SyncData gmSync, double upstrmPdelay, SyncData revSync, double dwnstrmPdelay);

    /**
     * Registers an action to be run when a new sample has been computed
     * @param sampleConsumer Callback action
     */
    public final void registerErrorComputeAction(Consumer<Sample> sampleConsumer) {
        sample_consumers.add(sampleConsumer);
    }

    /**
     * Removes a previously registered action for when a new sample is computed
     * @param sampleConsumer Callback action
     */
    public final void unregisterErrorComputeAction(Consumer<Sample> sampleConsumer) {
        sample_consumers.remove(sampleConsumer);
    }

    /**
     * Registers and action to be run when a new AMTLV has been parsed
     * @param amtlvConsumer Callback action
     */
    public final void onAMTLVReceipt(Consumer<AMTLVData<Sample>> amtlvConsumer) {
        amtlv_consumers.add(amtlvConsumer);
    }

    /**
     * Removes a previously registered action for when a new AMTLV has been parsed
     * @param amtlvConsumer Callback action
     */
    public final void unregisterAMTLVReceiptAction(Consumer<AMTLVData<Sample>> amtlvConsumer) {
        amtlv_consumers.add(amtlvConsumer);
    }
}
