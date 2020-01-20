package edu.unh.artt.core.outlier;

import edu.unh.artt.core.error_sample.representation.TimeErrorSample;
import edu.unh.artt.core.models.ErrorModel;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * A simple outlier detection mechanism that uses a the probability density function of a computed error model to
 * estimate for a given point the size of the neighborhood surrounding that point. If the number of neighboring nodes is
 * less than a specified threshold then the point is considered to be an outlier.
 *
 * The neighborhood in this case is simply the cumulative likelihood of the sample +/- the standard deviation of the
 * entire window.
 * @param <Sample> Sample type to operate on
 */
public class DistanceOutlierDetector<Sample extends TimeErrorSample> extends OutlierDetector<Sample> {
    /* User specified threshold of the cumulative likelihood */
    private final double likelihood_threshold;
    /* Base unit of the values for each dimension */
    private final double [] increment_amount;
    /* Model of the time error distribution to retrieve a pdf from */
    private final ErrorModel<Sample> reference_model;

    /**
     * @param refModel Model of the time error distribution to retrieve a pdf from
     * @param baseUnit Smallest unit of the values in each dimension. Used to integrate across the dimensions.
     * @param likelihoodThreshold Probability threshold used to determine when a sample is an outlier.
     */
    public DistanceOutlierDetector(ErrorModel<Sample> refModel, double [] baseUnit, double likelihoodThreshold) {
        super(refModel);

        if(Arrays.stream(baseUnit).anyMatch(d->d <= 0))
            throw new IllegalArgumentException("Base units must be greater than 0.");

        likelihood_threshold = likelihoodThreshold;
        reference_model = refModel;
        increment_amount = baseUnit;
    }

    /**
     * Helper method to sum over a multi-dimensional range. Starts at the lower range bound, and increments each
     * dimension by the associated value in the base unit vector specified in the constructor.
     * @param curDim Current dimension
     * @param explSpace Record of current position in the state space
     * @param range Min and max values for each dimension
     * @param testSamples Sample set to accumulate
     */
    private void fillMultiDim(int curDim, double [] explSpace, double[][] range, List<double[]> testSamples) {
        do {
            if(curDim == explSpace.length - 1)
                testSamples.add(Arrays.copyOf(explSpace, explSpace.length));
            else
                fillMultiDim(curDim + 1, explSpace, range, testSamples);
            explSpace[curDim] += increment_amount[curDim];
        } while(explSpace[curDim] <= range[1][curDim]);
    }

    /**
     * Uses the reference model to determine the size of the neighborhood for a given point. If the cumulative likelihood
     * over the range of the sample +/- the standard deviation of the data set is less than the specified threshold,
     * then the sample is considered an outlier.
     * @param sample Sample to check
     * @return Whether or not the sample is considered to be an outlier
     */
    @Override
    public boolean isOutlier(Sample sample) {
        double [] smp = sample.getSample();

        if(smp.length != increment_amount.length) {
            throw new IllegalArgumentException("Dimensionality of the input sample must match the dimensionality " +
                    "of the outlier detector.");
        }

        //Set the range of the state space to integrate over. Range is +/- the standard deviation
        double [] stdevs = reference_model.getStandardDeviation();
        double [][] range = new double[2][smp.length]; //Index 0 is min, 1 is max
        for(int m = 0; m < 2; m++) {
            range[m] = new double[smp.length];
            for (int i = 0; i < smp.length; i++) {
                double dist = stdevs[i] * ((m == 0) ? -1 : 1); //+/- the standard deviation
                range[m][i] = smp[i] + dist;
            }
        }

        //Generate a set of points in the state space to integrate over using the base unit vector
        List<double[]> testSamples = new LinkedList<>();
        fillMultiDim(0, Arrays.copyOf(range[0], range[0].length), range, testSamples);

        //Generate the likelihoods of each generated sample
        double[] probs = reference_model.estimate(
                sample.parseSamples(testSamples).toArray((Sample[]) Array.newInstance(sample.getClass(), 1))
        );
        return Arrays.stream(probs).sum() < likelihood_threshold; //Find the cumulative probability
    }
}
