package edu.unh.artt.core.error_sample.processing;

import edu.unh.artt.core.error_sample.representation.AMTLVData;
import edu.unh.artt.core.error_sample.representation.OffsetGmSample;
import edu.unh.artt.core.error_sample.representation.SyncData;
import org.apache.commons.codec.binary.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Processes samples that pertain only to the offsetFromGm time error measurement. The data parsed by this class is one
 * dimensional, not accounting for the clockId and sample weight.
 */
public class OffsetSampleProcessor extends SampleProcessor<OffsetGmSample> {
    private final static Logger logger = LoggerFactory.getLogger(OffsetSampleProcessor.class);

    /* Scaled nanoseconds: incorporates 2 bytes of sub-nanosecond precision into the transmitted value*/
    public static final int SCALED_NS_CONVERSION = 2 << 15;

    /* Record of the network representation values reported by direct link partners (indexed by clockId) */
    private final HashMap<String, Long> network_rep = new HashMap<>();

    /**
     * Computes the offsetFromGm measurement of the downstream link partner with respect to the grandmaster.
     * @see SampleProcessor#computeTimeError(SyncData, double, SyncData, double)
     * @return The newly computed offsetFromGm sample
     */
    @Override
    OffsetGmSample computeTimeError(SyncData gmSync, double upstrmPdelay, SyncData revSync, double dwnstrmPdelay) {
        long t1Gm = gmSync.origin_timestamp.getTimestamp();
        long t1Peer = revSync.origin_timestamp.getTimestamp();

        long t2Gm = gmSync.sync_receipt.getTimestamp();
        long t2Peer = revSync.sync_receipt.getTimestamp();

        double upstrmCorr = new BigInteger(gmSync.correction_field).doubleValue() / SCALED_NS_CONVERSION;
        double dwnstrmCorr = new BigInteger(revSync.correction_field).doubleValue() / SCALED_NS_CONVERSION;
        upstrmCorr += Math.round(upstrmPdelay);
        dwnstrmCorr += Math.round(dwnstrmPdelay);

        double offsetFromGm = (t1Peer - t1Gm) + (t2Gm - t2Peer) + (dwnstrmCorr - upstrmCorr);
        return new OffsetGmSample(1, offsetFromGm, revSync.clock_identity);
    }

    /**
     * Processes the data field of the given AMTLV. The expectation is that the amtlv data is formatted as follows:
     *
     * <table style="width:100%">
     *     <tr>
     *         <th>offset</th>
     *         <th>length</th>
     *         <th>name</th>
     *     </tr>
     *     <tr>
     *         <td>0</td>
     *         <td>4</td>
     *         <td>Weight (# of devices represented)</td>
     *     </tr>
     *     <tr>
     *         <td>4</td>
     *         <td>2</td>
     *         <td>Length of sample data (# of bytes, must be divisible by 8)</td>
     *     </tr>
     *     <tr>
     *         <td>6</td>
     *         <td>2</td>
     *         <td>Length of outlier data (# of bytes, must be divisible by 16)</td>
     *     </tr>
     *     <tr>
     *         <td>8</td>
     *         <td>Sample data length</td>
     *         <td>Sample data points</td>
     *     </tr>
     *     <tr>
     *         <td>Sample data length + 8</td>
     *         <td>Outlier data length</td>
     *         <td>Outlier data points</td>
     *     </tr>
     * </table>
     *
     * @see SampleProcessor#processAMTLVData(byte[], byte[])
     * @return Representation of the AMTLV data field with OffsetFromGm samples
     */
    @Override
    protected AMTLVData<OffsetGmSample> processAMTLVData(byte [] rxClockId, byte[] amtlv) {
        long weight = new BigInteger(Arrays.copyOfRange(amtlv, 0, 4)).longValue(); //Want unsigned
        int sampleLen = 0xffff & new BigInteger(Arrays.copyOfRange(amtlv, 0, 2)).intValue();
        int outlierLen = 0xffff & new BigInteger(Arrays.copyOfRange(amtlv, 2, 4)).intValue();

        if((amtlv.length-8) != outlierLen + sampleLen || amtlv.length % 8 != 0) {
            logger.error("Failed to process offsetFromGm AMTLV data field because it was incorrectly formatted. The " +
                    "AMTLV data field length values did not match the true size of the field or the field was not " +
                    "populated in segments of 8 bytes.");
            return null;
        }

        if(outlierLen % 16 != 0) {
            logger.error("Failed to process offsetFromGm AMTLV data field because it was incorrectly formatted. The " +
                    "outlier list portion of the AMTLV data field must be populated in segments of 16 bytes (offset + " +
                    "clockId)");
            return null;
        }

        //Keep track of the network representation reported by downstream partners
        network_rep.put(Hex.encodeHexString(rxClockId), weight);

        List<OffsetGmSample> samples = new LinkedList<>();
        List<OffsetGmSample> outliers = new LinkedList<>();
        for(int i = 0; i < amtlv.length; i += 8) {
            //Convert from scaled nanoseconds to a java double
            double offset = new BigInteger(Arrays.copyOfRange(amtlv, i, i+8)).doubleValue() / SCALED_NS_CONVERSION;
            if(i >= sampleLen) { //Parse outliers
                i += 8;
                byte [] clockId = Arrays.copyOfRange(amtlv, i, i+8);
                outliers.add(new OffsetGmSample(weight, offset, clockId));
            } else { //Parse samples
                samples.add(new OffsetGmSample(weight, offset, rxClockId));
            }
        }

        return new AMTLVData<>(weight, rxClockId, samples, outliers);
    }

    /**
     * @return The summed value of the representation values reported by downstream nodes
     */
    @Override
    public long getNetworkRepresentation() {
        return network_rep.values().stream().mapToLong(Long::longValue).sum(); //Sum of all reported representations
    }

    /**
     * Packages the resampled data, outliers, and network representation into an AMTLV with the appropriate sample type.
     * Note that the offset is not converted to scaled nanoseconds here.
     * @see SampleProcessor#packageAMTLVData(long, List, double[][])
     */
    @Override
    public AMTLVData<OffsetGmSample> packageAMTLVData(long networkRep, List<OffsetGmSample> outliers, double[][] resampledData) {
        List<OffsetGmSample> samples = Arrays.stream(resampledData).map(
                samp -> new OffsetGmSample(networkRep, samp[0], new byte[8])).collect(Collectors.toList());

        return new AMTLVData<>(networkRep, new byte[8], samples,outliers);
    }

    /**
     * Packages the given AMTLV data into something that can be transmitted on the wire. If the total amount of data
     * being packaged is greater than the maximum frame size then the data is segmented into multiple TLVs. Since
     * samples and outliers are processed per-sample by the upstream node then any number of TLVs can be used. Each
     * byte array returned will have the first 8 bytes as the weight and length fields which will correspond to the
     * data filled into the remainder of the byte array.
     * @see SampleProcessor#amtlvToBytes(AMTLVData, int)
     */
    @Override
    public List<byte[]> amtlvToBytes(AMTLVData<OffsetGmSample> amtlv, int maxDataFieldSize) {
        int remainingSampleLength = amtlv.subnetwork_samples.size() * 8; //offset
        int remainingOutlierLength = amtlv.subnetwork_outliers.size() * 16; //offset + clockId
        int headerSize = 8;

        //Make sure we can at least put one sample in
        if(remainingOutlierLength > 0 && maxDataFieldSize < headerSize + 16 )
            throw new IllegalArgumentException("Max frame size must be at least 24 bytes.");
        else if(maxDataFieldSize < headerSize + 8)
            throw new IllegalArgumentException("Max frame size must be at least 16 bytes.");

        List<byte[]> tlvData = new LinkedList<>();
        do { //Compute the size of each TLV and fill in the headers
            int sampLen = 0, outLen = 0;
            if(remainingSampleLength > 0) { //Compute length of sample data, which has priority
                sampLen = Math.min(remainingSampleLength, maxDataFieldSize - headerSize);
                remainingSampleLength -= sampLen;
            }

            //Compute length of outlier data if there is room left in this TLV
            if(sampLen + headerSize < maxDataFieldSize && remainingOutlierLength > 0) {
                outLen = Math.min(remainingOutlierLength, maxDataFieldSize - sampLen - headerSize);
                remainingOutlierLength -= outLen;
            }

            //Initialize and fill in the header info
            byte [] data = new byte[sampLen + outLen + headerSize];
            System.arraycopy(ByteBuffer.allocate(4).putInt((int)amtlv.weight).array(), 0, data, 0, 4);
            System.arraycopy(ByteBuffer.allocate(2).putShort((short)sampLen).array(), 0, data, 4, 2);
            System.arraycopy(ByteBuffer.allocate(2).putShort((short)outLen).array(), 0, data, 6, 2);
            tlvData.add(data);
        } while(remainingSampleLength + remainingOutlierLength > 0);

        Iterator<OffsetGmSample> sampleIterator = amtlv.subnetwork_samples.iterator();
        Iterator<OffsetGmSample> outlierIterator = amtlv.subnetwork_outliers.iterator();
        for(byte [] data : tlvData) {
            int idx = 8; //Start after the header

            //Start by filling samples first
            while(sampleIterator.hasNext() && (idx+8) <= data.length) {
                OffsetGmSample smpl = sampleIterator.next();
                long offsetScaled = Math.round(smpl.getSample()[0] * SCALED_NS_CONVERSION);
                System.arraycopy(ByteBuffer.allocate(8).putLong((offsetScaled)).array(), 0, data, idx, 8);
                idx+=8;
            }

            //Fill the remainder with the outliers
            while(outlierIterator.hasNext() && (idx+16) <= data.length) {
                OffsetGmSample smpl = outlierIterator.next();
                long offsetScaled = Math.round(smpl.getSample()[0] * SCALED_NS_CONVERSION);
                System.arraycopy(ByteBuffer.allocate(8).putLong((offsetScaled)).array(), 0, data, idx, 8);
                idx+=8;

                System.arraycopy(smpl.getClockIdentity(), 0, data, idx, 8);
                idx+=8;
            }
        }

        return tlvData;
    }

    @Override
    public String toString() {
        return "Offset from GM Sample Processor";
    }
}
