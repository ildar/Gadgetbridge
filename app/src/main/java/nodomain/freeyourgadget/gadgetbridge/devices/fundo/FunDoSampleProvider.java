package nodomain.freeyourgadget.gadgetbridge.devices.fundo;

import de.greenrobot.dao.AbstractDao;
import de.greenrobot.dao.Property;
import nodomain.freeyourgadget.gadgetbridge.devices.AbstractSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.entities.DaoSession;
import nodomain.freeyourgadget.gadgetbridge.entities.FunDoActivitySample;
import nodomain.freeyourgadget.gadgetbridge.entities.FunDoActivitySampleDao;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind;

public class FunDoSampleProvider extends AbstractSampleProvider<FunDoActivitySample> {
    private GBDevice mDevice;
    private DaoSession mSession;

    public FunDoSampleProvider(GBDevice device, DaoSession session) {
        super(device, session);
        this.mSession = session;
        this.mDevice = device;
    }

    @Override // nodomain.freeyourgadget.gadgetbridge.devices.AbstractSampleProvider
    public AbstractDao<FunDoActivitySample, ?> getSampleDao() {
        return getSession().getFunDoActivitySampleDao();
    }

    /* access modifiers changed from: protected */
    @Override // nodomain.freeyourgadget.gadgetbridge.devices.AbstractSampleProvider
    public Property getRawKindSampleProperty() {
        return FunDoActivitySampleDao.Properties.RawKind;
    }

    /* access modifiers changed from: protected */
    public Property getBloodOxygenSampleProperty() {
        return FunDoActivitySampleDao.Properties.BloodOxygen;
    }

    /* access modifiers changed from: protected */
    public Property getHeartRateSampleProperty() {
        return FunDoActivitySampleDao.Properties.HeartRate;
    }

    /* access modifiers changed from: protected */
    public Property getBloodPressureSystoleSampleProperty() {
        return FunDoActivitySampleDao.Properties.BloodPressureSystole;
    }

    /* access modifiers changed from: protected */
    public Property getBloodPressureDiastoleSampleProperty() {
        return FunDoActivitySampleDao.Properties.BloodPressureDiastole;
    }

    @Override // nodomain.freeyourgadget.gadgetbridge.devices.AbstractSampleProvider
    public Property getTimestampSampleProperty() {
        return FunDoActivitySampleDao.Properties.Timestamp;
    }

    /* access modifiers changed from: protected */
    @Override // nodomain.freeyourgadget.gadgetbridge.devices.AbstractSampleProvider
    public Property getDeviceIdentifierSampleProperty() {
        return FunDoActivitySampleDao.Properties.DeviceId;
    }

    @Override // nodomain.freeyourgadget.gadgetbridge.devices.SampleProvider
    public int normalizeType(int rawType) {
        return rawType;
    }

    @Override // nodomain.freeyourgadget.gadgetbridge.devices.SampleProvider
    public int toRawActivityKind(int activityKind) {
        switch (activityKind) {
            case FunDoConstants.TYPE_DEEP_SLEEP:
                return ActivityKind.TYPE_DEEP_SLEEP;
            case FunDoConstants.TYPE_LIGHT_SLEEP:
                return ActivityKind.TYPE_LIGHT_SLEEP;
            case FunDoConstants.TYPE_AWAKE:
            default:
                return ActivityKind.TYPE_ACTIVITY;
        }
    }

    @Override // nodomain.freeyourgadget.gadgetbridge.devices.SampleProvider
    public float normalizeIntensity(int rawIntensity) {
        return ((float) rawIntensity) / 255.0f;
    }

    @Override // nodomain.freeyourgadget.gadgetbridge.devices.SampleProvider
    public FunDoActivitySample createActivitySample() {
        return new FunDoActivitySample();
    }
}
