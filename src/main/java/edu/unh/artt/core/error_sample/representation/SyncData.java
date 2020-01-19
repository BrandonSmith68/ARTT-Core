package edu.unh.artt.core.error_sample.representation;

/**
 * Simple representation of the data contained in a Sync message (and Follow Up if two-step). Represents both Sync
 * messages from the grandmaster and reverse Sync messages.
 */
public class SyncData {
    /* Clock identity of the device sending the Sync */
    public final byte [] clock_identity;
    /* Correction field contained in the message */
    public final byte [] correction_field;

    /* Receipt timestamp of the Sync message */
    public final PTPTimestamp sync_receipt;
    /* Time in which the Sync message was sent */
    public final PTPTimestamp origin_timestamp;

    /* The data field of the AMTLV attached to the Sync message (if present) */
    public final byte [] amtlv;

    /**
     * @param t1 Time in which the Sync message was sent
     * @param t2 Receipt timestamp of the Sync message
     * @param correctionField Correction field contained in the message
     * @param clockId Clock identity of the device sending the Sync
     * @param amtlvDat The data field of the AMTLV attached to the Sync message (if present)
     */
    public SyncData(PTPTimestamp t1, PTPTimestamp t2, byte [] correctionField, byte [] clockId, byte [] amtlvDat) {
        sync_receipt = t2;
        origin_timestamp = t1;
        amtlv = amtlvDat;
        clock_identity = clockId;
        correction_field = correctionField;
    }
}
