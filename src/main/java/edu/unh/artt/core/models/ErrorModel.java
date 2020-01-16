package edu.unh.artt.core.models;

import edu.unh.artt.core.error_sample.representation.AMTLVData;
import edu.unh.artt.core.error_sample.representation.TimeErrorSample;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;


public abstract class ErrorModel<Sample extends TimeErrorSample> {
    private final Logger logger = LoggerFactory.getLogger(ErrorModel.class);

    //LinkedList for a simple queue. Always need to access all elements anyways
    protected LinkedList<Sample> sample_window;
    protected int sample_size;
    protected final int num_dimensions;

    protected final AtomicInteger samples_since_last_sent = new AtomicInteger(0);
    protected final AtomicBoolean windowFlag = new AtomicBoolean(false);
    protected final AtomicBoolean resampleFlag = new AtomicBoolean(false);

    public ErrorModel(int sampleWindow, int numDim) {
        sample_window = new LinkedList<>();
        sample_size = sampleWindow;
        num_dimensions = numDim;
    }

    public abstract void shutdown();

    public final void addSample(Sample sample) {
        sample_window.addFirst(sample);
        samples_since_last_sent.incrementAndGet();

        if(sample_window.size() > sample_size)
            sample_window.removeLast();

        if(sample_window.size() == sample_size) {
            if(!windowFlag.compareAndExchange(false, true))
                logger.info("Reached moving sample window size ({}). Model estimation has started.", sample_size);

            if(samples_since_last_sent.get() == sample_size)
                resampleFlag.set(true);

            computeMetrics(sample_window);
        }
    }

    public final int getLocalWindowSize() { return sample_size; }

    public final void modifyWindowSize(int newWindow) {
        if(newWindow > 0)
            sample_size = newWindow;
        else
            logger.error("Attempted to set a window size of {}, which is not greater than 0.", newWindow);
    }

    protected abstract void computeMetrics(LinkedList<Sample> sampleIterator);

    public final double[][] resample(int newWindow) {
        resampleFlag.set(false);
        return resampleImpl(newWindow);
    }
    protected abstract double[][] resampleImpl(int newWindow);

    public boolean shouldResample(AMTLVData<Sample> lastSent) {
        return resampleFlag.get();
    }

    public abstract double estimate(Sample point);
}
