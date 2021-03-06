package com.tuya.smart.android.demo.config;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Message;
import android.widget.Toast;

import com.tuya.smart.android.common.utils.L;
import com.tuya.smart.android.demo.R;
import com.tuya.smart.android.demo.base.activity.BrowserActivity;
import com.tuya.smart.android.demo.base.utils.ActivityUtils;
import com.tuya.smart.android.demo.base.utils.ProgressUtil;
import com.tuya.smart.android.demo.family.FamilyManager;
import com.tuya.smart.android.device.utils.WiFiUtil;
import com.tuya.smart.android.mvp.bean.Result;
import com.tuya.smart.android.mvp.presenter.BasePresenter;
import com.tuya.smart.home.sdk.TuyaHomeSdk;
import com.tuya.smart.sdk.api.ITuyaActivatorGetToken;
import com.tuya.smart.sdk.bean.DeviceBean;


/**
 * Created by letian on 16/1/8.
 */
public class ECBindPresenter extends BasePresenter {

    private static final String TAG = "ECBindPresenter";
    private static final int MESSAGE_SHOW_SUCCESS_PAGE = 1001;
    private final Context mContext;
    private final IECBindView mView;

    private static final int MESSAGE_CONFIG_WIFI_OUT_OF_TIME = 0x16;
    private final int mConfigMode;
    private int mTime;
    private boolean mStop;
    private DeviceBindModel mModel;
    private final String mPassWord;
    private final String mSSId;
    private boolean mBindDeviceSuccess;

    public ECBindPresenter(Context context, IECBindView view) {
        super(context);
        mContext = context;
        mView = view;
        mConfigMode = ((Activity) mContext).getIntent().getIntExtra(ECActivity.CONFIG_MODE, ECActivity.EC_MODE);
        mModel = new DeviceBindModel(context, mHandler);
        mPassWord = ((Activity) mContext).getIntent().getStringExtra(ECActivity.CONFIG_PASSWORD);
        mSSId = ((Activity) mContext).getIntent().getStringExtra(ECActivity.CONFIG_SSID);
        showConfigDevicePage();
        getTokenForConfigDevice();
    }

    private void showConfigDevicePage() {
        if (mConfigMode == ECActivity.EC_MODE) {
            mView.hideSubPage();
        } else {
            mView.hideMainPage();
            mView.showSubPage();
        }
    }

    private void getTokenForConfigDevice() {
        ProgressUtil.showLoading(mContext, R.string.loading);
        long homeId = FamilyManager.getInstance().getCurrentHomeId();
        TuyaHomeSdk.getActivatorInstance().getActivatorToken(homeId, new ITuyaActivatorGetToken() {
            @Override
            public void onSuccess(String token) {
                ProgressUtil.hideLoading();
                initConfigDevice(token);
            }

            @Override
            public void onFailure(String s, String s1) {
                ProgressUtil.hideLoading();
                if (mConfigMode == ECActivity.EC_MODE) {
                    mView.showNetWorkFailurePage();
                }
            }
        });

    }

    private void initConfigDevice(String token) {
        if (mConfigMode == ECActivity.EC_MODE) {
            mModel.setEC(mSSId, mPassWord, token);
            startSearch();
        } else if(mConfigMode == ECActivity.AP_MODE){
            mModel.setAP(mSSId, mPassWord, token);
        }
    }

    public void startSearch() {
        mModel.start();
        mView.showConnectPage();
        mBindDeviceSuccess = false;
        startLoop();
    }

    private void startLoop() {
        mTime = 0;
        mStop = false;
        mHandler.sendEmptyMessage(MESSAGE_CONFIG_WIFI_OUT_OF_TIME);
    }

    /**
     * ??????????????????
     */
    public void reStartEZConfig() {
        //ap????????????,??????????????????EC?????????
        if (mConfigMode == ECActivity.AP_MODE) {
            ActivityUtils.startActivity((Activity) mContext, new Intent(mContext, AddDeviceTipActivity.class), ActivityUtils.ANIMATE_FORWARD, true);
        } else {
            //EZ???????????????token????????????????????????????????????????????????
            goToEZActivity();
        }
    }

    private void goToEZActivity() {
        Intent intent = new Intent(mContext, ECActivity.class);
        ActivityUtils.startActivity((Activity) mContext, intent, ActivityUtils.ANIMATE_FORWARD, true);
    }

    @Override
    public boolean handleMessage(Message msg) {
        switch (msg.what) {
            case MESSAGE_SHOW_SUCCESS_PAGE:
                mView.showSuccessPage();
                break;
            case MESSAGE_CONFIG_WIFI_OUT_OF_TIME:
                checkLoop();
                break;
            //????????????????????????
            case DeviceBindModel.WHAT_EC_GET_TOKEN_ERROR:            //??????token??????
                stopSearch();
                mView.showNetWorkFailurePage();
                break;
            //ec????????????
            case DeviceBindModel.WHAT_EC_ACTIVE_ERROR:
                L.d(TAG, "ec_active_error");
                stopSearch();
                if (mBindDeviceSuccess) {
                    mView.showBindDeviceSuccessFinalTip();
                    break;
                }
                mView.showFailurePage();
                break;

            //AP????????????
            case DeviceBindModel.WHAT_AP_ACTIVE_ERROR:
                L.d(TAG, "ap_active_error");
                stopSearch();
                if (mBindDeviceSuccess) {
                    mView.showBindDeviceSuccessFinalTip();
                    break;
                }
                mView.showFailurePage();
                String currentSSID = WiFiUtil.getCurrentSSID(mContext);
                if (BindDeviceUtils.isAPMode())
                    WiFiUtil.removeNetwork(mContext, currentSSID);
                break;

            case DeviceBindModel.WHAT_EC_ACTIVE_SUCCESS:  //EC????????????
            case DeviceBindModel.WHAT_AP_ACTIVE_SUCCESS:  //AP????????????
                L.d(TAG, "active_success");
                DeviceBean configDev = (DeviceBean) ((Result)msg.obj).getObj();
                stopSearch();
                configSuccess(configDev);
                break;

            case DeviceBindModel.WHAT_DEVICE_FIND:
                L.d(TAG, "device_find");
                deviceFind((String) ((Result) (msg.obj)).getObj());
                break;
            case DeviceBindModel.WHAT_BIND_DEVICE_SUCCESS:
                L.d(TAG, "bind_device_success");
                bindDeviceSuccess(((DeviceBean) ((Result) (msg.obj)).getObj()).getName());
                break;

        }
        return super.handleMessage(msg);
    }

    private void bindDeviceSuccess(String name) {
        if (!mStop) {
            mBindDeviceSuccess = true;
            mView.setAddDeviceName(name);
            mView.showBindDeviceSuccessTip();
        }
    }

    private void deviceFind(String gwId) {
        if (!mStop) {
            mView.showDeviceFindTip(gwId);
        }
    }

    private void checkLoop() {
        if (mStop) return;
        if (mTime >= 100) {
            stopSearch();
            mModel.configFailure();
        } else {
            mView.setConnectProgress(mTime++, 1000);
            mHandler.sendEmptyMessageDelayed(MESSAGE_CONFIG_WIFI_OUT_OF_TIME, 1000);
        }
    }

    //????????????
    private void configSuccess(DeviceBean deviceBean) {
        if (deviceBean != null){
            Toast.makeText(mContext,"the device id is: " + deviceBean.getDevId(), Toast.LENGTH_SHORT).show();
        }
        stopSearch();
        mView.showConfigSuccessTip();
        mView.setConnectProgress(100, 800);
        mHandler.sendEmptyMessageDelayed(MESSAGE_SHOW_SUCCESS_PAGE, 1000);
    }

    //????????????
    private void stopSearch() {
        mStop = true;
        mHandler.removeMessages(MESSAGE_CONFIG_WIFI_OUT_OF_TIME);
        mModel.cancel();
    }

    /**
     * ??????????????????
     */
    public void gotoShareActivity() {
//        ActivityUtils.gotoActivity((Activity) mContext, SharedActivity.class, ActivityUtils.ANIMATE_FORWARD, true);
    }

    /**
     * ????????????
     */
    public void goForHelp() {
        Intent intent = new Intent(mContext, BrowserActivity.class);
        intent.putExtra(BrowserActivity.EXTRA_LOGIN, false);
        intent.putExtra(BrowserActivity.EXTRA_REFRESH, true);
        intent.putExtra(BrowserActivity.EXTRA_TOOLBAR, true);
        intent.putExtra(BrowserActivity.EXTRA_TITLE, mContext.getString(R.string.ty_ez_help));
        intent.putExtra(BrowserActivity.EXTRA_URI, CommonConfig.FAILURE_URL);
        mContext.startActivity(intent);
    }

    @Override
    public void onDestroy() {
        mHandler.removeMessages(MESSAGE_CONFIG_WIFI_OUT_OF_TIME);
        mHandler.removeMessages(MESSAGE_SHOW_SUCCESS_PAGE);
        mModel.onDestroy();
        super.onDestroy();
    }
}
