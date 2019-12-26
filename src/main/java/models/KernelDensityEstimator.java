package models;

import error_sample.representation.TimeErrorSample;
import models.bandwidth.BandwidthCalculator;
import models.bandwidth.ScottBandwidthCalculator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

public class KernelDensityEstimator<Sample extends TimeErrorSample> extends ErrorModel<Sample> {
    private final Logger logger = LoggerFactory.getLogger(KernelDensityEstimator.class);

    private volatile BandwidthCalculator bw_calculator = new ScottBandwidthCalculator(5);
    private final AtomicReference<double[]> current_bandwidth = new AtomicReference<>();

    public KernelDensityEstimator(int sampleWindow) {
        super(sampleWindow);
    }

    public void setBandwidthCalculator(BandwidthCalculator bw) {
        bw_calculator = bw;
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
        //Todo make this a rolling average / std dev
        int numD = samples.peek().getNumDimensions();
        int sampleSize = getWindowSize();

        double [] averages = new double[numD];
        double [] stdDevs = new double[numD];
        for(int i = 0; i < numD; i++) {
            final int idxBuf = i;
            double [] dimen = samples.stream().map(Sample::getSample).mapToDouble(v->v[idxBuf]).toArray();
            averages[idxBuf] = Arrays.stream(dimen).summaryStatistics().getAverage();
            double sumOSq = Arrays.stream(dimen).map(d -> Math.pow(d - averages[idxBuf], 2)).sum();
            stdDevs[idxBuf] = Math.sqrt(sumOSq / sampleSize);
        }

        current_bandwidth.set(bw_calculator.computeBandwidth(stdDevs, sampleSize));
    }

    @Override
    public double estimate(Sample point) {
        double [] data = point.getSample();
        int numDim = data.length;
        double [] bndws = current_bandwidth.get();

        if(bndws == null) {
            logger.error("Attempted to compute estimate without having computed the kernel bandwidths.");
            return 0;
        }

        double coeff = Math.pow(0.75, numDim);
        double invSum = 1 / Arrays.stream(bndws).sum();
        double prod = IntStream.range(0, point.getNumDimensions())
                                .mapToDouble(i -> 1 - Math.pow(data[i]/bndws[i], 2)).reduce((d1, d2) -> d1*d2).orElseGet(()->1);

        return coeff * invSum * prod;
    }
}
