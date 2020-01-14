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
import java.util.concurrent.atomic.AtomicReference;

//Todo comments once the error model structure is set
public class Aggregator<Sample extends TimeErrorSample> {
    final static Logger logger = LoggerFactory.getLogger(Aggregator.class);

    private final AtomicReference<SampleProcessor<Sample>> sample_processor = new AtomicReference<>();

    private final ErrorModel<Sample> network_model;

    private final OutlierDetector<Sample> local_outlier_detector;
    private final OutlierDetector<Sample> subnetwork_outlier_detector;

    private final ArrayList<Sample> outlier_buffer;

    public Aggregator(SampleProcessor<Sample> processor, ErrorModel<Sample> baselineModel,
                      OutlierDetector<Sample> localDetector, OutlierDetector<Sample> subnetworkDetector, int numPorts) {
        network_model = baselineModel;

        int windowSize = network_model.getWindowSize()*numPorts;
        network_model.modifyWindowSize(windowSize);
        outlier_buffer = new ArrayList<>(windowSize);

        local_outlier_detector = localDetector;
        subnetwork_outlier_detector = subnetworkDetector;
        setSampleProcessor(processor);
    }

    public SampleProcessor<Sample> getSampleProcessor() {
        return sample_processor.get();
    }

    public void setSampleProcessor(SampleProcessor<Sample> proc) {
        logger.info("New sync messages are now being processed using a " + proc);
        proc.registerErrorComputeAction((sample -> {
            network_model.addSample(sample);

            if(local_outlier_detector.isOutlier(sample) && subnetwork_outlier_detector.isOutlier(sample))
                outlier_buffer.add(sample);
        }));
        proc.onAMTLVReceipt((amtlv) -> {
            amtlv.subnetwork_samples.forEach(network_model::addSample);
            amtlv.subnetwork_outliers.stream().filter(subnetwork_outlier_detector::isOutlier).forEach(outlier_buffer::add);
        });
        sample_processor.set(proc);
    }

    public AMTLVData<Sample> retrieveNewData() {
        //Todo clear outlier buffer, get most recent samples
        return new AMTLVData<>(List.of(), List.of());
    }

    public static void main(String [] args) {
//        logger.info();
    }
}
