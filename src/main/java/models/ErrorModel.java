package models;

import error_sample.representation.TimeErrorSample;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicBoolean;


public abstract class ErrorModel<Sample extends TimeErrorSample> {
    private final Logger logger = LoggerFactory.getLogger(ErrorModel.class);

    //LinkedList for a simple queue. Always need to access all elements anyways
    private final LinkedList<Sample> sample_window;
    private final int sample_size;

    public ErrorModel(int sampleWindow) {
        sample_window = new LinkedList<>();
        sample_size = sampleWindow;
    }


    private final AtomicBoolean windowFlag = new AtomicBoolean(false);

    public final void addSample(Sample sample) {
        sample_window.addFirst(sample);

        if(sample_window.size() > sample_size)
            sample_window.removeLast();

        if(sample_window.size() == sample_size) {
            if(!windowFlag.compareAndExchange(false, true))
                logger.info("Reached moving sample window size ({}). Model estimation has started.", sample_size);

            computeMetrics(sample_window);
        }
    }

    public final int getWindowSize() { return sample_size; }

    protected abstract void computeMetrics(LinkedList<Sample> sampleIterator);

    public abstract void processAMTLV(byte [] amtlv);
    public abstract ErrorModel duplicate();
    public abstract void merge(ErrorModel model);

    public abstract double estimate(Sample point);
}
