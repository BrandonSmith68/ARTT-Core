package models.bandwidth;

public class ScottBandwidthCalculator implements BandwidthCalculator {
    final int coefficient;

    public ScottBandwidthCalculator(int coef) {
        coefficient = coef;
    }

    @Override
    public double[] computeBandwidth(double[] stdDevs, int sampleSize) {
        double [] res = new double[stdDevs.length];
        for(int i = 0; i < stdDevs.length; i++)
            res[i] = Math.sqrt(coefficient) * stdDevs[i] * Math.pow(sampleSize, -(1.0 / (stdDevs.length + 4)));
        return res;
    }
}
