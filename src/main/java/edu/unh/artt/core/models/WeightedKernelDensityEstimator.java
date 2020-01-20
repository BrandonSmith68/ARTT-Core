package edu.unh.artt.core.models;

import edu.unh.artt.core.error_sample.representation.AMTLVData;
import edu.unh.artt.core.error_sample.representation.OffsetGmSample;
import edu.unh.artt.core.error_sample.representation.TimeErrorSample;
import edu.unh.artt.core.outlier.DistanceOutlierDetector;
import jep.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import smile.plot.Histogram;
import smile.plot.PlotCanvas;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.FileWriter;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

/**
 * Models a time error distribution using a Gaussian weighted kernel density estimator. For the sake of code re-usage
 * this class uses the guassian_kde implementation in the scipy python library. This means that this class maintains a
 * python interpreter that converts the sample data into numpy NDArray instances and develops a probability density
 * function. There may be a performance implication to this, but it's likely small since the data sets usually contain
 * less than 10,000 samples.
 * @see edu.unh.artt.core.models.ErrorModel
 */
public class WeightedKernelDensityEstimator<Sample extends TimeErrorSample> extends ErrorModel<Sample> {
    private static final Logger logger = LoggerFactory.getLogger(WeightedKernelDensityEstimator.class);

    /* Python interpreter to utilize the gaussian_kde library */
    private final Interpreter kde_wrapper;

    /**
     * Initializes the python interpreter.
     * @see ErrorModel#ErrorModel(int, int)
     */
    public WeightedKernelDensityEstimator(int sampleWindow, int numDim) {
        super(sampleWindow, numDim);

        try {
            JepConfig conf = new JepConfig();
            conf.setRedirectOutputStreams(true);
            SharedInterpreter.setConfig(conf);
            kde_wrapper = new SharedInterpreter();

            //Setup libraries
            kde_wrapper.exec("import numpy as np");
            kde_wrapper.exec("from scipy import stats");
            kde_wrapper.exec("from scipy.spatial.distance import cdist");
            logger.info("Got here");
        } catch (JepException jpe) {
            logger.error("Failed to initialize python interpreter.");
            throw new IllegalStateException(jpe);
        }
    }

    /**
     * Computes a new probability density function using the gaussian_kde library.
     * @see ErrorModel#computeMetrics(LinkedList)
     */
    @Override
    protected void computeMetrics(LinkedList<Sample> smpls) {
        double [][] samples = new double[num_dimensions][smpls.size()];
        long [] weights = new long[smpls.size()];
        int idx = 0;
        for(Sample s : smpls) {
            for(int dim = 0; dim < num_dimensions; dim++)
                samples[dim][idx] = s.getSample()[dim];
            weights[idx++] = s.getWeight();
        }

        try {
            kde_wrapper.set("weights", weights);
            kde_wrapper.set("samples", samples);
            kde_wrapper.exec("weights = np.atleast_1d(weights)");
            kde_wrapper.exec("samples = np.atleast_2d(samples)");

            kde_wrapper.exec("pdf = stats.gaussian_kde(samples)");
        } catch(JepException jpe) {
            logger.error("Failed to transfer shared memory", jpe);
            throw new IllegalStateException();
        }
    }

    /**
     * @see ErrorModel#shouldResample(AMTLVData)
     */
    @Override
    public boolean shouldResample(AMTLVData<Sample> lastSent) {
        return super.shouldResample(lastSent);
        //TODO Compare the distributions
    }

    /**
     * Generates a new data set with the same shape as the input data. The pdf is used to generate a new sample set that
     * matches the computed error distribution.
     * @param newWindow Size of the generated data set
     * @return A representative sample data set
     */
    @Override
    protected double[][] resampleImpl(int newWindow) {
        try {
            if(kde_wrapper.getValue("pdf") != null) {
                kde_wrapper.exec("newsamples = pdf.resample(size= " + newWindow + ")");
                double[] newSamples = ((NDArray<double[]>) kde_wrapper.getValue("newsamples")).getData();
                if(newSamples.length != newWindow*num_dimensions)
                    throw new IllegalStateException("Attempted to resample previously computed KDE, but observed a " +
                            "mismatch in dimensionality.");

                double [][] split = new double[newWindow][num_dimensions];
                for(int i = 0; i < newSamples.length; i++)
                    split[i%newWindow][i/newWindow] = newSamples[i];
                return split;
            }
        } catch(JepException | ClassCastException jpe) {
            logger.error("Failed to transfer shared memory", jpe);
            throw new IllegalStateException();
        }
        return new double[0][];
    }

    /**
     * Uses the computed pdf to provide the likelihood of the given sample.
     * @see ErrorModel#estimate(TimeErrorSample)
     */
    @Override
    @SuppressWarnings("unchecked") //Want the array for 1->1 sample mapping to probabilities
    public double estimate(Sample point) {
        Sample [] smp = (Sample[])Array.newInstance(point.getClass(), 1);
        smp[0] = point;
        return this.estimate(smp)[0];
    }

    /**
     * Uses the computed pdf to provide the likelihood of each sample in the given range.
     * @see ErrorModel#estimate(TimeErrorSample[])
     */
    @Override
    public double [] estimate(Sample[] pointWindow) {
        double [][] samples = new double[num_dimensions][pointWindow.length];
        for(int i = 0; i < pointWindow.length; i++) {
            for(int dim = 0; dim < num_dimensions; dim++)
                samples[dim][i] = pointWindow[i].getSample()[dim];
        }
        try {
            kde_wrapper.set("tmp", samples);
            kde_wrapper.exec("res = pdf(np.atleast_2d(tmp))");
            return ((double[])((NDArray) kde_wrapper.getValue("res")).getData());
        } catch (JepException jpe) {
            logger.error("Failed to estimate point", jpe);
        }
        return new double[1];
    }

    /**
     * Disables the python interpreter
     */
    @Override
    public void shutdown() {
        try {
            kde_wrapper.close();
        } catch (JepException jpe) {
            logger.error("Failed to shutdown python interpreter", jpe);
        }
    }

    /**
     * Simple utility to test out the weighted kernel density estimator and provide visual plots. Data sets following an
     * 'm' modal gaussian distribution are generated and then added to the weighted density estimator. The weight of
     * a sample data point is simply the mode it's 'part of' (for the sake of visualizing the weights). The generated
     * plot compares the actual data with the computed pdf. The true data is also logged to a .csv file.
     */
    public static void main(String[] args) throws Exception {
        String usage = "Usage: <window size> <mean> <variance> <num_samples> <num_modes> <mode distance>";
        if(args.length != 6) {
            logger.error(usage);
            return;
        }

        double [] samples;
        double[][] distData;
        File csv = new File("test.csv");
        FileWriter csvWriter = new FileWriter(csv);
        try {
            WeightedKernelDensityEstimator<OffsetGmSample> estimator = new WeightedKernelDensityEstimator<>(Integer.parseInt(args[0]), 1);
            Random r = new Random();
            double mean = Double.parseDouble(args[1]);
            double variance =  Double.parseDouble(args[2]);
            int sampleCount = Integer.parseInt(args[3]);
            int modes = Integer.parseInt(args[4]);
            int modeDist = Integer.parseInt(args[5]);
            long min = Long.MAX_VALUE, max = Long.MIN_VALUE;
            samples = new double[modes*sampleCount];

            for(int i = 0; i < sampleCount*modes; i+=modes) {
                for(int m = 0; m < modes; m++) {
                    long sample = Math.round(mean + (modeDist * m) + r.nextGaussian() * variance);
                    samples[i + m] = sample;
                    estimator.addSample(new OffsetGmSample((short) ((m + 1)), sample, new byte[8]));
                    csvWriter.write(sample + "," + (m + 1) + "\n");
                    min = Math.min(min, sample);
                    max = Math.max(max, sample);
                }
            }
            csvWriter.close();

            DistanceOutlierDetector<OffsetGmSample> outlierDetector = new DistanceOutlierDetector<>(estimator, new double[]{5.}, new double[]{1.}, 10);
            int range = (int)(max - min) + ((int)variance*4);
            distData = new double[range][2];//XY coords
            for(int i = 0; i < range; i++) {
                long xCoord = i+min-((int)variance*2);
                distData[i][0] = xCoord;
                distData[i][1] = estimator.estimate(new OffsetGmSample((short)1, xCoord, new byte[8]));
                if(outlierDetector.isOutlier(new OffsetGmSample((short)1, xCoord, new byte[8])))
                    logger.info(xCoord+"");
            }
            if(outlierDetector.isOutlier(new OffsetGmSample((short)1, 1000, new byte[8])))
                logger.info(1000+"");
            estimator.resample(500);
            estimator.shutdown();
        } catch(NumberFormatException nfe) {
            logger.error("Invalid input: {}", nfe.getMessage());
            logger.error(usage);
            return;
        }

        JPanel panel = new JPanel(new GridLayout(1,2));
        PlotCanvas plot = Histogram.plot(samples);
        panel.add(plot);

        plot.line(distData);

        JFrame frame = new JFrame("Line Plot");
        frame.setSize(600, 600);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);
        frame.getContentPane().add(panel);
        frame.setVisible(true);

//        SwingUtilities.invokeLater(()-> {
//            try {
//                Thread.sleep(1000);
//                plot.save(new File("test.png"));
//            } catch(Exception e) {
//                logger.error("", e);
//            }
//        });
    }
}
