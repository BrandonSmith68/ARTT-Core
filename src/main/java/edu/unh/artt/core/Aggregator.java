package edu.unh.artt.core;

import edu.unh.artt.core.error_sample.processing.SampleProcessor;
import edu.unh.artt.core.error_sample.representation.AMTLVData;
import edu.unh.artt.core.error_sample.representation.TimeErrorSample;
import edu.unh.artt.core.models.ErrorModel;
import edu.unh.artt.core.outlier.OutlierDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Implements the AMTLV Combination algorithm. The algorithm computes the time error of a node's direct link partners,
 * collects time error information computed by downstream nodes, and uses both data pools to form a model characterizing
 * the distribution of time error of the network while enabling distributed outlier detection mechanisms.
 *
 * This class operates by coordinating instances of an ErrorModel, SampleProcessor, and OutlierDetector to perform the
 * algorithm. This class does not set limits on the number of dimensions or size of a sample, but it does require that
 * all core elements utilize the same sample type. For the sake of configurability the user of an Aggregator instance
 * must instantiate the instances of ErrorModel, SampleProcessor, and OutlierDetector.
 * @param <Sample> Sample type to assume
 */
public class Aggregator<Sample extends TimeErrorSample> {
    final static Logger logger = LoggerFactory.getLogger(Aggregator.class);

    /* Model used to represent the distribution of the visible network */
    public final ErrorModel<Sample> network_model;
    /* Outlier detection methodology used for constructing the outlier list */
    private final OutlierDetector<Sample> network_outlier_detector;
    /* Core processing unit of the information received on both monitoring and observation ports */
    private final AtomicReference<SampleProcessor<Sample>> sample_processor = new AtomicReference<>();

    private final int network_window_size, //Size of the data set to be transmitted upstream
                      num_monitoring_ports; //Number of ports monitoring reverse sync messages

    /* Buffer with the most recently received outliers */
    private final AtomicReference<ArrayList<Sample>> outlier_buffer;
    /* Most recently transmitted AMTLV */
    private final AtomicReference<AMTLVData<Sample>> prev_tx_amtlv = new AtomicReference<>();

    /* Callback run when a new outlier has been detected */
    private final Vector<Consumer<Sample>> outlier_receipt_callbacks = new Vector<>();

    /**
     * @param processor Instance used to process information received on both observation and monitoring ports
     * @param networkModel Method used to develop the network model
     * @param networkDetector Outlier detection mechanism
     * @param numMonPorts Number of ports monitoring reverse sync messages
     * @param networkWindowSize The maximum number of samples allowed in an AMTLV
     */
    public Aggregator(SampleProcessor<Sample> processor, ErrorModel<Sample> networkModel,
                      OutlierDetector<Sample> networkDetector, int numMonPorts, int networkWindowSize) {
        network_model = networkModel;
        network_window_size = networkWindowSize;
        num_monitoring_ports = numMonPorts;

        int windowSize = network_model.getLocalWindowSize()*numMonPorts;
        network_model.modifyWindowSize(windowSize);
        outlier_buffer = new AtomicReference<>(new ArrayList<>(windowSize));

        network_outlier_detector = networkDetector;
        setSampleProcessor(processor);
    }

    /**
     * @return The information parsing methodology used for the combination algorithm.
     */
    public SampleProcessor<Sample> getSampleProcessor() {
        return sample_processor.get();
    }

    /**
     * @param proc The information parsing methodology to be used for the combination algorithm.
     */
    public void setSampleProcessor(SampleProcessor<Sample> proc) {
        logger.info("New sync messages are now being processed using a " + proc);

        //Process the results of the comparison between the observer port and monitor ports.
        proc.registerErrorComputeAction((sample -> {
            network_model.addSample(sample);

            if(network_model.hasReachedMinSampleWindow() && network_outlier_detector.isOutlier(sample)) {
                outlier_receipt_callbacks.forEach(c -> c.accept(sample));
                outlier_buffer.get().add(sample);
            }
        }));

        //Process the AMTLVs received on any monitoring port
        proc.onAMTLVReceipt((amtlv) -> {
            network_model.addSamples(amtlv.subnetwork_samples);
            amtlv.subnetwork_outliers.stream().filter(network_outlier_detector::isOutlier).forEach((smp) -> outlier_buffer.get().add(smp));
        });
        sample_processor.set(proc);
    }

    /**
     * Generates a new AMTLV to be transmitted upstream. The current outlier buffer will always be cleared out, with
     * every outlier being placed into the AMTLV. If the supplied error model deems itself to be significantly different
     * from the previously transmitted model then the model will be resampled and inserted into the AMTLV.
     * @param maxDataFieldSize The maximum allowed size of the data field for an AMTLV.
     *                         See the javadoc for {@link SampleProcessor#amtlvToBytes(AMTLVData, int)} and associated
     *                         child classes.
     * @return List of newly generated AMTLVs.
     */
    public List<byte []> retrieveNewData(int maxDataFieldSize) {
        ArrayList<Sample> outliers = outlier_buffer.getAndSet(new ArrayList<>());
        double[][] samples = (prev_tx_amtlv.get() != null && network_model.shouldResample(prev_tx_amtlv.get()))
                ? network_model.resample(network_window_size)
                : new double[0][];
        long totalWeight = num_monitoring_ports + sample_processor.get().getNetworkRepresentation();
        AMTLVData<Sample> amtlvData = sample_processor.get().packageAMTLVData(totalWeight, outliers, samples);
        prev_tx_amtlv.set(amtlvData);
        return sample_processor.get().amtlvToBytes(amtlvData, maxDataFieldSize);
    }

    public void registerOutlierReceiptCallback(Consumer<Sample> callback) {
        outlier_receipt_callbacks.add(callback);
    }

    public void unregisterOutlierReceiptCallback(Consumer<Sample> callback) {
        outlier_receipt_callbacks.remove(callback);
    }

    public void clearData() {
        outlier_buffer.get().clear();
        network_model.sample_window.clear();
    }

    public void stopAggregation() {
        network_model.shutdown();
        sample_processor.get().stopProcessing();
        outlier_receipt_callbacks.clear();
    }
}
