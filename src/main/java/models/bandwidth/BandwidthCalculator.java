package models.bandwidth;

public interface BandwidthCalculator {
    double [] computeBandwidth(double [] stdDev, int sampleSize);
}
