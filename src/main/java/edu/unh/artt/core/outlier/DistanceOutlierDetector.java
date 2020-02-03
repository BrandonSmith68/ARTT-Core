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
        //TODO The effectiveness of removing this range needs to be tested, but just using the likelihood of the sample should be sufficient
//        double [] stdevs = reference_model.getStandardDeviation();
//        double [][] range = new double[2][smp.length]; //Index 0 is min, 1 is max
//        for(int m = 0; m < 2; m++) {
//            range[m] = new double[smp.length];
//            for (int i = 0; i < smp.length; i++) {
//                //If the standard deviation is very large the number of samples to estimate grows rapidly and will
//                //subsequently pin down 1 or more processors and render the mechanism useless. In the worst case just
//                //use a percentage of the local window size.
//                double rng = (stdevs[i] > reference_model.getLocalWindowSize()) ? reference_model.getLocalWindowSize() * 0.25 : stdevs[i];
//                double dist = rng * ((m == 0) ? -1 : 1); //+/- the standard deviation
//                range[m][i] = smp[i] + dist;
//            }
//        }

        //Generate a set of points in the state space to integrate over using the base unit vector
        List<double[]> testSamples = List.of(sample.getSample());
//        ErrorModel.fillMultiDim(0, Arrays.copyOf(range[0], range[0].length), range, testSamples, increment_amount);

        //Generate the likelihoods of each generated sample
        double[] probs = reference_model.estimate(
                sample.parseSamples(testSamples).toArray((Sample[]) Array.newInstance(sample.getClass(), 1))
        );
        return Arrays.stream(probs).sum() < likelihood_threshold; //Find the cumulative probability
    }
}
