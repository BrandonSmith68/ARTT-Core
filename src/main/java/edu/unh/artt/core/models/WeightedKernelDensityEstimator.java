package edu.unh.artt.core.models;

import edu.unh.artt.core.error_sample.representation.AMTLVData;
import edu.unh.artt.core.error_sample.representation.OffsetGmSample;
import edu.unh.artt.core.error_sample.representation.TimeErrorSample;
import jep.*;
import org.apache.commons.math3.stat.descriptive.moment.Mean;
import org.apache.commons.math3.stat.descriptive.moment.Variance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import smile.plot.Histogram;
import smile.plot.PlotCanvas;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.FileWriter;
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

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
    private final static AtomicReference<Interpreter> kde_wrapper = new AtomicReference<>();
    private final static ExecutorService python_executor = Executors.newFixedThreadPool(1);

    /* Used to keep track of the statistics most recently computed */
    private final double[] averages, variances;

    private final String weightVar = "weights" + getUniqueID(),
                         sampleVar = "samples" + getUniqueID(),
                         pdfVar = "pdf" + getUniqueID(),
                         resampleVar = "newSamples" + getUniqueID();

    private static void getInterpreterAccess(int timeout, TimeUnit unit, Consumer<Interpreter> pythonInterpreter) {
        CountDownLatch latch = new CountDownLatch(1);
        python_executor.execute(() -> {
            try {
                if (kde_wrapper.get() == null) { //Runs on a single thread, no race conditions for setting kde_wrapper.
                    JepConfig conf = new JepConfig();
                    conf.setRedirectOutputStreams(true);
                    SharedInterpreter.setConfig(conf);
                    kde_wrapper.set(new SharedInterpreter());

                    Interpreter wrapper = kde_wrapper.get();
                    //Setup libraries
                    wrapper.exec("import numpy as np");
                    wrapper.exec("from scipy import stats");
                    wrapper.exec("from scipy.spatial.distance import cdist");
                }
                pythonInterpreter.accept(kde_wrapper.get());
            } catch (JepException jep) {
                logger.error("Failed to enable access to python interpreter.", jep);
                throw new IllegalStateException(jep);
            } finally {
                latch.countDown();
            }
        });
        try {
            if (!latch.await(timeout, unit))
                throw new IllegalStateException("Failed to provide interpreter access: timed out while waiting for " +
                        "commands to complete.");
        } catch (InterruptedException ie) {
            throw new IllegalStateException(ie);
        }
    }

    private final int getUniqueID() {
        return System.identityHashCode(this);
    }

    /**
     * Initializes the python interpreter.
     * @see ErrorModel#ErrorModel(int, int)
     */
    public WeightedKernelDensityEstimator(int sampleWindow, int numDim) {
        super(sampleWindow, numDim);

        averages = new double[numDim];
        variances = new double[numDim];
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

        //Transpose the sample array so the major index is the dimension
        for(Sample s : smpls) {
            for(int dim = 0; dim < num_dimensions; dim++)
                samples[dim][idx] = s.getSample()[dim];
            weights[idx++] = s.getWeight();
        }

        synchronized (averages) {
            for (int i = 0; i < num_dimensions; i++) {
                averages[i] =  new Mean().evaluate(samples[i]);
                variances[i] = new Variance().evaluate(samples[i]);
            }
        }

        getInterpreterAccess(1, TimeUnit.HOURS, (wrapper) -> {
            try {
                wrapper.set(weightVar, weights);
                wrapper.set(sampleVar, samples);
                wrapper.exec(weightVar + " = np.atleast_1d(" + weightVar + ")");
                wrapper.exec(sampleVar + " = np.atleast_2d(" + sampleVar + ")");

                wrapper.exec(pdfVar + " = stats.gaussian_kde(" + sampleVar + ")");
            } catch(JepException jpe) {
                logger.error("Failed to transfer shared memory", jpe);
                throw new IllegalStateException(jpe);
            }
        });
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
        AtomicReference<double[]> newSamples = new AtomicReference<>();
        AtomicBoolean sawPDF = new AtomicBoolean(false);
        getInterpreterAccess(1, TimeUnit.HOURS, (wrapper) -> {
            try {
                sawPDF.set(wrapper.getValue(pdfVar) != null);
                if(sawPDF.get()) {
                    wrapper.exec(resampleVar + " = " + pdfVar + ".resample(size= " + newWindow + ")");
                    newSamples.set(((NDArray<double[]>) wrapper.getValue(resampleVar)).getData());
                }
            } catch(JepException | ClassCastException jpe) {
                logger.error("Failed to transfer shared memory", jpe);
                throw new IllegalStateException();
            }
        });

        if(sawPDF.get()) {
            double[] newSmps = newSamples.get();
            if (newSmps.length != newWindow * num_dimensions)
                throw new IllegalStateException("Attempted to resample previously computed KDE, but observed a " +
                        "mismatch in dimensionality.");

            double[][] split = new double[newWindow][num_dimensions];
            for (int i = 0; i < newSmps.length; i++)
                split[i % newWindow][i / newWindow] = newSmps[i];
            return split;
        }

        return new double[0][];
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

        AtomicReference<double[]> estimate = new AtomicReference<>(new double[1]);
        getInterpreterAccess(1, TimeUnit.HOURS, wrapper -> {
            try {
                //The tmp and res variables can be shared between threads
                wrapper.set("tmp", samples);
                wrapper.exec("res = " + pdfVar + "(np.atleast_2d(tmp))");
                estimate.set((double[])((NDArray) wrapper.getValue("res")).getData());
            } catch (JepException jpe) {
                logger.error("Failed to estimate point", jpe);
            }
        });

        return estimate.get();
    }

    @Override
    public double[] getMean() {
        synchronized (averages) {
            return Arrays.copyOf(averages, averages.length);
        }
    }

    @Override
    public double[] getVariance() {
        synchronized (averages) {
            return Arrays.copyOf(variances, variances.length);
        }
    }

    @Override
    public double[] getStandardDeviation() {
        double [] stdevs = getVariance();
        for(int i = 0; i < stdevs.length; i++)
            stdevs[i] = Math.sqrt(stdevs[i]);
        return stdevs;
    }

    /**
     * Disables the python interpreter
     */
    @Override
    public void shutdown() {
        getInterpreterAccess(1,TimeUnit.MINUTES, interpreter -> {
            try {
                interpreter.exec(pdfVar + " = None");
                interpreter.exec(sampleVar + " = None");
                interpreter.exec(resampleVar + " = None");
                interpreter.exec(weightVar + " = None");
            } catch (JepException jpe) {
                logger.error("Failed to shutdown python interpreter", jpe);
            }
        });
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

            int range = (int)(max - min) + ((int)variance*4);
            distData = new double[range][2];//XY coords
            for(int i = 0; i < range; i++) {
                long xCoord = i+min-((int)variance*2);
                distData[i][0] = xCoord;
                distData[i][1] = estimator.estimate(new OffsetGmSample((short)1, xCoord, new byte[8]));
            }
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
