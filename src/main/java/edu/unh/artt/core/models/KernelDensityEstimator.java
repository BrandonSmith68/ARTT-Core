package edu.unh.artt.core.models;

import edu.unh.artt.core.error_sample.representation.OffsetGmSample;
import edu.unh.artt.core.error_sample.representation.TimeErrorSample;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import smile.plot.Histogram;
import smile.plot.PlotCanvas;
import smile.stat.distribution.KernelDensity;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.LinkedList;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class KernelDensityEstimator<Sample extends TimeErrorSample> extends ErrorModel<Sample> {
    private static final Logger logger = LoggerFactory.getLogger(KernelDensityEstimator.class);

    private final AtomicReference<KernelDensity> most_recent_estimator = new AtomicReference<>(null);

    public KernelDensityEstimator(int sampleWindow) {
        super(sampleWindow);
    }

    @Override
    LinkedList<Sample> resample(LinkedList<Sample> s1, LinkedList<Sample> s2) {
        //Todo eventually combine standard deviations and resample distributions
        return Stream.concat(s1.stream(), s2.stream()).collect(Collectors.toCollection(LinkedList::new));
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

    public static void main(String[] args) throws Exception {
        String usage = "Usage: <window size> <mean> <variance> <num_samples> <num_modes> <mode distance>";
        if(args.length != 6) {
            logger.error(usage);
            return;
        }

        double [] samples;
        double[][] distData;
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
                    long sample = Math.round(mean + (modeDist*m) + r.nextGaussian() * variance);
                    samples[i+m] = sample;
                    estimator.addSample(new OffsetGmSample(sample, new byte[8]));
                    min = (sample < min) ? sample : min;
                    max = (sample > max) ? sample : max;
                }
            }

            int range = (int)(max - min) + ((int)variance*4);
            distData = new double[range][2];//XY coords
            for(int i = 0; i < range; i++) {
                long xCoord = i+min-((int)variance*2);
                distData[i][0] = xCoord;
                distData[i][1] = estimator.estimate(new OffsetGmSample(xCoord, new byte[8]));
            }
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
