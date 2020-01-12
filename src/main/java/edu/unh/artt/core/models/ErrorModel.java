package edu.unh.artt.core.models;

import edu.unh.artt.core.error_sample.representation.TimeErrorSample;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicBoolean;


public abstract class ErrorModel<Sample extends TimeErrorSample> {
    private final Logger logger = LoggerFactory.getLogger(ErrorModel.class);

    //LinkedList for a simple queue. Always need to access all elements anyways
    protected LinkedList<Sample> sample_window;
    protected int sample_size;

    public ErrorModel(int sampleWindow) {
        sample_window = new LinkedList<>();
        sample_size = sampleWindow;
    }

    public abstract void shutdown();

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

    public final void modifyWindowSize(int newWindow) {
        if(newWindow > 0)
            sample_size = newWindow;
        else
            logger.error("Attempted to set a window size of {}, which is not greater than 0.", newWindow);
    }

    protected abstract void computeMetrics(LinkedList<Sample> sampleIterator);

    abstract LinkedList<Sample> resample(int newWindow);

    public abstract double estimate(Sample point);
}
