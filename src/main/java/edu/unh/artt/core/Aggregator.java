package edu.unh.artt.core;

import edu.unh.artt.core.error_sample.processing.SampleFactory;
import edu.unh.artt.core.error_sample.processing.SampleProcessor;
import edu.unh.artt.core.error_sample.representation.AMTLVData;
import edu.unh.artt.core.error_sample.representation.TimeErrorSample;
import edu.unh.artt.core.models.ErrorModel;
import edu.unh.artt.core.outlier.OutlierDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

//Todo comments once the error model structure is set
public class Aggregator<Sample extends TimeErrorSample> {
    final static Logger logger = LoggerFactory.getLogger(Aggregator.class);

    private final AtomicReference<SampleProcessor<Sample>> sample_processor = new AtomicReference<>();

    private final ErrorModel<Sample> local_model;
    private final ErrorModel<Sample> subnetwork_model;

    private final OutlierDetector<Sample> local_outlier_detector;
    private final OutlierDetector<Sample> subnetwork_outlier_detector;

    private final ArrayList<Sample> outlier_buffer;

    public Aggregator(SampleProcessor<Sample> processor, ErrorModel<Sample> baselineModel,
                      Class<OutlierDetector<Sample>> detectorType, int numPorts) {
        local_model = baselineModel;
        subnetwork_model = local_model.duplicate();

        int windowSize = subnetwork_model.getWindowSize()*numPorts;
        subnetwork_model.modifyWindowSize(windowSize);
        outlier_buffer = new ArrayList<>(windowSize);

        try {
            local_outlier_detector = detectorType.getDeclaredConstructor(ErrorModel.class).newInstance(local_model);
            subnetwork_outlier_detector = detectorType.getDeclaredConstructor(ErrorModel.class).newInstance(subnetwork_model);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to instantiate outlier detectors.", e);
        }
        setSampleProcessor(processor);
    }

    public SampleProcessor getSampleProcessor() {
        return sample_processor.get();
    }

    public void setSampleProcessor(SampleProcessor<Sample> proc) {
        logger.info("New sync messages are now being processed using a " + proc);
        proc.registerErrorComputeAction((sample -> {
            local_model.addSample(sample);
            subnetwork_model.addSample(sample);

            if(local_outlier_detector.isOutlier(sample) && subnetwork_outlier_detector.isOutlier(sample))
                outlier_buffer.add(sample);
        }));
        proc.onAMTLVReceipt((amtlv) -> {
            amtlv.subnetwork_samples.forEach(subnetwork_model::addSample);
            amtlv.subnetwork_outliers.stream().filter(subnetwork_outlier_detector::isOutlier).forEach(outlier_buffer::add);
        });
        sample_processor.set(proc);
    }

    public AMTLVData<Sample> retrieveNewData() {
        //Todo clear outlier buffer, get most recent samples
        return new AMTLVData<>(List.of(), List.of());
    }
}
