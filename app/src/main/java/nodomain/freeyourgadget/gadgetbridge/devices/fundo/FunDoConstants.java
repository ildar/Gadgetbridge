package nodomain.freeyourgadget.gadgetbridge.devices.fundo;

import java.util.UUID;

import nodomain.freeyourgadget.gadgetbridge.service.btle.AbstractBTLEDeviceSupport;

public class FunDoConstants {

    public static final UUID FunDo_SERVICE_FEE7 = UUID.fromString(String.format(AbstractBTLEDeviceSupport.BASE_UUID, "FEE7"));
    public static final UUID FunDo_SERVICE_FEE7_C_FEA1 = UUID.fromString(String.format(AbstractBTLEDeviceSupport.BASE_UUID, "FEA1"));//notify, read; realtime step data; stops after write 0 to
    public static final UUID FunDo_SERVICE_FEE7_C_FEC8 = UUID.fromString(String.format(AbstractBTLEDeviceSupport.BASE_UUID, "FEC8"));//indicate
    public static final UUID FunDo_SERVICE_FEE7_C_FEC9 = UUID.fromString(String.format(AbstractBTLEDeviceSupport.BASE_UUID, "FEC9"));//read; get mac adress
    public static final UUID FunDo_SERVICE_FEE7_C_FEC7 = UUID.fromString(String.format(AbstractBTLEDeviceSupport.BASE_UUID, "FEC7"));//write

    public static final UUID FunDo_SERVICE_FFC0 = UUID.fromString("f000ffc0-0451-4000-b000-000000000000");
    public static final UUID FunDo_SERVICE_FFC0_C_FFC1 = UUID.fromString("f000ffc1-0451-4000-b000-000000000000");//notify, write, write no response
    public static final UUID FunDo_SERVICE_FFC0_C_FFC2 = UUID.fromString("f000ffc2-0451-4000-b000-000000000000");//notify, write, write no response

    //This is the main Service used to transfer data:
    public static final UUID FunDo_SERVICE3 = UUID.fromString("c3e6fea0-e966-1000-8000-be99c223df6a");
    public static final UUID FunDo_SERVICE3_NOTIFY = UUID.fromString("c3e6fea2-e966-1000-8000-be99c223df6a");
    public static final UUID FunDo_SERVICE3_WRITE = UUID.fromString("c3e6fea1-e966-1000-8000-be99c223df6a");
    public static final UUID FunDo_SERVICE_2902 = UUID.fromString(String.format(AbstractBTLEDeviceSupport.BASE_UUID, "2902"));

    public static final byte SCMD_GetFirmwareInfo = 0x12;
    public static final byte SCMD_BLOOD_O2 = -78;
    public static final byte CMD_BLOOD_PRESSUE = -79;

    public static final byte CMD_HEARTHISTORY = -92;
    public static final byte CMD_HEART_RATE = -85;
    public static final byte CMD_RUN = -84;
    public static final byte CMD_SLEEPHISTORY = -94;
    public static final byte CMD_STEPHISTORY = -93;
    public static final byte SCMD_SET_LANGUAGE = 0x27;
    public static final byte SCMD_SET_WEATHER = 0x30;
    public static final byte SCMD_SEND_NOTIFICATION = (byte) 0x60;
    public static final byte SCMD_GET_DATA = (byte) (0xa0 & 0xFF);
    public static final byte SCMD_GET_HEARTRATE = (byte) (0x92 & 0xFF);
    public static final byte SCMD_SET_TIME = (byte) (0x20 & 0xFF);
    public static final String CONFIG_ITEM_STEP_GOAL = "step_goal";
    public static final String CONFIG_ITEM_TIMEZONE_OFFSET = "timezone_offset";


    public static final byte FunDoHistorySleep = 1;
    public static final String PREF_FunDoHistorySleepSyncTime = "FunDoHistorySleepSyncTime";
    public static final byte FunDoHistoryHeartRate = 2;
    public static final String PREF_FunDoHistoryHeartSyncTime = "FunDoHistoryHeartSyncTime";
    public static final byte FunDoHistorySteps = 3;
    public static final String PREF_FunDoHistoryStepsSyncTime = "FunDoHistoryStepsSyncTime";
    public static final byte FunDoHistoryDaysToFetch = 14;

    public static final byte ICON_CALL = 0;
    public static final byte ICON_SMS = 1;
    public static final byte ICON_SNAPCHAT = 2;
    public static final byte ICON_WECHAT = 3;
    public static final byte ICON_FACEBOOK = 4;
    public static final byte ICON_DOTS = 5;//todo:logo meaning?
    public static final byte ICON_TWITTER = 6;
    public static final byte ICON_WHATSAPP = 7;
    public static final byte ICON_LINE = 8;
    public static final byte ICON_INSTAGRAM = 9;
    public static final byte ICON_APP = 10;


    public static final String PREF_STEP_COUNT = "STEP_COUNT";
    public static final String PREF_STEP_GOAL = "STEP_GOAL";
    public static final String PREF_TIMEZONE_OFFSET = "TIMEZONE_OFFSET";
    public static final String PREF_ACTIVITY_TRACKING = "fundo_activity_tracking";

    public static final String PREF_DO_NOT_DISTURB = "fundo_do_not_disturb";
    public static final String PREF_DO_NOT_DISTURB_END = "fundo_do_not_disturb_end";
    public static final String PREF_DO_NOT_DISTURB_START = "fundo_do_not_disturb_start";

    public static final String PREF_FUNDO_HEARTRATE_INTERVAL = "fundo_heartrate_interval";
    public static final String PREF_FUNDO_HEARTRATE_TIME_START = "fundo_heartrate_time_start";
    public static final String PREF_FUNDO_HEARTRATE_TIME_STOP = "fundo_heartrate_time_stop";
    public static final String PREF_HANDMOVE_DISPLAY = "fundo_handmove_display";

    public static final String PREF_INACTIVITY_ENABLE = "fundo_inactivity_warnings";
    public static final String PREF_INACTIVITY_MO = "fundo_prefs_inactivity_repetitions_mo";
    public static final String PREF_INACTIVITY_TU = "fundo_prefs_inactivity_repetitions_tu";
    public static final String PREF_INACTIVITY_WE = "fundo_prefs_inactivity_repetitions_we";
    public static final String PREF_INACTIVITY_TH = "fundo_prefs_inactivity_repetitions_th";
    public static final String PREF_INACTIVITY_FR = "fundo_prefs_inactivity_repetitions_fr";
    public static final String PREF_INACTIVITY_SA = "fundo_prefs_inactivity_repetitions_sa";
    public static final String PREF_INACTIVITY_SU = "fundo_prefs_inactivity_repetitions_su";
    public static final String PREF_INACTIVITY_SIGNALING = "fundo_vibration_profile_inactivity";
    public static final String PREF_INACTIVITY_START = "fundo_inactivity_warnings_start";
    public static final String PREF_INACTIVITY_END = "fundo_inactivity_warnings_end";
    public static final String PREF_INACTIVITY_THRESHOLD = "fundo_inactivity_warnings_threshold";

    public static final String PREF_WATER_ENABLE = "fundo_water_warnings";
    public static final String PREF_WATER_MO = "fundo_prefs_water_repetitions_mo";
    public static final String PREF_WATER_TU = "fundo_prefs_water_repetitions_tu";
    public static final String PREF_WATER_WE = "fundo_prefs_water_repetitions_we";
    public static final String PREF_WATER_TH = "fundo_prefs_water_repetitions_th";
    public static final String PREF_WATER_FR = "fundo_prefs_water_repetitions_fr";
    public static final String PREF_WATER_SA = "fundo_prefs_water_repetitions_sa";
    public static final String PREF_WATER_SU = "fundo_prefs_water_repetitions_su";
    public static final String PREF_WATER_START = "fundo_water_warnings_start";
    public static final String PREF_WATER_END = "fundo_water_warnings_end";
    public static final String PREF_WATER_THRESHOLD = "fundo_water_warnings_threshold";


    public final static int TYPE_LIGHT_SLEEP = 1;
    public final static int TYPE_DEEP_SLEEP = 2;
    public final static int TYPE_AWAKE = 3;
}
