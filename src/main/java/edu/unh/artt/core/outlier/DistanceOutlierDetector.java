package edu.unh.artt.core.outlier;

import edu.unh.artt.core.error_sample.representation.TimeErrorSample;
import edu.unh.artt.core.models.ErrorModel;
import smile.math.distance.EuclideanDistance;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.IntStream;

public class DistanceOutlierDetector<Sample extends TimeErrorSample> extends OutlierDetector<Sample> {
    private final long neighbor_threshold;
    private final double [] range_distances, increment_amount;
    private final ErrorModel<Sample> reference_model;

    public DistanceOutlierDetector(ErrorModel<Sample> refModel, double [] rangeDist, double [] incAmt, long neighborThreshold) {
        super(refModel);

        if(rangeDist.length != incAmt.length)
            throw new IllegalArgumentException("Dimensionality of the range distances and increment amount arguments must match.");

        if(Arrays.stream(rangeDist).anyMatch(d->d <= 0))
            throw new IllegalArgumentException("Range distances must be greater than 0.");

        range_distances = rangeDist;
        neighbor_threshold = neighborThreshold;
        reference_model = refModel;
        increment_amount = incAmt;
    }

    @Override
    public boolean isOutlier(Sample sample) {
        double [] smp = sample.getSample();

        if(smp.length != range_distances.length) {
            throw new IllegalArgumentException("Dimensionality of the input sample must match the dimensionality " +
                    "of the outlier detector.");
        }

        double [][] range = new double[2][smp.length];
        for(int m = 0; m < 2; m++) {
            range[m] = new double[smp.length];
            for (int i = 0; i < smp.length; i++) {
                double dist = range_distances[i] * ((m == 0) ? -1 : 1);
                range[m][i] = smp[i] + dist;
            }
        }

        List<double[]> testSamples = new LinkedList<>();
        var euclid_util = new EuclideanDistance(); //Not sure why this isn't a static class, but it works
        do {
            testSamples.add(Arrays.copyOf(range[0], range[0].length));
            for(int i = 0; i < range[0].length; i++)
                range[0][i] += increment_amount[i];
        } while(euclid_util.d(range[0], range[1]) > 0);


        //TODO Convert sample data to TimeErrorSamples for model estimation

        return false;
    }
}
