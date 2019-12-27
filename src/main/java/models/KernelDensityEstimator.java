package models;

import error_sample.representation.TimeErrorSample;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import smile.stat.distribution.KernelDensity;

import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicReference;

public class KernelDensityEstimator<Sample extends TimeErrorSample> extends ErrorModel<Sample> {
    private final Logger logger = LoggerFactory.getLogger(KernelDensityEstimator.class);

    private final AtomicReference<KernelDensity> most_recent_estimator = new AtomicReference<>(null);

    public KernelDensityEstimator(int sampleWindow) {
        super(sampleWindow);
    }

    @Override
    public void processAMTLV(byte[] amtlv) {

    }

    @Override
    public ErrorModel duplicate() {
        return null;
    }

    @Override
    public void merge(ErrorModel model) {

    }

    @Override
    protected void computeMetrics(LinkedList<Sample> samples) {
        //Todo The current solution for this is to simply utilize the SMILE library to compute a new kernel estimate
        // in the future this should be expanded to utilize multiple kernel types, different bandwidths, and rolling
        // calculations of each kernel
//        int numD = samples.peek().getNumDimensions();
//        int sampleSize = getWindowSize();
//
//        double [] averages = new double[numD];
//        double [] stdDevs = new double[numD];
//        for(int i = 0; i < numD; i++) {
//            final int idxBuf = i;
//            double [] dimen = samples.stream().map(Sample::getSample).mapToDouble(v->v[idxBuf]).toArray();
//            averages[idxBuf] = Arrays.stream(dimen).summaryStatistics().getAverage();
//            double sumOSq = Arrays.stream(dimen).map(d -> Math.pow(d - averages[idxBuf], 2)).sum();
//            stdDevs[idxBuf] = Math.sqrt(sumOSq / sampleSize);
//        }
        double [] smps = samples.stream().mapToDouble(s -> s.getSample()[0]).toArray();
        most_recent_estimator.set(new KernelDensity(smps));

        samples.toArray(new TimeErrorSample[0]);
    }

    @Override
    public double estimate(Sample point) {
        double [] data = point.getSample();
        int numDim = data.length;
        if(numDim > 1) {
            logger.error("Only 1-dimensional measurements are currently supported.");
            return 0;
        }

        KernelDensity estimator = most_recent_estimator.get();

        if(estimator == null) {
            logger.error("Attempted to compute estimate without having previously computed the kernel.");
            return 0;
        }

        double x = point.getSample()[0];

        return estimator.p(x);
    }
}
