package edu.unh.artt.core.models;

import edu.unh.artt.core.error_sample.representation.AMTLVData;
import edu.unh.artt.core.error_sample.representation.TimeErrorSample;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Represents the distribution of time error for a given network. Maintains a moving sample window in which old values
 * expire when new ones are added (i.e. samples that fall out of the window are discarded). This class provides a few
 * useful methods:
 * <ul>
 *     <li>resample: Samples from the computed distribution to form a sample set which can be transmitted upstream</li>
 *     <li>shouldResample: Allows for limiting the rate at which the distribution is re-sampled, limiting the amount of
 *     information put on the network</li>
 *     <li>estimate: Use the computed probability density function to provide the likelihood of a given sample</li>
 * </ul>
 * @param <Sample> Sample type to build the distribution over
 */
public abstract class ErrorModel<Sample extends TimeErrorSample> {
    private final Logger logger = LoggerFactory.getLogger(ErrorModel.class);

    /* LinkedList for a simple queue. Always need to access all elements anyways */
    protected final LinkedList<Sample> sample_window;
    /* Size of the data set maintained for the distribution (not the same as the network sample size) */
    protected volatile int sample_size;
    /* Expected number of dimensions for the input data */
    protected final int num_dimensions;

    /* Number of samples accumulated since the distribution was last re-sampled */
    protected final AtomicInteger samples_since_last_sent = new AtomicInteger(0);
    /* Indicates whether or not the distribution should be re-sampled for AMTLV transmission */
    protected final AtomicBoolean resampleFlag = new AtomicBoolean(false);

    /* Just used for logging purposes. Indicates when the required sample size has been first reached */
    protected final AtomicBoolean windowFlag = new AtomicBoolean(false);

    /**
     * @param sampleWindow Size of the window to maintain locally for the computed distribution. This should be greater
     *                     than or equal to the sample size transmitted on the network (generally the more data the better)
     * @param numDim Expected number of dimensions of the sample data.
     */
    public ErrorModel(int sampleWindow, int numDim) {
        sample_window = new LinkedList<>();
        sample_size = sampleWindow;
        num_dimensions = numDim;
    }

    /**
     * Used to disable any 3rd party libraries being used for modeling
     */
    public abstract void shutdown();

    /**
     * Adds a new sample to the dataset. If the sample window is >= the max sample size then the oldest value is pushed
     * out of the sample window. The distribution metrics are re-computed when applicable.
     * @param sample
     */
    public final void addSample(Sample sample) {
        synchronized (sample_window) { //Samples are likely only added via a single thread, but better safe than sorry
            sample_window.addFirst(sample);
            samples_since_last_sent.incrementAndGet();

            if (sample_window.size() > sample_size)
                sample_window.removeLast();

            if (num_dimensions != sample.getNumDimensions())
                throw new IllegalArgumentException("Provided sample does not match the dimensionality expected by this " +
                        "model. Cannot add it to the sample dataset.");

            if (sample_window.size() == sample_size) {
                if (!windowFlag.compareAndExchange(false, true))
                    logger.info("Reached moving sample window size ({}). Model estimation has started.", sample_size);
                resampleFlag.set(samples_since_last_sent.get() >= sample_size);

                computeMetrics(sample_window);
            }
        }
    }

    /**
     * @return The number of samples maintained for the associated distribution (i.e. size of the moving window)
     */
    public final int getLocalWindowSize() { return sample_size; }

    /**
     * Changes the size of the moving window to the given value
     * @param newWindow New sample size
     */
    public final void modifyWindowSize(int newWindow) {
        if(newWindow > 0)
            sample_size = newWindow;
        else
            logger.error("Attempted to set a window size of {}, which is not greater than 0.", newWindow);
    }

    /**
     * Computes a new pdf using the given sample window.
     * @param sampleIterator Iterator over the moving sample window.
     */
    protected abstract void computeMetrics(LinkedList<Sample> sampleIterator);

    public final double[][] resample(int newWindow) {
        resampleFlag.set(false);
        samples_since_last_sent.set(0);
        return resampleImpl(newWindow);
    }
    protected abstract double[][] resampleImpl(int newWindow);

    public boolean shouldResample(AMTLVData<Sample> lastSent) {
        return resampleFlag.get();
    }

    public abstract double estimate(Sample point);
}
