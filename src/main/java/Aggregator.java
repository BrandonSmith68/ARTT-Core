import error_sample.processing.OffsetSampleProcessor;
import error_sample.processing.SampleProcessor;
import error_sample.representation.TimeErrorSample;
import models.ErrorModel;
import models.gaussian.MultivariateGaussianModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public class Aggregator {
    final static Logger logger = LoggerFactory.getLogger(Aggregator.class);

    private final AtomicReference<SampleProcessor> sample_processor = new AtomicReference<>();

    private final ErrorModel local_model;
    private final ErrorModel subnetwork_model;

    public Aggregator() {
        this(new OffsetSampleProcessor(), new MultivariateGaussianModel());
    }

    public Aggregator(ErrorModel baselineModel) {
        this(new OffsetSampleProcessor(), baselineModel);
    }

    public Aggregator(SampleProcessor processor, ErrorModel baselineModel) {
        setSampleProcessor(processor);
        local_model = baselineModel;
        subnetwork_model = local_model.duplicate();
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
