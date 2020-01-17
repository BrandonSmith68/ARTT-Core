package edu.unh.artt.core.models;

import edu.unh.artt.core.error_sample.representation.AMTLVData;
import edu.unh.artt.core.error_sample.representation.OffsetGmSample;
import edu.unh.artt.core.error_sample.representation.TimeErrorSample;
import jep.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import smile.plot.Histogram;
import smile.plot.PlotCanvas;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.FileWriter;
import java.util.LinkedList;
import java.util.Random;

public class WeightedKernelDensityEstimator<Sample extends TimeErrorSample> extends ErrorModel<Sample> {
    private static final Logger logger = LoggerFactory.getLogger(WeightedKernelDensityEstimator.class);

    private final Interpreter kde_wrapper;

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

    @Override
    public boolean shouldResample(AMTLVData<Sample> lastSent) {
        return super.shouldResample(lastSent);
        //TODO Compare the distributions
    }

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

    @Override
    public double estimate(Sample point) {
        try {
            kde_wrapper.set("tmp", point.getSample());
            kde_wrapper.exec("res = pdf(np.atleast_2d(tmp))");
            return ((double[])((NDArray) kde_wrapper.getValue("res")).getData())[0];
        } catch (JepException jpe) {
            logger.error("Failed to estimate point", jpe);
        }
        return 0.0;
    }

    @Override
    public void shutdown() {
        try {
            kde_wrapper.close();
        } catch (JepException jpe) {
            logger.error("Failed to shutdown python interpreter", jpe);
        }
    }

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
            WeightedKernelDensityEstimator<OffsetGmSample> estimator = new WeightedKernelDensityEstimator<>(Integer.valueOf(args[0]), 2);
            Random r = new Random();
            double mean = Double.valueOf(args[1]);
            double variance =  Double.valueOf(args[2]);
            int sampleCount = Integer.valueOf(args[3]);
            int modes = Integer.valueOf(args[4]);
            int modeDist = Integer.valueOf(args[5]);
            long min = Long.MAX_VALUE, max = Long.MIN_VALUE;
            samples = new double[modes*sampleCount];

            for(int i = 0; i < sampleCount*modes; i+=modes) {
                for(int m = 0; m < modes; m++) {
                    long sample = Math.round(mean + (modeDist * m) + r.nextGaussian() * variance);
                    samples[i + m] = sample;
                    estimator.addSample(new OffsetGmSample((short) ((m + 1)), sample, new byte[8]));
                    csvWriter.write(sample + "," + (m + 1) + "\n");
                    min = (sample < min) ? sample : min;
                    max = (sample > max) ? sample : max;
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
