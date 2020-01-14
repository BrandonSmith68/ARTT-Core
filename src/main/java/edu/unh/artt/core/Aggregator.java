package edu.unh.artt.core;

import edu.unh.artt.core.error_sample.processing.SampleProcessor;
import edu.unh.artt.core.error_sample.representation.AMTLVData;
import edu.unh.artt.core.error_sample.representation.TimeErrorSample;
import edu.unh.artt.core.models.ErrorModel;
import edu.unh.artt.core.outlier.OutlierDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

//Todo comments once the error model structure is set
public class Aggregator<Sample extends TimeErrorSample> {
    final static Logger logger = LoggerFactory.getLogger(Aggregator.class);
    final Class<Sample> sample_type = (Class<Sample>) ((ParameterizedType)getClass().getGenericSuperclass()).getActualTypeArguments()[0];

    private final AtomicReference<SampleProcessor<Sample>> sample_processor = new AtomicReference<>();
    private final AtomicInteger max_obs_weight = new AtomicInteger(1);

    private final ErrorModel<Sample> network_model;
    private final OutlierDetector<Sample> network_outlier_detector;
    private final AtomicReference<ArrayList<Sample>> outlier_buffer;
    private final int network_window_size;

    public Aggregator(SampleProcessor<Sample> processor, ErrorModel<Sample> baselineModel,
                      OutlierDetector<Sample> networkDetector, int numPorts, int networkWindowSize) {


        network_model = baselineModel;
        network_window_size = networkWindowSize;

        int windowSize = network_model.getWindowSize()*numPorts;
        network_model.modifyWindowSize(windowSize);
        outlier_buffer = new AtomicReference<>(new ArrayList<>(windowSize));

        network_outlier_detector = networkDetector;
        setSampleProcessor(processor);
    }

    public SampleProcessor<Sample> getSampleProcessor() {
        return sample_processor.get();
    }

    public void setSampleProcessor(SampleProcessor<Sample> proc) {
        logger.info("New sync messages are now being processed using a " + proc);
        proc.registerErrorComputeAction((sample -> {
            network_model.addSample(sample);

            if(network_outlier_detector.isOutlier(sample))
                outlier_buffer.get().add(sample);
        }));
        proc.onAMTLVReceipt((amtlv) -> {
            amtlv.subnetwork_samples.forEach(sample -> {
                network_model.addSample(sample);
                if(sample.getWeight() > max_obs_weight.get())
                    max_obs_weight.set(sample.getWeight());
            });
            amtlv.subnetwork_outliers.stream().filter(network_outlier_detector::isOutlier).forEach((smp) -> outlier_buffer.get().add(smp));
        });
        sample_processor.set(proc);
    }

    public AMTLVData<Sample> retrieveNewData() {
        ArrayList<Sample> outliers = outlier_buffer.getAndSet(new ArrayList<>());
        double[][] samples = network_model.resample(network_window_size);
        AMTLVData<Sample> amtlvData = sample_processor.get().packageAMTLVData(max_obs_weight.get(), outliers, samples);
        return amtlvData;
    }

    public static void main(String [] args) {
//        logger.info();
    }
}
