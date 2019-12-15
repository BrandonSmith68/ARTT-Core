package error_sample.representation;

public class SyncData {
    public final byte [] clock_identity;
    public final byte [] correction_field;
    public final PTPTimestamp sync_receipt;
    public final PTPTimestamp origin_timestamp;
    public final byte [] amtlv;

    public SyncData(PTPTimestamp t1, PTPTimestamp t2, byte [] correctionField, byte [] clockId, byte [] amtlvDat) {
        sync_receipt = t2;
        origin_timestamp = t1;
        amtlv = amtlvDat;
        clock_identity = clockId;
        correction_field = correctionField;
    }
}
