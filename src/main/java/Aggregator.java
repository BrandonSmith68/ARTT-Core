import error_sample.processing.OffsetSampleProcessor;
import error_sample.processing.SampleProcessor;
import error_sample.representation.TimeErrorSample;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicReference;

public class Aggregator {
    final static Logger logger = LoggerFactory.getLogger(Aggregator.class);

    private final AtomicReference<SampleProcessor> sample_processor = new AtomicReference<>();

    public Aggregator() {
        this(new OffsetSampleProcessor());
    }

    public Aggregator(SampleProcessor processor) {
        setSampleProcessor(processor);
    }

    public SampleProcessor getSampleProcessor() {
        return sample_processor.get();
    }

    public void setSampleProcessor(SampleProcessor proc) {
        logger.info("New sync messages are now being processed using a " + proc);
        proc.registerErrorComputeAction(this::processErrorSample);
        sample_processor.set(proc);
    }

    private void processErrorSample(TimeErrorSample sample) {

    }
}
