package edu.unh.artt.core.models;

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
import java.util.concurrent.atomic.AtomicReference;

public class KernelDensityEstimator<Sample extends TimeErrorSample> extends ErrorModel<Sample> {
    private static final Logger logger = LoggerFactory.getLogger(KernelDensityEstimator.class);

    private final Interpreter kde_wrapper;

    private final AtomicReference<double[][]> sample_array = new AtomicReference<>(new double[0][0]);
    private final AtomicReference<int[]> weight_array = new AtomicReference<>(new int[0]);

    public KernelDensityEstimator(int sampleWindow) {
        super(sampleWindow);

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
        int numD = smpls.get(0).getNumDimensions();
        double [][] samples = new double[smpls.size()][numD];
        int [] weights = new int[smpls.size()];
        int idx = 0;
        for(Sample s : smpls) {
            samples[idx] = s.getSample();
            weights[idx++] = s.getWeight();
        }

        try {
            kde_wrapper.set("weights", weights);
            kde_wrapper.set("samples", samples);
            kde_wrapper.exec("weights = np.atleast_1d(weights)");
            kde_wrapper.exec("samples = np.atleast_2d(weights)");

            kde_wrapper.exec("pdf = stats.gaussian_kde(samples, weights=weights)");
        } catch(JepException jpe) {
            logger.error("Failed to transfer shared memory", jpe);
            throw new IllegalStateException();
        }
    }

    @Override
    LinkedList<Sample> resample(int newWindow) {
        return null;
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
            KernelDensityEstimator<OffsetGmSample> estimator = new KernelDensityEstimator<>(Integer.valueOf(args[0]));
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
