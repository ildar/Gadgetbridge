/*  Copyright (C) 2020 dc6jn
    based on files and other work from:
    Copyright (C) 2016-2020 Andreas Shimokawa, Carsten Pfeiffer, Daniele
    Gobbetti, José Rebelo, Kranz, Sebastian Kranz, Cre3per, Petr Kadlec, protomors

    This file is part of Gadgetbridge.

    Gadgetbridge is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Gadgetbridge is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.

    This file contains parts from several other sources, mainly
    - nodomain/freeyourgadget/gadgetbridge/service/devices/makibeshr3/MakibesHR3DeviceSupport.java
    - nodomain/freeyourgadget/gadgetbridge/devices/zetime/ZeTimeCoordinator.java
    - probably some more...

*/

package nodomain.freeyourgadget.gadgetbridge.service.devices.fundo;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.CountDownTimer;
import android.os.Handler;
import android.widget.Toast;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst;
import nodomain.freeyourgadget.gadgetbridge.database.DBHandler;
import nodomain.freeyourgadget.gadgetbridge.database.DBHelper;
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventBatteryInfo;
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventFindPhone;
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventVersionInfo;
import nodomain.freeyourgadget.gadgetbridge.devices.fundo.FunDoConstants;
import nodomain.freeyourgadget.gadgetbridge.devices.fundo.FunDoSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.devices.makibeshr3.MakibesHR3Coordinator;
import nodomain.freeyourgadget.gadgetbridge.entities.Device;
import nodomain.freeyourgadget.gadgetbridge.entities.FunDoActivitySample;
import nodomain.freeyourgadget.gadgetbridge.entities.User;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind;
import nodomain.freeyourgadget.gadgetbridge.model.ActivityUser;
import nodomain.freeyourgadget.gadgetbridge.model.Alarm;
import nodomain.freeyourgadget.gadgetbridge.model.BatteryState;
import nodomain.freeyourgadget.gadgetbridge.model.CalendarEventSpec;
import nodomain.freeyourgadget.gadgetbridge.model.CallSpec;
import nodomain.freeyourgadget.gadgetbridge.model.CannedMessagesSpec;
import nodomain.freeyourgadget.gadgetbridge.model.MusicSpec;
import nodomain.freeyourgadget.gadgetbridge.model.MusicStateSpec;
import nodomain.freeyourgadget.gadgetbridge.model.NotificationSpec;
import nodomain.freeyourgadget.gadgetbridge.model.WeatherSpec;
import nodomain.freeyourgadget.gadgetbridge.service.btle.AbstractBTLEDeviceSupport;
import nodomain.freeyourgadget.gadgetbridge.service.btle.BLETypeConversions;
import nodomain.freeyourgadget.gadgetbridge.service.btle.GattService;
import nodomain.freeyourgadget.gadgetbridge.service.btle.TransactionBuilder;
import nodomain.freeyourgadget.gadgetbridge.util.GB;
import nodomain.freeyourgadget.gadgetbridge.util.Prefs;

public class FunDoDeviceSupport extends AbstractBTLEDeviceSupport {

    public static final Logger LOG = LoggerFactory.getLogger(FunDoDeviceSupport.class.getSimpleName());
    private final GBDeviceEventBatteryInfo batteryCmd = new GBDeviceEventBatteryInfo();
    private final int maxMsgLength = 20;
    private final GBDeviceEventVersionInfo versionCmd = new GBDeviceEventVersionInfo();
    public BluetoothGattCharacteristic ctrlCharacteristic = null;
    public BluetoothGattCharacteristic measureCharacteristic = null;
    public BluetoothGattCharacteristic realtimeStepsCharacteristic = null;
    public BluetoothGattCharacteristic c2 = null;
    public BluetoothGattCharacteristic c3 = null;
    DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH);
    ByteArrayOutputStream inputStream = new ByteArrayOutputStream();
    private Handler mFindPhoneHandler = new Handler();
    private int days_to_fetch_SleepData = FunDoConstants.FunDoHistoryDaysToFetch;
    private int days_to_fetch_StepsData = FunDoConstants.FunDoHistoryDaysToFetch;
    private int days_to_fetch_HeartRateData = FunDoConstants.FunDoHistoryDaysToFetch;
    private int expected_msg_len;
    private boolean input_in_progress = false;

    //nodomain/freeyourgadget/gadgetbridge/service/devices/makibeshr3/MakibesHR3DeviceSupport.java countdown to remove notification
    private CountDownTimer mFetchCountDown = new CountDownTimer(20000, 2000) {
        public void onTick(long millisUntilFinished) {
        }

        public void onFinish() {
            FunDoDeviceSupport.LOG.info("download finished");
            GB.updateTransferNotification(null, "", false, 100, FunDoDeviceSupport.this.getContext());
        }
    };


    private short seq = 0;


    public FunDoDeviceSupport() {
        super(LOG);
        addSupportedService(GattService.UUID_SERVICE_GENERIC_ACCESS);
        addSupportedService(GattService.UUID_SERVICE_GENERIC_ATTRIBUTE);
        addSupportedService(FunDoConstants.FunDo_SERVICE3);
        addSupportedService(FunDoConstants.FunDo_SERVICE_FEE7);
        addSupportedService(FunDoConstants.FunDo_SERVICE_FFC0);

    }

    public static byte calculateCRC8(byte[] b, int len) {
        int crc = 255;
        for (int i = 0; i < len; i++) {
            int crc2 = crc ^ ((byte) ((Integer.reverse(b[i]) >> 24) & 0xFF));
            for (int j = 0; j < 8; j++) {
                if ((crc2 & 128) == 128) {
                    crc2 = (crc2 << 1) ^ 29;
                } else {
                    crc2 <<= 1;
                }
            }
            crc = crc2 & 0xFF;
        }
        return (byte) (((byte) ((Integer.reverse(crc) >> 24) & 0xFF)) & 0xFF);
    }

    private void fetch(boolean start) {
        if (start) {
            GB.updateTransferNotification(null, getContext().getString(R.string.busy_task_fetch_activity_data), true, 0, getContext());
        }
        this.mFetchCountDown.cancel();
        this.mFetchCountDown.start();
    }

    /**
     * from nodomain/freeyourgadget/gadgetbridge/service/devices/makibeshr3/MakibesHR3DeviceSupport.java
     * Use to show the battery icon in the device card.
     * If the icon shows up later, the user might be trying to tap one thing but the battery icon
     * will shift everything.
     * This is hacky. There should be a "supportsBattery" function in the coordinator that displays
     * the battery icon before the battery level is received.
     */
    private void fakeBattery() {
        GBDeviceEventBatteryInfo batteryInfo = new GBDeviceEventBatteryInfo();
        batteryInfo.level = 0;
        batteryInfo.state = BatteryState.UNKNOWN;
        handleGBDeviceEvent(batteryInfo);
    }

    @Override // nodomain.freeyourgadget.gadgetbridge.service.btle.AbstractBTLEDeviceSupport
    protected TransactionBuilder initializeDevice(TransactionBuilder builder) {
        LOG.info("Initializing FunDo");
        fakeBattery();
        //Reset history fetch counter:
        days_to_fetch_SleepData = FunDoConstants.FunDoHistoryDaysToFetch;
        days_to_fetch_StepsData = FunDoConstants.FunDoHistoryDaysToFetch;
        days_to_fetch_HeartRateData = FunDoConstants.FunDoHistoryDaysToFetch;

        this.gbDevice.setState(GBDevice.State.INITIALIZING);
        this.gbDevice.sendDeviceUpdateIntent(getContext());
        this.measureCharacteristic = getCharacteristic(FunDoConstants.FunDo_SERVICE3_NOTIFY);
        this.ctrlCharacteristic = getCharacteristic(FunDoConstants.FunDo_SERVICE3_WRITE);
        builder.setGattCallback(this);
        builder.notify(this.measureCharacteristic, true);
        realtimeStepsCharacteristic = getCharacteristic(FunDoConstants.FunDo_SERVICE_FEE7_C_FEA1);
        builder.notify(realtimeStepsCharacteristic, false);// realtime step data, disable
        c2 = getCharacteristic(FunDoConstants.FunDo_SERVICE_FFC0_C_FFC1);
        builder.notify(c2, true);
        c3 = getCharacteristic(FunDoConstants.FunDo_SERVICE_FFC0_C_FFC2);
        builder.notify(c3, true);
        LOG.info("Initializing FunDo: start configuration");
        sendShortCMD(builder, (byte) 4, (byte) 0x55);
        sendShortCMD(builder, (byte) 1, (byte) 14);
        set_lang_timeoff(builder);
        set_DateAndTime(builder);
        getFirmwareVersion(builder);
        syncConfiguration(builder);

        this.gbDevice.setState(GBDevice.State.INITIALIZED);
        this.gbDevice.sendDeviceUpdateIntent(getContext());
        LOG.info("Initialization FunDo Done");
        return builder;
    }

    protected SharedPreferences getDeviceSpecificPreferences() {
        return GBApplication.getDeviceSpecificSharedPrefs(getDevice().getAddress());
    }

    private void syncConfiguration(TransactionBuilder builder) {
        ActivityUser activityUser = new ActivityUser();
        sendPersonalInformation(activityUser.getAge(), activityUser.getYearOfBirth(), activityUser.getGender(), activityUser.getHeightCm(), activityUser.getWeightKg());
        setHeartRateLimits(builder);
        setStepGoal(builder);
        setInactivityAlert(builder);
        setWaterAlert(builder);
        setDisplayOnMovement(builder);
        setDoNotDisturb(builder);

    }

    @Override // nodomain.freeyourgadget.gadgetbridge.service.DeviceSupport
    public boolean useAutoConnect() {
        return true;
    }

    @Override
    public boolean onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
        super.onCharacteristicRead(gatt, characteristic, status);
        UUID characteristicUUID = characteristic.getUuid();
        byte[] data = characteristic.getValue();
        LOG.info(String.format("on characteristic %s read: %s", characteristicUUID, GB.hexdump(data, 0, data.length)));
        return true;
    }

    @Override
    public boolean onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
        UUID characteristicUUID = characteristic.getUuid();
        LOG.info(String.format("hit onCharacteristicWrite %s - status:%d", characteristicUUID, Integer.valueOf(status)));
        return true;
    }

    @Override
    public boolean onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        byte[] payload;
        //boolean rdRemoteRssi = gatt.readRemoteRssi();
        if (super.onCharacteristicChanged(gatt, characteristic)) {
            return true;
        }
        byte[] data = characteristic.getValue();

        UUID characteristicUUID = characteristic.getUuid();
        if (FunDoConstants.FunDo_SERVICE_FEE7_C_FEA1.equals(characteristicUUID)) {
            ByteBuffer pl = ByteBuffer.wrap(data);
            pl.order(ByteOrder.LITTLE_ENDIAN);
            byte n1 = pl.get(); // always 1
            if (n1 == 1) {
                int stepsl = pl.getShort();
                LOG.info(String.format("got realtime step data from characteristic %s val=%s (%d steps)", characteristicUUID, GB.hexdump(data, 0, data.length), stepsl));
            } else {
                LOG.info(String.format("got realtime data from characteristic %s type %d, val=%s ", characteristicUUID, n1, GB.hexdump(data, 0, data.length)));
            }
            return true;
        }

        LOG.info("onReceive:" + GB.hexdump(data, 0, data.length));
        if (this.input_in_progress) {
            try {
                this.inputStream.write(data);
                LOG.info(String.format("append len=%d Bytes, complete buffer so far (%d Bytes): %s", data.length, this.inputStream.size(), GB.hexdump(this.inputStream.toByteArray(), 0, this.inputStream.size())));
            } catch (Exception e) {
                LOG.warn(e.getMessage());
            }
        } else if (isMsgFormatOK(data, false)) {
            int dataLength = ((data[3] & 0xFF) | ((data[2] << 8) & 0xFF00)) + 8;
            LOG.info(String.format("got message start header, expected msg len=%d, current message len=%d", Integer.valueOf(dataLength), Integer.valueOf(data.length)));
            this.inputStream.reset();
            this.expected_msg_len = dataLength;
            this.input_in_progress = true;
            try {
                this.inputStream.write(data);
            } catch (Exception e2) {
                LOG.warn(e2.getMessage());
            }
        }
        byte[] msg = this.inputStream.toByteArray();
        if (msg.length > this.expected_msg_len) {
            this.input_in_progress = false;
            this.inputStream.reset();
            LOG.error("unexpected reset of input stream, maybe we missed some packet header?");
            return true;
        } else if (isMsgFormatOK(msg, true)) {
            this.input_in_progress = false;
            this.inputStream.reset();

            int cmd = msg[8] & 0xFF;
            int subcmd = msg[10] & 0xFF;
            int query = (msg[6] << 8) | msg[7];
            int pll = (msg[11] << 8) | (msg[12] & 0xFF);
            if (pll > 0) {
                payload = Arrays.copyOfRange(msg, 13, msg.length);
            } else {
                payload = null;
            }
            LOG.info(String.format("onReceive: valid answer to query %d :CMD:0x%02x Subcmd:0x%02x PLL:%d, msg=%s", Integer.valueOf(query), Integer.valueOf(cmd), Integer.valueOf(subcmd), Integer.valueOf(pll), GB.hexdump(msg, 0, msg.length)));
            switch (cmd) {
                case 1:
                    parse_cmd1(msg, query, cmd, subcmd, pll, payload);
                    break;
                case 2:
                    parse_cmd2(msg, query, cmd, subcmd, pll, payload);
                    break;
                case 4:
                    parse_cmd4(msg, query, cmd, subcmd, pll, payload);
                    break;
                case 5:
                    parse_cmd5(msg, query, cmd, subcmd, pll, payload);
                    break;
                case 6:
                    parse_cmd6(msg, query, cmd, subcmd, pll, payload);
                    break;
                case 9:
                    parse_cmd9(msg, query, cmd, subcmd, pll, payload);
                    break;
                case 0x0a:
                    parse_cmdA(msg, query, cmd, subcmd, pll, payload);
                    break;
                default:
                    LOG.warn(String.format("onChange: %s not handled valid  CMD:%02x subcmd:%02x PLL:%d, msg:%s", characteristicUUID, Integer.valueOf(cmd), Integer.valueOf(subcmd), Integer.valueOf(pll), GB.hexdump(msg, 0, msg.length)));
            }
        } else {
            LOG.warn(String.format("received message incomplete or invalid from characteristic : %s, msg:%s", characteristicUUID, GB.hexdump(msg, 0, msg.length)));
            return true;
        }
        return true;
    }

    public void logMessageContent(String info, byte[] value) {
        if (value != null) {
            LOG.info(String.format("%s: %d Bytes: %s", info, Integer.valueOf(value.length), GB.hexdump(value, 0, value.length)));
            return;
        }
        LOG.info(info);
    }

    private boolean isMsgFormatOK(byte[] msg, boolean checkSize) {
        if (msg != null) {
            try {
                int header1 = msg[0] & 0xFF;
                int header2 = msg[1] & 0xFF;
                if (header1 == 0xBA && header2 == 0x30 && msg[8] != 0 && msg[9] != 0 && msg[8] == msg[9]) {
                    if (!checkSize) {
                        return true;
                    }
                    if ((((msg[2] << 8) & 0xFF00) | (msg[3] & 0xFF)) + 8 == msg.length) {
                        byte[] bytestocrc = Arrays.copyOfRange(msg, 8, msg.length);
                        if (msg[5] == calculateCRC8(bytestocrc, bytestocrc.length)) {
                            return true;
                        }
                    }
                }
            } catch (ArrayIndexOutOfBoundsException e) {
                return false;
            }
        }
        return false;
    }


    // copied from MakibesHR3:
    private void addGBActivitySamples(FunDoActivitySample[] samples) {
        try (DBHandler dbHandler = GBApplication.acquireDB()) {

            User user = DBHelper.getUser(dbHandler.getDaoSession());
            Device device = DBHelper.getDevice(this.getDevice(), dbHandler.getDaoSession());

            FunDoSampleProvider provider = new FunDoSampleProvider(this.getDevice(), dbHandler.getDaoSession());

            for (FunDoActivitySample sample : samples) {
                if (sample.getTimestamp() == 0) {
                    sample.setTimestamp((int) (System.currentTimeMillis() / 1000));
                }
                sample.setDevice(device);
                sample.setUser(user);
                sample.setProvider(provider);
                provider.addGBActivitySample(sample);
            }

        } catch (Exception ex) {
            LOG.error(ex.getMessage());
        }
    }

    private void addGBActivitySample(FunDoActivitySample sample) {
        addGBActivitySamples(new FunDoActivitySample[]{sample});
    }

    private void saveHeartRateSample(Long timestamp, int heartRate) {
        LOG.info(String.format("received heart rate sample for %s - %d BpM", this.df.format(timestamp), Integer.valueOf(heartRate)));
        FunDoActivitySample sample = new FunDoActivitySample();
        sample.setHeartRate(heartRate);
        sample.setTimestamp((int) (timestamp.longValue() / 1000));
        if (heartRate < 10) {
            LOG.info("heart rate to low, either dead or not correct");
            sample.setRawKind(ActivityKind.TYPE_NOT_WORN);
        } else {
            sample.setRawKind(ActivityKind.TYPE_UNKNOWN);
        }
        addGBActivitySample(sample);
        LOG.info("add heart rate sample: " + sample);
    }

    private void saveStepSample(Long timestamp, int Steps) {
        if (Steps >= 1) {
            LOG.info(String.format("received step sample for %s - %d steps", this.df.format(timestamp), Integer.valueOf(Steps)));
            FunDoActivitySample sample = new FunDoActivitySample();
            sample.setSteps(Steps);
            sample.setTimestamp((int) (timestamp.longValue() / 1000));
            sample.setRawKind(ActivityKind.TYPE_ACTIVITY);
            addGBActivitySample(sample);
            LOG.info("add step sample: " + sample);
        }
    }

    private void saveBloodPressureSample(Long timestamp, int Systole, int Diastole) {
        LOG.info(String.format("received blood pressure sample for %s - %d/%d mmHg", this.df.format(timestamp), Integer.valueOf(Systole), Integer.valueOf(Diastole)));
        FunDoActivitySample sample = new FunDoActivitySample();
        sample.setTimestamp((int) (timestamp.longValue() / 1000));
        sample.setBloodPressureSystole(Integer.valueOf(Systole));
        sample.setBloodPressureDiastole(Integer.valueOf(Diastole));
        addGBActivitySample(sample);
        LOG.info("add blood pressure sample: " + sample);
    }

    private void onReceiveBloodOxygenSample(Long timestamp, int Value) {
        LOG.info(String.format("received oxygen sample for %s - %d %%", this.df.format(timestamp), Integer.valueOf(Value)));
        FunDoActivitySample sample = new FunDoActivitySample();
        sample.setTimestamp((int) (timestamp.longValue() / 1000));
        sample.setBloodOxygen(Integer.valueOf(Value));
        addGBActivitySample(sample);
        LOG.info("add oxygen sample: " + sample);
    }

    private void onReceiveSleepSample(Long timestamp, int Value) {
        FunDoActivitySample sample = new FunDoActivitySample();
        sample.setTimestamp((int) (timestamp.longValue() / 1000));
        sample.setRawIntensity(Value);
        if (Value == FunDoConstants.TYPE_DEEP_SLEEP) {
            sample.setRawKind(ActivityKind.TYPE_DEEP_SLEEP);
        } else if (Value == FunDoConstants.TYPE_LIGHT_SLEEP) {
            sample.setRawKind(ActivityKind.TYPE_LIGHT_SLEEP);
        } else {
            sample.setRawKind(ActivityKind.TYPE_ACTIVITY);
        }
        LOG.info(String.format("received sleep sample for %s- %s", this.df.format(timestamp), ActivityKind.asString(sample.getRawKind(), getContext())));
        addGBActivitySample(sample);
        LOG.info("add sleep sample: " + sample);
    }

    private boolean parse_cmd1(byte[] msg, int query, int cmd, int subcmd, int pll, byte[] payload) {
        byte[] bArr = msg;

        LOG.info(String.format("parse_cmd_%02x: %02x data:%s", Integer.valueOf(cmd), Integer.valueOf(subcmd), GB.hexdump(bArr, 0, bArr.length)));
        if (subcmd == 14) {
            LOG.info(String.format("query %d : cmd %02x-%02x %s handled", Integer.valueOf(query), Integer.valueOf(cmd), Integer.valueOf(subcmd), pll == 0 ? "successful" : "with error"));

        } else if (subcmd != 19) {
            LOG.info(String.format("Parse cmd %02x Unexpected subcmd: %02x", Integer.valueOf(cmd), Integer.valueOf(subcmd)));
        } else {
            int hwver = (((bArr[18] & 0xFF) << 8) & 0xFF00) | (bArr[19] & 0xFF);
            GBDeviceEventVersionInfo gBDeviceEventVersionInfo = this.versionCmd;
            gBDeviceEventVersionInfo.fwVersion = (bArr[13] & 0xFF) + "." + (bArr[14] & 0xFF) + "." + (bArr[15] & 0xFF);
            this.versionCmd.hwVersion = String.format("%04x", Integer.valueOf(hwver));
            handleGBDeviceEvent(this.versionCmd);
            LOG.info(String.format("Firmware version is: %s Hardware version is %d", this.versionCmd.fwVersion, Integer.valueOf(hwver)));
            return true;
        }
        return false;
    }

    private boolean parse_cmd2(byte[] msg, int query, int cmd, int subcmd, int pll, byte[] payload) {
        LOG.info(String.format("parse_cmd_%02x: %02x data:%s", Integer.valueOf(cmd), Integer.valueOf(subcmd), GB.hexdump(msg, 0, msg.length)));
        if (subcmd != 32) {
            if (subcmd != 47) {
                LOG.info(String.format("Parse cmd %02x Unexpected subcmd: %02x", Integer.valueOf(cmd), Integer.valueOf(subcmd)));
            } else {
                LOG.info("got device config data");
                parse_config(msg, cmd, subcmd, pll, payload);
            }
            return false;
        }

        LOG.info(String.format("query %d : cmd %02x-%02x %s handled", Integer.valueOf(query), Integer.valueOf(cmd), Integer.valueOf(subcmd), pll == 0 ? "successful" : "with error"));
        return true;
    }

    private boolean parse_cmd4(byte[] msg, int query, int cmd, int subcmd, int pll, byte[] payload) {
        LOG.info(String.format("parse_cmd_%02x: %02x data:%s", Integer.valueOf(cmd), Integer.valueOf(subcmd), GB.hexdump(msg, 0, msg.length)));
        if (subcmd != 65) {
            LOG.info(String.format("Parse cmd %02x Unexpected subcmd: %02x", Integer.valueOf(cmd), Integer.valueOf(subcmd)));
            return false;
        }
        GBDeviceEventBatteryInfo batteryInfo = new GBDeviceEventBatteryInfo();
        batteryInfo.level = (short) (msg[13] & 0xFF);
        batteryInfo.state = msg[14] == 1 ? BatteryState.BATTERY_CHARGING : BatteryState.BATTERY_NORMAL;
        handleGBDeviceEvent(batteryInfo);
        StringBuilder sb = new StringBuilder();
        sb.append("Battery is ");
        sb.append(batteryInfo.state == BatteryState.BATTERY_CHARGING ? "charging" : "not charging");
        sb.append(", level is: ");
        sb.append(batteryInfo.level);
        LOG.info(sb.toString());
        if (batteryInfo.state == BatteryState.BATTERY_CHARGING) {
            batteryInfo.lastChargeTime = new GregorianCalendar();
        }
        return true;
    }

    private boolean parse_cmd5(byte[] msg, int query, int cmd, int subcmd, int pll, byte[] payload) {
        LOG.info(String.format("parse_cmd_%02x: %02x data:%s", Integer.valueOf(cmd), Integer.valueOf(subcmd), GB.hexdump(msg, 0, msg.length)));
        if (subcmd != 80) {
            LOG.info(String.format("parse_cmd_%02x Unexpected subcmd: %02x", Integer.valueOf(cmd), Integer.valueOf(subcmd)));
            return false;
        }
        LOG.info("Telefon suchen wurde ausgelöst");
        return true;
    }

    private boolean parse_cmd6(byte[] msg, int query, int cmd, int subcmd, int pll, byte[] payload) {
        LOG.info(String.format("parse_cmd_%02x: %02x data:%s", Integer.valueOf(cmd), Integer.valueOf(subcmd), GB.hexdump(msg, 0, msg.length)));
        if (subcmd != 96) {
            LOG.info(String.format("parse_cmd_%02x Unexpected subcmd: %02x", Integer.valueOf(cmd), Integer.valueOf(subcmd)));
            return false;
        }
        LOG.info(String.format("Message %s sent", pll == 0 ? "successful" : "not successful"));
        return true;
    }

    private boolean parse_cmd9(byte[] msg, int query, int cmd, int subcmd, int pll, byte[] payload) {
        LOG.info(String.format("parse_cmd_%02x: %02x data:%s", Integer.valueOf(cmd), Integer.valueOf(subcmd), GB.hexdump(msg, 0, msg.length)));
        if (subcmd != 146) {
            LOG.info(String.format("parse_cmd_%02x Unexpected subcmd: %02x", Integer.valueOf(cmd), Integer.valueOf(subcmd)));
            return false;
        }

        LOG.info(String.format("heartrate settings %02x-%02x %s sent", Integer.valueOf(cmd), Integer.valueOf(subcmd), pll == 0 ? "successful" : "not successful"));
        return true;
    }

    private boolean parse_cmdA(byte[] msg, int query, int cmd, int subcmd, int pll, byte[] payload) {
        byte[] bArr = msg;
        LOG.info(String.format("parse_cmd_%02x: %02x data:%s", Integer.valueOf(cmd), Integer.valueOf(subcmd), GB.hexdump(msg, 0, bArr.length)));
        switch (subcmd) {
            case 0xAB:
                parse_heartrate(msg, cmd, subcmd, pll, payload);
                break;
            case 0xAC:
                parse_runinfo(msg, cmd, subcmd, pll, payload);
                break;
            case 0xB2:
                String info2 = String.format("Blutsauerstoffkonzentration ist %d%%", Integer.valueOf(bArr[13] & 0xFF));
                onReceiveBloodOxygenSample(Long.valueOf(GregorianCalendar.getInstance().getTimeInMillis()), bArr[13]);
                LOG.info(info2);
                GB.toast(getContext(), info2, 1, 1);
            case 0xB1:
                String info = String.format("Blutdruck Sys: %s Dia: %s mmHg", Byte.valueOf(bArr[13]), Byte.valueOf(bArr[14]));
                saveBloodPressureSample(Long.valueOf(GregorianCalendar.getInstance().getTimeInMillis()), bArr[13], bArr[14]);
                LOG.info(info);
                GB.toast(getContext(), info, 1, 1);
                break;
            case 0xA2:
                parse_sleephistory(msg, cmd, subcmd, pll, payload);
                break;
            case 0xA3:
                parse_stephistory(msg, cmd, subcmd, pll, payload);
                break;
            case 0xA4:
                parse_heartratehistory(msg, cmd, subcmd, pll, payload);
                break;
            default:
                LOG.info(String.format("Parse cmd %02x Unexpected subcmd: %02x", Integer.valueOf(cmd), Integer.valueOf(subcmd)));
                break;
        }
        return true;
    }


    private void parse_config(byte[] msg, int cmd, int subcmd, int pll, byte[] payload) {
        String str;
        String str2;
        String str3;
        String str4;
        LOG.info(String.format("parse_cmd_%02x: %02x (getConfig) data:%s", Integer.valueOf(cmd), Integer.valueOf(subcmd), GB.hexdump(msg, 0, msg.length)));
        ByteBuffer pl = ByteBuffer.wrap(payload);
        pl.order(ByteOrder.BIG_ENDIAN);
        pl.rewind();
        int i = 0;
        //pos13+
        while (true) {
            if (i >= 5) {
                break;
            }
            LOG.info(String.format("Alarm %d  @ %02d:%02d ,%s, %s", i, Byte.valueOf(pl.get()), Byte.valueOf(pl.get()), Byte.valueOf(pl.get()), Byte.valueOf(pl.get()), (pl.get() == 1) ? "aktiv" : "nicht aktiv"));
            i++;
        }
        // pos 38+
        LOG.info(String.format("Bewegungswarnung: %s from %02d-%02dUhr, rep=%8s, time=%d, level=%d", (pl.get() == 1) ? "aktiv" : "nicht aktiv", pl.get(), pl.get(), getRepetitionDays(pl.getShort()), pl.get(), pl.getShort()));
        LOG.info(String.format("gender: %s, age=%d, size=%d, weight=%d, steps=%d", pl.get(), Integer.valueOf(pl.get() & 0xFF), Integer.valueOf(pl.get() & 0xFF), pl.get(), Long.valueOf(((long) pl.getInt()) & 4294967295L)));
        LOG.info(String.format("remindermode=%d, do not disturb: %s from %02d:%02d to %02d:%02d", pl.get(), (pl.get() == 1) ? "aktiv" : "nicht aktiv", pl.get(), pl.get(), pl.get(), pl.get()));

        LOG.info(String.format("heart rate measurements %s from %02d:%02d to %02d:%02d every %d min", (pl.get() == 1) ? "aktiv" : "nicht aktiv", pl.get(), pl.get(), pl.get(), pl.get(), pl.get()));
        LOG.info(String.format("system language=%d, time offset=%d,sysscreen=%d,sysbond=%d", pl.get(), pl.get(), pl.get(), pl.get()));

        LOG.info(String.format("water reminder %s from %02d:%02d to %02d:%02d, repeat=%d,  every %d min", (pl.get() == 1) ? "aktiv" : "nicht aktiv", pl.get(), pl.get(), pl.get(), pl.get(), getRepetitionDays(pl.get()), pl.get()));
        LOG.info(String.format("%d,%d,%d, %d,%d,%d,%d, gestures: %d, %d, %d", Integer.valueOf(pl.get() & 0xFF), Integer.valueOf(pl.get() & 0xFF), Integer.valueOf(pl.get() & 0xFF), Integer.valueOf(pl.get() & 0xFF), Integer.valueOf(pl.get() & 0xFF), Integer.valueOf(pl.get() & 0xFF), Integer.valueOf(pl.get() & 0xFF), Integer.valueOf(pl.get() & 0xFF), Integer.valueOf(pl.get() & 0xFF), Integer.valueOf(pl.get() & 0xFF)));
        LOG.info(String.format("Got setup"));
    }

    private void parse_heartrate(byte[] msg, int cmd, int subcmd, int pll, byte[] payload) {
        LOG.info(String.format("parse_cmd_%02x: %02x (live HeartRate) data:%s", Integer.valueOf(cmd), Integer.valueOf(subcmd), GB.hexdump(msg, 0, msg.length)));

        int heartrate = msg[13] & 0xFF;
        if (heartrate != 0) {
            Long timestamp = Long.valueOf(Calendar.getInstance(Locale.ENGLISH).getTimeInMillis());
            LOG.info(String.format("Got heart rate live data: %s %3d BpM", this.df.format(timestamp), Integer.valueOf(heartrate)));
            saveHeartRateSample(timestamp, heartrate);
            GB.toast(getContext(), String.format("Heart Rate measured: %d", Integer.valueOf(heartrate)), 1, 1);
        }
    }
    //nodomain/freeyourgadget/gadgetbridge/service/devices/huami/operations/FetchSportsSummaryOperation.java:

    void saveLastSyncTimestamp(GregorianCalendar timestamp, String key) {
        SharedPreferences.Editor editor = GBApplication.getDeviceSpecificSharedPrefs(getDevice().getAddress()).edit();
        editor.putLong(key, timestamp.getTimeInMillis());
        editor.apply();
    }


    protected GregorianCalendar getLastSuccessfulSyncTime(String key) {
        long timeStampMillis = GBApplication.getDeviceSpecificSharedPrefs(getDevice().getAddress()).getLong(key, 0);
        if (timeStampMillis != 0) {
            GregorianCalendar calendar = BLETypeConversions.createCalendar();
            calendar.setTimeInMillis(timeStampMillis);
            return calendar;
        }
        GregorianCalendar calendar = BLETypeConversions.createCalendar();
        calendar.add(Calendar.DAY_OF_MONTH, -14);
        return calendar;
    }

    private void queryHistoricData() {

        int alldays = FunDoConstants.FunDoHistoryDaysToFetch + FunDoConstants.FunDoHistoryDaysToFetch + FunDoConstants.FunDoHistoryDaysToFetch;
        int percentage = (int) ((int) ((alldays - (days_to_fetch_HeartRateData + days_to_fetch_SleepData + days_to_fetch_StepsData)) * 100) / (alldays + 0.0001)); //to avoid divison by 0

        if (days_to_fetch_SleepData > -1) {
            fetch(true);
            syncData(FunDoConstants.FunDoHistorySleep, days_to_fetch_SleepData);
            GB.updateTransferNotification(null, "Sleep data " + days_to_fetch_SleepData, true, percentage, getContext());
        } else if (days_to_fetch_StepsData > -1) {
            fetch(true);
            syncData(FunDoConstants.FunDoHistorySteps, days_to_fetch_StepsData);
            GB.updateTransferNotification(null, "step data" + days_to_fetch_StepsData, true, percentage, getContext());
        } else if (days_to_fetch_HeartRateData > -1) {
            fetch(true);
            syncData(FunDoConstants.FunDoHistoryHeartRate, days_to_fetch_HeartRateData);
            GB.updateTransferNotification(null, "Heart rate data" + days_to_fetch_HeartRateData, true, percentage, getContext());
        } else {
            //no more history data
            GB.updateTransferNotification(null, "", false, 100, getContext());

            if (getDevice().isBusy()) {
                getDevice().unsetBusyTask();
                GB.signalActivityDataFinish();
            }
        }
    }

    private void parse_heartratehistory(byte[] msg, int cmd, int subcmd, int pll, byte[] payload) {
        LOG.info(String.format("parse_cmd_%02x: %02x (HeartRateHistory) data:%s", Integer.valueOf(cmd), Integer.valueOf(subcmd), GB.hexdump(msg, 0, msg.length)));

        if ((pll / 7 < 1) || (pll < 7)) {
            LOG.info("no heart rate history data available");
            days_to_fetch_HeartRateData -= 1;
            queryHistoricData();
            return;
        }
        ByteBuffer records = ByteBuffer.wrap(payload);
        records.rewind();
        int i = 0;
        while (records.remaining() >= 7) {
            try {
                int year = records.get() + 2000;
                int month = records.get();
                int day = records.get();
                int hour = records.get();
                int minute = records.get();
                int second = records.get();
                try {
                    int heartrate = records.get();
                    i++;
                    GregorianCalendar cal = new GregorianCalendar(year, month - 1, day, hour, minute, second);
                    Long timeStamp = Long.valueOf(cal.getTimeInMillis());
                    LOG.info(String.format("Got heart rate history data: %s: %3d BpM", df.format(timeStamp), heartrate));
                    saveHeartRateSample(timeStamp, heartrate);
                    saveLastSyncTimestamp(cal, FunDoConstants.PREF_FunDoHistoryHeartSyncTime);
                } catch (Exception e) {
                    LOG.warn("Invalid calendar dates");
                    days_to_fetch_HeartRateData -= 1;
                    queryHistoricData();
                    return;
                }
            } catch (BufferUnderflowException e3) {
                LOG.warn("error parsing heart rate data");
                days_to_fetch_HeartRateData -= 1;
                queryHistoricData();
                return;
            }
        }
        GB.toast(getContext(), String.format("parsed %d Heart Rate measurements", Integer.valueOf(i)), 1, 1);
        days_to_fetch_HeartRateData -= 1;
        queryHistoricData();

    }


    private void parse_sleephistory(byte[] msg, int cmd, int subcmd, int pll, byte[] payload) {

        LOG.info(String.format("parse_sleephistory %02x: %02x (SleepHistory) data:%s", Integer.valueOf(cmd), Integer.valueOf(subcmd), GB.hexdump(msg, 0, msg.length)));
        if (pll < 7) {
            LOG.info("no sleep history for requested date available");
            days_to_fetch_SleepData -= 1;
            queryHistoricData();
            return;
        }
        ByteBuffer srecords = ByteBuffer.wrap(payload);
        srecords.order(ByteOrder.BIG_ENDIAN);
        int year = 0;
        int month = 0;
        int day = 0;
        srecords.rewind();
        if (srecords.remaining() > 3) {
            year = srecords.get() + 2000;
            month = srecords.get();
            day = srecords.get();
        }
        LOG.info(String.format("got sleep data for %d-%d-%d", Integer.valueOf(year), Integer.valueOf(month), Integer.valueOf(day)));
        int i = 0;
        while (srecords.remaining() >= 3) {
            int typ = srecords.get();
            int sbh = srecords.get();
            int sbm = srecords.get();
//            int sm1 = srecords.get();
//            int seh = srecords.get();
//            int sem = srecords.get();

            //LOG.info(String.format("Sleep data %d = %d von %02d:%02d bis %02d:%02d, typ=%d", i, typ, sbh, sbm, seh, sem, sm1));
            LOG.info(String.format("Sleep data %d =  %02d:%02d, typ=%d", i, sbh, sbm, typ));
            onReceiveSleepSample(Long.valueOf(new GregorianCalendar(year, month - 1, day, sbh, sbm, 0).getTimeInMillis()), typ);
            //   onReceiveSleepSample(Long.valueOf(new GregorianCalendar(year, month - 1, day, seh, sem, 0).getTimeInMillis()), sm1);

            GregorianCalendar cal = new GregorianCalendar(year, month - 1, day, sbh, sbm, 0);
            Long timeStamp = Long.valueOf(cal.getTimeInMillis());
            saveLastSyncTimestamp(cal, FunDoConstants.PREF_FunDoHistorySleepSyncTime);
            i++;
        }
        GB.toast(getContext(), String.format("parsed %d sleep data records", Integer.valueOf(i)), 1, 1);
        days_to_fetch_SleepData -= 1;
        queryHistoricData();
    }


    private void parse_stephistory(byte[] msg, int cmd, int subcmd, int pll, byte[] payload) {
        char c = 0;
        int i = 2;
        LOG.info(String.format("parse_cmd_%02x: %02x (StepHistory) data:%s", Integer.valueOf(cmd), Integer.valueOf(subcmd), GB.hexdump(msg, 0, msg.length)));
        if (pll < 7) {
            LOG.info("no step history data available");
            days_to_fetch_StepsData -= 1;
            queryHistoricData();
            return;
        }
        ByteBuffer srecords = ByteBuffer.wrap(payload);
        srecords.order(ByteOrder.BIG_ENDIAN);
        int year = 0;
        int month = 0;
        int day = 0;
        srecords.rewind();
        if (srecords.remaining() > 3) {
            year = srecords.get() + 2000;
            month = srecords.get();
            day = srecords.get();
            srecords.get();
        }
        GregorianCalendar cal = new GregorianCalendar(year, month - 1, day, 0, 0, 0);
        SimpleDateFormat pdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");

        int cumulated_steps = 0;
        while (srecords.remaining() >= 4) {
            int hourly_steps = srecords.getInt() - cumulated_steps;
            if (hourly_steps < 0) {
                hourly_steps = 0;
            }

            cumulated_steps += hourly_steps;

            LOG.debug(String.format("Steps from  %s +1hour: = %d", pdf.format(cal.getTime()), hourly_steps));
            saveStepSample(Long.valueOf(cal.getTimeInMillis()), hourly_steps);
            cal.add(Calendar.HOUR, 1);
        }

        saveLastSyncTimestamp(cal, FunDoConstants.PREF_FunDoHistoryStepsSyncTime);


        LOG.info(String.format("Step summary data for %d-%d-%d : %5d steps", Integer.valueOf(year), Integer.valueOf(month), Integer.valueOf(day), Integer.valueOf(cumulated_steps)));
        days_to_fetch_StepsData -= 1;
        queryHistoricData();


    }

    private void parse_runinfo(byte[] msg, int cmd, int subcmd, int pll, byte[] payload) {
        LOG.info(String.format("parse_cmd_%02x: %02x (live runinfo) data:%s", Integer.valueOf(cmd), Integer.valueOf(subcmd), GB.hexdump(msg, 0, msg.length)));
        ByteBuffer pl = ByteBuffer.wrap(payload);
        pl.order(ByteOrder.BIG_ENDIAN);
        int steps = pl.getInt();
        float calories = pl.getFloat();
        float distance = pl.getFloat();
        String info = String.format("Got live usage data: %s Steps:%d\t Calorie=%8.1f \tDistance=%8.1f", this.df.format(Long.valueOf(Calendar.getInstance(Locale.ENGLISH).getTimeInMillis())), Integer.valueOf(steps), Float.valueOf(calories), Float.valueOf(distance));
        LOG.info(info);
        //todo: save to database?
        GB.toast(getContext(), info, 1, 1);
    }

    private String getRepetitionDays(int rep) {
        String res = "";

//        res += ((rep & 1) == 1) ? getContext().getResources().getString(R.string.alarm_mon_short) : "";
//        res += ((rep & 2) == 2) ? getContext().getResources().getString(R.string.alarm_tue_short) : "";
//        res += ((rep & 1) == 1) ? getContext().getResources().getString(R.string.alarm_wed_short) : "";
//        res += ((rep & 1) == 1) ? getContext().getResources().getString(R.string.alarm_thu_short) : "";
//        res += ((rep & 1) == 1) ? getContext().getResources().getString(R.string.alarm_fri_short) : "";
//        res += ((rep & 1) == 1) ? getContext().getResources().getString(R.string.alarm_sat_short) : "";
//        res += ((rep & 1) == 1) ? getContext().getResources().getString(R.string.alarm_sun_short) : "";

        res += ((rep & 1) == 1) ? "mon, " : "";
        res += ((rep & 2) == 2) ? "tue, " : "";
        res += ((rep & 4) == 4) ? "wed, " : "";
        res += ((rep & 8) == 8) ? "thu, " : "";
        res += ((rep & 16) == 16) ? "fri, " : "";
        res += ((rep & 32) == 32) ? "sat, " : "";
        res += ((rep & 64) == 64) ? "sun, " : "";
        return res;
    }

    @Override // nodomain.freeyourgadget.gadgetbridge.devices.EventHandler
    public void onSetAlarms(ArrayList<? extends Alarm> alarms) {
        TransactionBuilder transactionBuilder = createTransactionBuilder("setalarms");
        ByteBuffer buf = ByteBuffer.allocate(28);
        buf.order(ByteOrder.BIG_ENDIAN);
        buf.put((byte) 33);
        buf.putShort((short) 25);
        for (int i = 0; i < alarms.size(); i++) {
            Alarm alarm = alarms.get(i);
            int repetition = alarm.getRepetition();
            //    int reps = 0 | (prefs.getBoolean(FunDoConstants.PREF_INACTIVITY_MO, false) ? 1 : 0) | ((prefs.getBoolean(FunDoConstants.PREF_INACTIVITY_TU, false) ? 1 : 0) << 1) | ((prefs.getBoolean(FunDoConstants.PREF_INACTIVITY_WE, false) ? 1 : 0) << 2) | ((prefs.getBoolean(FunDoConstants.PREF_INACTIVITY_TH, false) ? 1 : 0) << 3) | ((prefs.getBoolean(FunDoConstants.PREF_INACTIVITY_FR, false) ? 1 : 0) << 4) | ((prefs.getBoolean(FunDoConstants.PREF_INACTIVITY_SA, false) ? 1 : 0) << 5) | ((prefs.getBoolean(FunDoConstants.PREF_INACTIVITY_SU, false) ? 1 : 0) << 6);

            buf.put((byte) alarm.getHour());
            buf.put((byte) alarm.getMinute());
            buf.put((byte) alarm.getRepetition());
            buf.put((byte) 1);
            buf.put(alarm.getEnabled() ? (byte) 1 : 0);
            LOG.info(String.format("Alarm %d @ %02d:%02d %s, repeats %s", i, alarm.getHour(), alarm.getMinute(), alarm.getEnabled() ? "active" : "inactive", getRepetitionDays(alarm.getRepetition())));
        }
        byte[] payload = buf.array();
        LOG.info("set alarms");
        writeToChunked(transactionBuilder, commandWithChecksum((byte) 2, payload));
        try {
            performConnected(transactionBuilder.getTransaction());
        } catch (Exception e) {
            LOG.error("setalarms failed");
        }
    }

    @Override // nodomain.freeyourgadget.gadgetbridge.devices.EventHandler
    public void onSetTime() {
        LOG.info("onSetTime");
        try {
            TransactionBuilder builder = performInitialized("SetTime");
            set_DateAndTime(builder);
            builder.queue(getQueue());
        } catch (IOException e) {
            LOG.warn(e.getMessage());
        }
    }

    @Override
    public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
        LOG.info("RSSI=" + rssi);
    }

    @Override // nodomain.freeyourgadget.gadgetbridge.devices.EventHandler
    public void onFindDevice(boolean start) {
        try {
            TransactionBuilder builder = performInitialized("FindDevice");
            builder.write(this.ctrlCharacteristic, commandWithChecksum((byte) 5, new byte[]{80, 0}));
            builder.queue(getQueue());
        } catch (Exception e) {
            LOG.warn(e.getMessage());
        }
        GB.toast(getContext(), "Your device will vibrate 4 times!", 1, 1);
    }

    @Override // nodomain.freeyourgadget.gadgetbridge.devices.EventHandler
    public void onNotification(NotificationSpec notificationSpec) {
        String notificationTitle = nodomain.freeyourgadget.gadgetbridge.util.StringUtils.getFirstOf(notificationSpec.sender, notificationSpec.title);
        LOG.info("onNotification: " + notificationTitle);
        byte icon;
        switch (notificationSpec.type) {
            case GENERIC_SMS:
            case GENERIC_EMAIL:
                icon = FunDoConstants.ICON_SMS;
                break;
            case FACEBOOK:
            case FACEBOOK_MESSENGER:
                icon = FunDoConstants.ICON_FACEBOOK;
                break;
            case TWITTER:
                icon = FunDoConstants.ICON_TWITTER;
                break;
            case WECHAT:
                icon = FunDoConstants.ICON_WECHAT;
                break;
            case WHATSAPP:
                icon = FunDoConstants.ICON_WHATSAPP;
                break;
            case SNAPCHAT:
                icon = FunDoConstants.ICON_SNAPCHAT;
                break;
            case INSTAGRAM:
                icon = FunDoConstants.ICON_INSTAGRAM;
                break;
            default:
                icon = FunDoConstants.ICON_APP;
                break;
        }

        String message = "";
        if (notificationSpec.title != null) {
            message = message + notificationSpec.title + ": ";
        }
        if (notificationSpec.body != null) {
            message = message + notificationSpec.body + ": ";
        }
        showNotification(icon, message);
    }


    @Override // nodomain.freeyourgadget.gadgetbridge.devices.EventHandler
    public void onDeleteNotification(int id) {
    }

    @Override // nodomain.freeyourgadget.gadgetbridge.devices.EventHandler
    public void onSetCallState(CallSpec callSpec) {
        if (callSpec.command == CallSpec.CALL_INCOMING) {
            String message = "";
            try {
                if (callSpec.name != null) {
                    message = message + callSpec.name + " / ";
                }
                if (callSpec.number != null) {
                    message = message + callSpec.number;
                }
                showNotification(FunDoConstants.ICON_CALL, message);
            } catch (Exception ex) {
                LOG.error("Unable to send call notification", ex);
            }
        }
    }

    @Override
    // you can enable/disable transmission of realtime step data; currently not used/saved
    public void onEnableRealtimeSteps(boolean enable) {
        LOG.info("onEnableRealtimeSteps: " + enable);
        try {
            TransactionBuilder builder = performInitialized("EnableRealtimeSteps");
            builder.notify(realtimeStepsCharacteristic, enable);
            builder.queue(getQueue());
        } catch (Exception e) {
            LOG.warn(e.getMessage());
        }

    }

    private void onReverseFindDevice(boolean start) {
        if (start) {
            SharedPreferences sharedPreferences = GBApplication.getDeviceSpecificSharedPrefs(
                    this.getDevice().getAddress());

            int findPhone = MakibesHR3Coordinator.getFindPhone(sharedPreferences);

            if (findPhone != MakibesHR3Coordinator.FindPhone_OFF) {
                GBDeviceEventFindPhone findPhoneEvent = new GBDeviceEventFindPhone();

                findPhoneEvent.event = GBDeviceEventFindPhone.Event.START;

                evaluateGBDeviceEvent(findPhoneEvent);

                if (findPhone > 0) {
                    this.mFindPhoneHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            onReverseFindDevice(false);
                        }
                    }, findPhone * 1000);
                }
            }
        } else {
            // Always send stop, ignore preferences.
            GBDeviceEventFindPhone findPhoneEvent = new GBDeviceEventFindPhone();

            findPhoneEvent.event = GBDeviceEventFindPhone.Event.STOP;

            evaluateGBDeviceEvent(findPhoneEvent);
        }
    }

    @Override // nodomain.freeyourgadget.gadgetbridge.devices.EventHandler
    public void onReset(int flags) {
        LOG.info("onReset: " + flags);
    }

    @Override // nodomain.freeyourgadget.gadgetbridge.devices.EventHandler
    public void onHeartRateTest() {
        LOG.info("onHeartRateTest");
    }

    @Override // nodomain.freeyourgadget.gadgetbridge.devices.EventHandler
    public void onEnableRealtimeHeartRateMeasurement(boolean enable) {
        LOG.info("onEnableRealtimeHeartRateMeasurement: " + enable);
    }

    @Override // nodomain.freeyourgadget.gadgetbridge.devices.EventHandler
    public void onSetConstantVibration(int integer) {
    }

    @Override // nodomain.freeyourgadget.gadgetbridge.devices.EventHandler
    public void onSetCannedMessages(CannedMessagesSpec cannedMessagesSpec) {
        LOG.info("onSetCannedMessages: " + cannedMessagesSpec.cannedMessages.toString());
    }

    @Override // nodomain.freeyourgadget.gadgetbridge.devices.EventHandler
    public void onSetMusicState(MusicStateSpec stateSpec) {
    }

    @Override // nodomain.freeyourgadget.gadgetbridge.devices.EventHandler
    public void onSetMusicInfo(MusicSpec musicSpec) {
    }

    @Override // nodomain.freeyourgadget.gadgetbridge.devices.EventHandler
    public void onInstallApp(Uri uri) {
    }

    @Override // nodomain.freeyourgadget.gadgetbridge.devices.EventHandler
    public void onAppInfoReq() {
    }

    @Override // nodomain.freeyourgadget.gadgetbridge.devices.EventHandler
    public void onAppStart(UUID uuid, boolean start) {
        LOG.info("onAppStart: " + uuid + " start: " + start);
    }

    @Override // nodomain.freeyourgadget.gadgetbridge.devices.EventHandler
    public void onAppDelete(UUID uuid) {
    }

    @Override // nodomain.freeyourgadget.gadgetbridge.devices.EventHandler
    public void onAppConfiguration(UUID appUuid, String config, Integer id) {
        LOG.info("onappConfiguration: " + config);
    }

    @Override // nodomain.freeyourgadget.gadgetbridge.devices.EventHandler
    public void onAppReorder(UUID[] uuids) {
    }

    @Override // nodomain.freeyourgadget.gadgetbridge.devices.EventHandler
    public void onFetchRecordedData(int dataTypes) {
        LOG.info("fetch recorded data: " + dataTypes);
        //todo: query last sync time and only request newer data
        Long lastSyncSleep = getLastSuccessfulSyncTime(FunDoConstants.PREF_FunDoHistorySleepSyncTime).getTimeInMillis();
        Long lastSyncHeart = getLastSuccessfulSyncTime(FunDoConstants.PREF_FunDoHistoryHeartSyncTime).getTimeInMillis();
        Long lastSyncSteps = getLastSuccessfulSyncTime(FunDoConstants.PREF_FunDoHistoryStepsSyncTime).getTimeInMillis();
        Long now = Calendar.getInstance(Locale.ENGLISH).getTimeInMillis();
        //todo: sollten wir den Zeitstempel des letzten syncs weitergeben oder gleich ein Startdatum für den Abruf generieren?
        long daystofetch = Math.min(TimeUnit.DAYS.convert((now - lastSyncSleep), TimeUnit.MILLISECONDS), FunDoConstants.FunDoHistoryDaysToFetch);
        if (daystofetch > 0) {
            LOG.info("Days to fetch sleep: " + daystofetch);
            days_to_fetch_SleepData = (int) daystofetch;//FunDoConstants.FunDoHistoryDaysToFetch;
        }

        daystofetch = Math.min(TimeUnit.DAYS.convert((now - lastSyncHeart), TimeUnit.MILLISECONDS), FunDoConstants.FunDoHistoryDaysToFetch); //always query at least one day
        if (daystofetch > 0) {
            LOG.info("Days to fetch HR : " + daystofetch);
            days_to_fetch_HeartRateData = (int) daystofetch;//FunDoConstants.FunDoHistoryDaysToFetch;
        }
        daystofetch = Math.min(TimeUnit.DAYS.convert((now - lastSyncSteps), TimeUnit.MILLISECONDS), FunDoConstants.FunDoHistoryDaysToFetch);
        if (daystofetch > 0) {
            LOG.info("Days to fetch steps: " + daystofetch);
            days_to_fetch_StepsData = (int) daystofetch;//FunDoConstants.FunDoHistoryDaysToFetch;
        }
        //days_to_fetch_StepsData = FunDoConstants.FunDoHistoryDaysToFetch;
        //days_to_fetch_HeartRateData = FunDoConstants.FunDoHistoryDaysToFetch;
        queryHistoricData();
    }


    @Override // nodomain.freeyourgadget.gadgetbridge.devices.EventHandler
    public void onScreenshotReq() {
    }

    @Override // nodomain.freeyourgadget.gadgetbridge.devices.EventHandler
    public void onEnableHeartRateSleepSupport(boolean enable) {
        LOG.info("onEnableHeartRateSleepSupport " + enable);
    }

    @Override // nodomain.freeyourgadget.gadgetbridge.devices.EventHandler
    public void onSetHeartRateMeasurementInterval(int seconds) {
        LOG.info("onSetHeartRateMeasurementInterval " + seconds);
    }

    private void setInactivityAlert(TransactionBuilder builder) {
        LOG.info(String.format("setInactivityAlert"));
        Prefs prefs = GBApplication.getPrefs();
        boolean enabled = prefs.getBoolean(FunDoConstants.PREF_INACTIVITY_ENABLE, false);
        int interval = prefs.getInt(FunDoConstants.PREF_INACTIVITY_THRESHOLD, 60);
        String start = prefs.getString(FunDoConstants.PREF_INACTIVITY_START, "08:00");
        String end = prefs.getString(FunDoConstants.PREF_INACTIVITY_END, "16:00");
        DateFormat hr_start = new SimpleDateFormat("HH:mm");
        DateFormat hr_end = new SimpleDateFormat("HH:mm");
        Calendar calendar = GregorianCalendar.getInstance();
        Calendar calendar_end = GregorianCalendar.getInstance();
        try {
            calendar.setTime(hr_start.parse(start));
            calendar_end.setTime(hr_end.parse(end));
            int reps = 0 | (prefs.getBoolean(FunDoConstants.PREF_INACTIVITY_MO, false) ? 1 : 0) | ((prefs.getBoolean(FunDoConstants.PREF_INACTIVITY_TU, false) ? 1 : 0) << 1) | ((prefs.getBoolean(FunDoConstants.PREF_INACTIVITY_WE, false) ? 1 : 0) << 2) | ((prefs.getBoolean(FunDoConstants.PREF_INACTIVITY_TH, false) ? 1 : 0) << 3) | ((prefs.getBoolean(FunDoConstants.PREF_INACTIVITY_FR, false) ? 1 : 0) << 4) | ((prefs.getBoolean(FunDoConstants.PREF_INACTIVITY_SA, false) ? 1 : 0) << 5) | ((prefs.getBoolean(FunDoConstants.PREF_INACTIVITY_SU, false) ? 1 : 0) << 6);
            ByteBuffer buf = ByteBuffer.allocate(8 + 3);
            buf.order(ByteOrder.BIG_ENDIAN);
            buf.put((byte) 37);
            buf.putShort((short) 8);
            buf.put(enabled ? (byte) 1 : 0);

            buf.put((byte) calendar.get(Calendar.HOUR_OF_DAY));
            buf.put((byte) calendar.get(Calendar.MINUTE));
            buf.put((byte) calendar_end.get(Calendar.HOUR_OF_DAY));
            buf.put((byte) calendar_end.get(Calendar.MINUTE));
            buf.put((byte) reps);
            buf.put((byte) (interval & 0xFF));
            buf.put((byte) 0);
            writeToChunked(builder, commandWithChecksum((byte) 2, buf.array()));
        } catch (Exception e) {

            LOG.error("Unexpected exception in FunDoDeviceSupport.setInactivityAlert: " + e.getMessage());
        }
    }

    private void setWaterAlert(TransactionBuilder builder) {
        LOG.info(String.format("setWaterAlert"));
        Prefs prefs = GBApplication.getPrefs();
        boolean enabled = prefs.getBoolean(FunDoConstants.PREF_WATER_ENABLE, false);
        int interval = prefs.getInt(FunDoConstants.PREF_WATER_THRESHOLD, 60);
        String start = prefs.getString(FunDoConstants.PREF_WATER_START, "08:00");
        String end = prefs.getString(FunDoConstants.PREF_WATER_END, "16:00");
        DateFormat hr_start = new SimpleDateFormat("HH:mm");
        DateFormat hr_end = new SimpleDateFormat("HH:mm");
        Calendar calendar = GregorianCalendar.getInstance();
        Calendar calendar_end = GregorianCalendar.getInstance();
        try {
            calendar.setTime(hr_start.parse(start));
            calendar_end.setTime(hr_end.parse(end));
            int reps = 0 | (prefs.getBoolean(FunDoConstants.PREF_WATER_MO, false) ? 1 : 0) | ((prefs.getBoolean(FunDoConstants.PREF_WATER_TU, false) ? 1 : 0) << 1) | ((prefs.getBoolean(FunDoConstants.PREF_WATER_WE, false) ? 1 : 0) << 2) | ((prefs.getBoolean(FunDoConstants.PREF_WATER_TH, false) ? 1 : 0) << 3) | ((prefs.getBoolean(FunDoConstants.PREF_WATER_FR, false) ? 1 : 0) << 4) | ((prefs.getBoolean(FunDoConstants.PREF_WATER_SA, false) ? 1 : 0) << 5) | ((prefs.getBoolean(FunDoConstants.PREF_WATER_SU, false) ? 1 : 0) << 6);
            ByteBuffer buf = ByteBuffer.allocate(8 + 3);
            buf.order(ByteOrder.BIG_ENDIAN);
            buf.put((byte) 40);
            buf.putShort((short) 8);
            buf.put(enabled ? (byte) 1 : 0);
            try {
                buf.put((byte) calendar.get(Calendar.HOUR_OF_DAY));
                buf.put((byte) calendar.get(Calendar.MINUTE));
                buf.put((byte) calendar_end.get(Calendar.HOUR_OF_DAY));
                buf.put((byte) calendar_end.get(Calendar.MINUTE));
                buf.put((byte) reps);
                buf.put((byte) (interval & 0xFF));
                buf.put((byte) 0);
                try {
                    writeToChunked(builder, commandWithChecksum((byte) 2, buf.array()));
                } catch (Exception e) {
                    e = e;
                }
            } catch (Exception e) {

                LOG.error("Unexpected exception in FunDoDeviceSupport.setWaterAlert: " + e.getMessage());
            }
        } catch (Exception e) {

            LOG.error("Unexpected exception in FunDoDeviceSupport.setWaterAlert: " + e.getMessage());
        }
    }

    @Override // nodomain.freeyourgadget.gadgetbridge.devices.EventHandler
    public void onAddCalendarEvent(CalendarEventSpec calendarEventSpec) {
        LOG.info("onAddCalendarEvent " + calendarEventSpec);
    }

    @Override // nodomain.freeyourgadget.gadgetbridge.devices.EventHandler
    public void onDeleteCalendarEvent(byte type, long id) {
        LOG.info("onDeleteCalendarEvent " + id);
    }

    @Override // nodomain.freeyourgadget.gadgetbridge.devices.EventHandler
    public void onSendConfiguration(String config) {
        LOG.info("onSendConfig: " + config);
        try {
            TransactionBuilder builder = performInitialized("sendConfiguration");

            switch (config) {
                case FunDoConstants.PREF_HANDMOVE_DISPLAY:
                    setDisplayOnMovement(builder);
                    break;
                case FunDoConstants.PREF_DO_NOT_DISTURB:
                case FunDoConstants.PREF_DO_NOT_DISTURB_START:
                case FunDoConstants.PREF_DO_NOT_DISTURB_END:
                    setDoNotDisturb(builder);
                    break;
                case FunDoConstants.PREF_ACTIVITY_TRACKING:
                case FunDoConstants.PREF_INACTIVITY_ENABLE:
                case FunDoConstants.PREF_INACTIVITY_MO:
                case FunDoConstants.PREF_INACTIVITY_TU:
                case FunDoConstants.PREF_INACTIVITY_WE:
                case FunDoConstants.PREF_INACTIVITY_TH:
                case FunDoConstants.PREF_INACTIVITY_FR:
                case FunDoConstants.PREF_INACTIVITY_SA:
                case FunDoConstants.PREF_INACTIVITY_SU:
                case FunDoConstants.PREF_INACTIVITY_START:
                case FunDoConstants.PREF_INACTIVITY_END:
                case FunDoConstants.PREF_INACTIVITY_THRESHOLD:
                case FunDoConstants.PREF_INACTIVITY_SIGNALING:
                    setInactivityAlert(builder);
                    break;
                case FunDoConstants.PREF_WATER_ENABLE:
                case FunDoConstants.PREF_WATER_START:
                case FunDoConstants.PREF_WATER_END:
                case FunDoConstants.PREF_WATER_MO:
                case FunDoConstants.PREF_WATER_TU:
                case FunDoConstants.PREF_WATER_WE:
                case FunDoConstants.PREF_WATER_TH:
                case FunDoConstants.PREF_WATER_FR:
                case FunDoConstants.PREF_WATER_THRESHOLD:
                    setWaterAlert(builder);
                    break;
                case FunDoConstants.PREF_FUNDO_HEARTRATE_INTERVAL:
                    //case FunDoConstants.PREF_FUNDO_HEARTRATE_MIN:
                    //case FunDoConstants.PREF_FUNDO_HEARTRATE_MAX:
                case FunDoConstants.PREF_FUNDO_HEARTRATE_TIME_START:
                case FunDoConstants.PREF_FUNDO_HEARTRATE_TIME_STOP:
                    setHeartRateLimits(builder);
                    break;
                //case FunDoConstants.S:
                //    setStepGoal(builder);
                //    break;
                default:
                    LOG.warn("found unhandled preference setting:" + config);
                    break;
            }
            builder.queue(getQueue());
        } catch (IOException e) {
            Context context = getContext();
            GB.toast(context, "Error sending configuration: " + e.getLocalizedMessage(), 1, 3);
        }
    }

    private void setStepGoal(TransactionBuilder builder) {
        LOG.info(String.format("setStepGoal"));
        int steps = GBApplication.getPrefs().getInt(FunDoConstants.CONFIG_ITEM_STEP_GOAL, 10000);
        try {
            ByteBuffer buf = ByteBuffer.allocate(4 + 3);
            buf.order(ByteOrder.BIG_ENDIAN);
            buf.put((byte) 34);
            buf.putShort((short) 4);
            buf.putShort((short) steps);
            buf.putShort((short) 0);
            writeToChunked(builder, commandWithChecksum((byte) 2, buf.array()));
        } catch (Exception e) {
            LOG.error("Unexpected exception in FunDoDeviceSupport.setStepGoal: " + e.getMessage());
        }
    }

    private void setDisplayOnMovement(TransactionBuilder builder) {
        boolean movement = GBApplication.getPrefs().getBoolean(FunDoConstants.PREF_HANDMOVE_DISPLAY, false);
        try {
            ByteBuffer buf = ByteBuffer.allocate(8);
            buf.order(ByteOrder.BIG_ENDIAN);
            short len = 3;
            buf.put((byte) 74);
            buf.putShort(len);
            if (movement) {
                buf.put((byte) 1);
                buf.put((byte) 1);
                buf.put((byte) 1);
            } else {
                buf.put((byte) 0);
                buf.put((byte) 0);
                buf.put((byte) 0);
            }
            writeToChunked(builder, commandWithChecksum((byte) 4, buf.array()));
        } catch (Exception e) {
            LOG.warn(e.getMessage());
        }
        GB.toast(getContext(), "set hand movement action to device", 1, 1);
    }

    private void setDoNotDisturb(TransactionBuilder builder) {
        Prefs prefs = GBApplication.getPrefs();
        String scheduled = prefs.getString(FunDoConstants.PREF_DO_NOT_DISTURB, "off");
        String dndScheduled = getContext().getString(R.string.p_scheduled);
        String start = prefs.getString(FunDoConstants.PREF_DO_NOT_DISTURB_START, "22:00");
        String end = prefs.getString(FunDoConstants.PREF_DO_NOT_DISTURB_END, "07:00");
        DateFormat df_start = new SimpleDateFormat("HH:mm");
        DateFormat df_end = new SimpleDateFormat("HH:mm");
        Calendar calendar = GregorianCalendar.getInstance();
        Calendar calendar_end = GregorianCalendar.getInstance();
        try {
            calendar.setTime(df_start.parse(start));

            calendar_end.setTime(df_end.parse(end));
            ByteBuffer buf = ByteBuffer.allocate(8);
            buf.order(ByteOrder.BIG_ENDIAN);
            buf.put((byte) 100);
            short len = 5;
            buf.putShort(len);
            if (scheduled.equals(dndScheduled)) {
                buf.put((byte) 1);
            } else {
                buf.put((byte) 0);
            }
            buf.put((byte) calendar.get(Calendar.HOUR_OF_DAY));
            buf.put((byte) calendar.get(Calendar.MINUTE));
            buf.put((byte) calendar_end.get(Calendar.HOUR_OF_DAY));
            buf.put((byte) calendar_end.get(Calendar.MINUTE));
            LOG.info("set PersonalInformation");
            writeToChunked(builder, commandWithChecksum((byte) 6, buf.array()));
        } catch (Exception e) {
            LOG.error("Unexpected exception in FunDo.setDoNotDisturb: " + e.getMessage());
        }
    }

    //todo: get data from preferences
    private void sendPersonalInformation(int age, int year_of_birth, int gender, int height, int weight) {
        TransactionBuilder transactionBuilder = createTransactionBuilder("setPersonalInformation");
        ByteBuffer buf = ByteBuffer.allocate(11);
        buf.order(ByteOrder.BIG_ENDIAN);
        buf.put((byte) 35);
        short len = 8;
        buf.putShort(len);
        buf.put((byte) gender);
        buf.put((byte) (age & 0xFF));
        buf.put((byte) (height & 0xFF));
        buf.put((byte) (year_of_birth & 0xFF));
        buf.put((byte) ((weight / 2) & 0xFF));
        buf.put((byte) 13);//todo: what does it mean?
        buf.put((byte) 0);
        buf.put((byte) 0);
        LOG.info("set PersonalInformation");
        writeToChunked(transactionBuilder, commandWithChecksum((byte) 2, buf.array()));
        try {
            performConnected(transactionBuilder.getTransaction());
        } catch (Exception e) {
            LoggerFactory.getLogger(getClass()).error("set PersonalInformation failed");
        }
    }

    @Override // called from preferences activity
    public void onReadConfiguration(String config) {
        LOG.info("onReadConfiguration:" + config);
        getConfig();
    }

    private void getConfig() {
        try {
            TransactionBuilder builder = performInitialized("requestConfig");
            builder.write(this.ctrlCharacteristic, commandWithChecksum((byte) 2, new byte[]{0x2e, 0}));
            builder.queue(getQueue());
        } catch (Exception e) {
            LOG.warn(e.getMessage());
        }
    }

    @Override // nodomain.freeyourgadget.gadgetbridge.devices.EventHandler
    public void onSendWeather(WeatherSpec weatherSpec) {
        setWeather(weatherSpec);
    }

    private void setWeather(WeatherSpec weatherSpec) {
        try {
            short len = 8;
            ByteBuffer buf = ByteBuffer.allocate(len + 3);
            buf.order(ByteOrder.BIG_ENDIAN);
            buf.put(FunDoConstants.SCMD_SET_WEATHER);
            buf.putShort(len);
            buf.put((byte) weatherSpec.todayMinTemp);
            buf.put((byte) weatherSpec.todayMaxTemp);
            buf.put((byte) weatherSpec.currentConditionCode);
            int i = 0;
            Iterator<WeatherSpec.Forecast> it = weatherSpec.forecasts.iterator();
            while (true) {
                if (!it.hasNext()) {
                    break;
                }
                WeatherSpec.Forecast forecast = it.next();
                buf.put((byte) ((forecast.minTemp - 273) & 0xFF));
                buf.put((byte) ((forecast.maxTemp - 273) & 0xFF));
                buf.put((byte) 1);
                i++;
                if (i > 1) {
                    break;
                }
            }

            TransactionBuilder builder = performInitialized("SendWeather");
            writeToChunked(builder, commandWithChecksum((byte) 3, buf.array()));
            builder.queue(getQueue());
        } catch (Exception e) {
            LOG.warn(e.getMessage());
        }
        GB.toast(getContext(), "send weather forecast to device", 1, 1);
    }

    @Override // nodomain.freeyourgadget.gadgetbridge.devices.EventHandler
    public void onTestNewFunction() {
        //showNotification((byte) 1, "test");
        //syncData(2, 0);

    }

    private void writeToChunked(TransactionBuilder builder, byte[] data) {
        int remaining = data.length;
        byte count = 0;
        LOG.info("writeChunked: " + GB.hexdump(data, 0, data.length));
        logMessageContent("Sen", data);
        while (remaining > 0) {
            int copybytes = Math.min(remaining, maxMsgLength);
            byte[] chunk = new byte[copybytes];
            System.arraycopy(data, count * maxMsgLength, chunk, 0, copybytes);
            LOG.info("writeChunked: " + GB.hexdump(chunk, 0, chunk.length));
            builder.write(this.ctrlCharacteristic, chunk);
            remaining -= copybytes;
            count = (byte) (count + 1);
        }
    }

    private void showNotification(byte icon, String message) {
        try {
            TransactionBuilder builder = performInitialized("ShowNotification");
            byte[] messageBytes = stringToUTF16Bytes(message, 80);
            ByteBuffer buf = ByteBuffer.allocate(messageBytes.length + 4);
            buf.order(ByteOrder.BIG_ENDIAN);
            buf.put(FunDoConstants.SCMD_SEND_NOTIFICATION);
            buf.putShort((short) (messageBytes.length + 1));
            buf.put(icon);
            buf.put(messageBytes);
            LOG.info(String.format("ShowNotification: %02x subcmd %02x, %s", (byte) 6, FunDoConstants.SCMD_SEND_NOTIFICATION, message));
            writeToChunked(builder, commandWithChecksum((byte) 6, buf.array()));
            builder.queue(getQueue());
        } catch (IOException e) {
            LOG.warn(e.getMessage());
        }
    }

    private void sendShortCMD(TransactionBuilder builder, byte cmd, byte subcmd) {
        LOG.info(String.format("sendShortCMD: %02x subcmd %02x", Byte.valueOf(cmd), Byte.valueOf(subcmd)));
        writeToChunked(builder, commandWithChecksum(cmd, new byte[]{subcmd, 0, 0}));
    }

    private void getFirmwareVersion(TransactionBuilder builder) {
        LOG.info("get firmware version");
        LOG.info(String.format("sendShortCMD: %02x subcmd %02x", (byte) 1, (byte) 18));
        writeToChunked(builder, commandWithChecksum((byte) 1, new byte[]{18, 0, 0}));
    }


    private void set_lang_timeoff(TransactionBuilder builder) {
        //"borrowed" from nodomain/freeyourgadget/gadgetbridge/service/devices/zetime/ZeTimeDeviceSupport.java
        String localeString = GBApplication.getDeviceSpecificSharedPrefs(gbDevice.getAddress()).getString(DeviceSettingsPreferenceConst.PREF_LANGUAGE, "auto");
        if (localeString == null || localeString.equals("auto")) {
            String language = Locale.getDefault().getLanguage();
            String country = Locale.getDefault().getCountry();

            if (country == null) {
                // sometimes country is null, no idea why, guess it.
                country = language;
            }
            localeString = language + "_" + country.toUpperCase();
        }
        byte time_offset = 60; //todo: get from preferences too
        byte cmd = 2;
        short len = 4;
        ByteBuffer buf = ByteBuffer.allocate(len + 3);
        buf.order(ByteOrder.BIG_ENDIAN);
        buf.put(FunDoConstants.SCMD_SET_LANGUAGE);
        buf.putShort(len);

        switch (localeString.substring(0, 2)) {
            case "zh":
                if (localeString.equals("zh_CN")) {
                    buf.put((byte) 0);
                } else {
                    buf.put((byte) 2);//??
                }
                break;
            case "fr":
                buf.put((byte) 3);
                break;
            case "de":
                buf.put((byte) 4);
                break;
            case "en":
            default:
                buf.put((byte) 1);
        }
        buf.put((byte) 0);
        buf.put(time_offset);
        buf.put((byte) 0);
        byte[] payload = buf.array();
        LOG.info(String.format("setLanguage_Timeoffset; locale=%s", localeString));
        writeToChunked(builder, commandWithChecksum((byte) cmd, payload));
    }

    private void setHeartRateLimits(TransactionBuilder builder) {
        LOG.info(String.format("setHeartRateLimits"));
        Prefs prefs = GBApplication.getPrefs();
        int interval = prefs.getInt(FunDoConstants.PREF_FUNDO_HEARTRATE_INTERVAL, 5);//"fundo_heartrate_interval", 5);
        String start = prefs.getString(FunDoConstants.PREF_FUNDO_HEARTRATE_TIME_START, "00:00");
        String end = prefs.getString(FunDoConstants.PREF_FUNDO_HEARTRATE_TIME_STOP, "00:00");
        DateFormat hr_start = new SimpleDateFormat("HH:mm");
        DateFormat hr_end = new SimpleDateFormat("HH:mm");
        Calendar calendar = GregorianCalendar.getInstance();
        Calendar calendar_end = GregorianCalendar.getInstance();
        try {
            calendar.setTime(hr_start.parse(start));
            calendar_end.setTime(hr_end.parse(end));
            set_heartratemeasurements(builder, (byte) calendar.get(Calendar.HOUR_OF_DAY), (byte) calendar.get(Calendar.MINUTE), (byte) calendar_end.get(Calendar.HOUR_OF_DAY), (byte) calendar_end.get(Calendar.MINUTE), (byte) (interval & 0xFF), interval > 0 ? (byte) 1 : 0);
        } catch (Exception e) {
            LOG.error("Unexpected exception in FunDoDeviceSupport.setHeartRateLimits: " + e.getMessage());
        }
    }

    private void set_heartratemeasurements(TransactionBuilder builder, byte shour, byte smin, byte ehour, byte emin, byte interval, byte active) {
        ByteBuffer buf = ByteBuffer.allocate(10);
        buf.order(ByteOrder.BIG_ENDIAN);
        byte cmd = 0x09;
        byte subcmd = FunDoConstants.SCMD_GET_HEARTRATE;
        buf.put(subcmd);
        buf.putShort((short) 8);
        buf.put(active);
        buf.put(shour);
        buf.put(smin);
        buf.put(ehour);
        buf.put(emin);
        buf.put(interval);
        buf.put((byte) 0);
        writeToChunked(builder, commandWithChecksum(cmd, buf.array()));
        LOG.info(String.format("Set Heartrate measurements from %02d:%02d to %02d:%02d every %d minutes", Byte.valueOf(shour), Byte.valueOf(smin), Byte.valueOf(ehour), Byte.valueOf(emin), Byte.valueOf(interval)));
    }

    private void set_DateAndTime(TransactionBuilder builder) {
        LOG.info("Sync Date&Time");
        Calendar cal = Calendar.getInstance();
        ByteBuffer buf = ByteBuffer.allocate(10);
        buf.order(ByteOrder.BIG_ENDIAN);
        byte cmd = 0x02;
        byte subcmd = FunDoConstants.SCMD_SET_TIME;
        buf.put(subcmd);
        buf.putShort((short) 7);
        buf.put((byte) ((cal.get(Calendar.YEAR) - 2000) & 0xFF));
        buf.put((byte) ((cal.get(Calendar.MONTH) + 1) & 0xFF));
        buf.put((byte) (cal.get(Calendar.DAY_OF_MONTH) & 0xFF));
        buf.put((byte) (cal.get(Calendar.HOUR_OF_DAY) & 0xFF));
        buf.put((byte) (cal.get(Calendar.MINUTE) & 0xFF));
        buf.put((byte) (cal.get(Calendar.SECOND) & 0xFF));
        buf.put((byte) 0);
        writeToChunked(builder, commandWithChecksum(cmd, buf.array()));
    }

    private void syncData(int type, int daysback) {

        short len = 7;
        ByteBuffer buf = ByteBuffer.allocate(len + 3);
        buf.order(ByteOrder.BIG_ENDIAN);
        byte cmd = 0x0a;
        byte subcmd = FunDoConstants.SCMD_GET_DATA;
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, daysback * -1);
        buf.put(subcmd);
        buf.putShort(len);
        buf.put((byte) (type & 0xFF));
        buf.put((byte) ((cal.get(Calendar.YEAR) - 2000) & 0xFF));
        buf.put((byte) ((cal.get(Calendar.MONTH) + 1) & 0xFF));
        buf.put((byte) (cal.get(Calendar.DAY_OF_MONTH) & 0xFF));
        buf.put((byte) 0); //always noon
        buf.put((byte) 0);
        buf.put((byte) 0);
       /* buf.put((byte) (cal.get(Calendar.HOUR_OF_DAY)&0xFF)); //always noon
        buf.put((byte) (cal.get(Calendar.MINUTE)&0xFF));
        buf.put((byte) (cal.get(Calendar.SECOND)&0xFF));*/
        try {
            String typestring = "";
            switch (type) {
                case 1:
                    typestring = "sleep";
                    break;
                case 2:
                    typestring = "heart rate";
                    break;
                case 3:
                    typestring = "step";
                    break;
                default:
                    typestring = "unknown";
            }
            TransactionBuilder builder = performInitialized("fetch_" + typestring + "_data");

            LOG.info(String.format("syncData: cmd %02x, subcmd %02x, Type = %d (%s history), Requested Date=%04d-%02d-%02d", cmd, subcmd, Integer.valueOf(type), typestring, Integer.valueOf(cal.get(Calendar.YEAR)), Integer.valueOf(cal.get(Calendar.MONTH) + 1), Integer.valueOf(cal.get(Calendar.DAY_OF_MONTH))));
            writeToChunked(builder, commandWithChecksum((byte) cmd, buf.array()));
            builder.queue(getQueue());
        } catch (IOException e) {
            GB.toast(getContext(), "Error fetching historic data: " + e.getLocalizedMessage(), Toast.LENGTH_LONG, GB.ERROR);
        }
    }


    private byte[] commandWithChecksum(byte cmd, byte[] data) {
        int ld = data.length + 2;
        ByteBuffer buf = ByteBuffer.allocate(ld + 8);
        buf.order(ByteOrder.BIG_ENDIAN);
        buf.put(new byte[]{(byte) (0xBA & 0xFF), 0x20});
        buf.putShort((short) ld);
        buf.putShort((short) 0);
        this.seq += 1; //todo:possible overrun?
        buf.putShort(this.seq);
        buf.put(cmd);
        buf.put((byte) 0);
        buf.put(data);
        byte[] bytesToWrite = buf.array();
        byte[] bytestocrc = Arrays.copyOfRange(bytesToWrite, 8, bytesToWrite.length);
        bytesToWrite[5] = calculateCRC8(bytestocrc, bytestocrc.length);
        return bytesToWrite;
    }

    private byte[] stringToUTF8Bytes(String src, int byteCount) {
        if (src == null) {
            return null;
        }
        for (int i = src.length(); i > 0; i--) {
            byte[] subUTF8 = src.substring(0, i).getBytes(StandardCharsets.UTF_8);
            if (subUTF8.length == byteCount) {
                return subUTF8;
            }
            if (subUTF8.length < byteCount) {
                byte[] largerSubUTF8 = new byte[byteCount];
                System.arraycopy(subUTF8, 0, largerSubUTF8, 0, subUTF8.length);
                return largerSubUTF8;
            }
        }
        return null;
    }

    private byte[] stringToUTF16Bytes(String src, int byteCount) {
        if (src == null) {
            return null;
        }
        for (int i = src.length(); i > 0; i--) {
            byte[] subUTF16 = src.substring(0, i).getBytes(StandardCharsets.UTF_16LE);
            if (subUTF16.length <= byteCount) {
                return subUTF16;
            }
            if (subUTF16.length > byteCount) {
                byte[] largerSubUTF16 = new byte[byteCount];
                System.arraycopy(subUTF16, 0, largerSubUTF16, 0, byteCount);
                return largerSubUTF16;
            }
        }
        return null;
    }
}
