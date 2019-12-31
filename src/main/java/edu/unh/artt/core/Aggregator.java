package edu.unh.artt.core;

import edu.unh.artt.core.error_sample.processing.SampleFactory;
import edu.unh.artt.core.error_sample.processing.SampleProcessor;
import edu.unh.artt.core.error_sample.representation.TimeErrorSample;
import edu.unh.artt.core.models.ErrorModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicReference;

//Todo comments once the error model structure is set
public class Aggregator<Sample extends TimeErrorSample> {
    final static Logger logger = LoggerFactory.getLogger(Aggregator.class);

    private final AtomicReference<SampleProcessor<Sample>> sample_processor = new AtomicReference<>();

    private final ErrorModel<Sample> local_model;
    private final ErrorModel<Sample> subnetwork_model;

    public Aggregator(SampleProcessor<Sample> processor, ErrorModel<Sample> baselineModel, int numPorts) {
        local_model = baselineModel;
        subnetwork_model = local_model.duplicate();
        subnetwork_model.modifyWindowSize(subnetwork_model.getWindowSize()*numPorts);
        setSampleProcessor(processor);
    }

    public SampleProcessor getSampleProcessor() {
        return sample_processor.get();
    }

    public void setSampleProcessor(SampleProcessor<Sample> proc) {
        logger.info("New sync messages are now being processed using a " + proc);
        proc.registerErrorComputeAction(local_model::addSample);
        proc.onAMTLVReceipt((amtlv) -> amtlv.subnetwork_outliers.forEach(subnetwork_model::addSample));
        //todo outlier processing
        sample_processor.set(proc);
    }
}
