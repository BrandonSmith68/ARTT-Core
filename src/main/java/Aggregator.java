import error_sample.processing.OffsetSampleProcessor;
import error_sample.processing.SampleProcessor;
import error_sample.representation.PTPTimestamp;
import error_sample.representation.SyncData;
import models.ErrorModel;
import models.gaussian.MultivariateGaussianModel;
import org.apache.commons.codec.binary.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicReference;

//Todo comments once the error model structure is set
public class Aggregator {
    final static Logger logger = LoggerFactory.getLogger(Aggregator.class);

    private final AtomicReference<SampleProcessor> sample_processor = new AtomicReference<>();

    private final ErrorModel local_model;
    private final ErrorModel subnetwork_model;

    public Aggregator() {
        this(new MultivariateGaussianModel());
    }

    public Aggregator(ErrorModel baselineModel) {
        this(new OffsetSampleProcessor(), baselineModel);
    }

    public Aggregator(SampleProcessor processor, ErrorModel baselineModel) {
        local_model = baselineModel;
        subnetwork_model = local_model.duplicate();
        setSampleProcessor(processor);
    }

    public SampleProcessor getSampleProcessor() {
        return sample_processor.get();
    }

    public void setSampleProcessor(SampleProcessor proc) {
        logger.info("New sync messages are now being processed using a " + proc);
        proc.registerErrorComputeAction(local_model::addSample);
        proc.onAMTLVReceipt(subnetwork_model::processAMTLV);
        sample_processor.set(proc);
    }
}
