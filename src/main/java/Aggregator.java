import error_sample.processing.OffsetSampleProcessor;
import error_sample.processing.SampleFactory;
import error_sample.processing.SampleProcessor;
import error_sample.representation.OffsetGmSample;
import error_sample.representation.TimeErrorSample;
import models.ErrorModel;
import models.KernelDensityEstimator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
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
        proc.onAMTLVReceipt((Byte id, byte [] amtlvData) ->
            SampleFactory.<Sample>processAMTLVData(id, amtlvData).forEach(subnetwork_model::addSample));
        sample_processor.set(proc);
    }
}
