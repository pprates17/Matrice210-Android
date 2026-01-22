package com.dji.sdk.Matrice210App.internal.view;

import static com.dji.sdk.Matrice210App.internal.utils.ModuleVerificationUtil.getFlightController;
import static com.google.android.gms.internal.zzahn.runOnUiThread;

import android.app.Service;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.dji.sdk.Matrice210App.Interfaces.MocInteraction;
import com.dji.sdk.Matrice210App.Interfaces.MocInteractionListener;
import com.dji.sdk.Matrice210App.R;
import com.dji.sdk.Matrice210App.internal.controller.DJISampleApplication;
import com.dji.sdk.Matrice210App.internal.controller.MainActivity;
import com.dji.sdk.Matrice210App.internal.utils.Helper;
import com.dji.sdk.Matrice210App.internal.utils.ToastUtils;
import com.dji.sdk.Matrice210App.internal.utils.VideoFeedView;

import androidx.annotation.NonNull;
import dji.common.airlink.PhysicalSource;
import dji.common.error.DJIError;
import dji.common.util.CommonCallbacks;
import dji.keysdk.AirLinkKey;
import dji.keysdk.KeyManager;
import dji.keysdk.callback.ActionCallback;
import dji.keysdk.callback.SetCallback;
import dji.sdk.airlink.AirLink;
import dji.sdk.base.BaseProduct;
import dji.sdk.camera.VideoFeeder;
import dji.sdk.flightcontroller.FlightController;
import dji.sdk.products.Aircraft;
import dji.sdk.sdkmanager.DJISDKManager;

import dji.ux.widget.MapWidget;

import com.dji.sdk.Matrice210App.tools.ByteArrayUtils;

/**
 * Class that manage live video feed from DJI products to the mobile device.
 * Also give the example of "getPrimaryVideoFeed" and "getSecondaryVideoFeed".
 */
public class ControlView extends LinearLayout
        implements View.OnClickListener, PresentableView, CompoundButton.OnCheckedChangeListener {

    private PopupNumberPicker popupNumberPicker = null;
    private PopupNumberPickerDouble popupNumberPickerDouble = null;
    private static int[] INDEX_CHOSEN = { -1, -1, -1 };
    private Handler handler = new Handler(Looper.getMainLooper());

    private Context context;
    private VideoFeedView fpvVideoFeed;
    private VideoFeeder.PhysicalSourceListener sourceListener;
    private AirLinkKey extEnabledKey;
    private AirLinkKey lbBandwidthKey;
    private AirLinkKey hdmiBandwidthKey;
    private AirLinkKey mainCameraBandwidthKey;
    private AirLinkKey assignSourceToPrimaryChannelKey;
    private AirLinkKey primaryVideoBandwidthKey;
    private SetCallback setBandwidthCallback;
    private SetCallback setExtEnableCallback;
    private ActionCallback allocSourceCallback;
    private AirLink airLink;
    private View fpvCoverView;
    private TextView cameraListTitle;
    private String cameraListStr;

    private Button sendTaskBtn;

    private MapWidget mapWidget;

    private boolean isMapMini = true;

    public ControlView(Context context) {
        super(context);
        this.context = getContext();
        init(context);
    }

    private void init(Context context) {
        setOrientation(HORIZONTAL);
        setClickable(true);
        LayoutInflater layoutInflater = (LayoutInflater) context.getSystemService(Service.LAYOUT_INFLATER_SERVICE);
        layoutInflater.inflate(R.layout.control, this, true);


        initAirLink();
        initAllKeys();
        initUI();
        initCallbacks();
        setUpListeners();
    }

    private void onViewClick(View view) {
    }

    private void initUI() {
        cameraListTitle = (TextView) findViewById(R.id.camera_index_title);
        fpvVideoFeed = (VideoFeedView) findViewById(R.id.fpv_video_feed);
        fpvCoverView = findViewById(R.id.fpv_cover_view);
        fpvVideoFeed.setCoverView(fpvCoverView);

        sendTaskBtn = findViewById(R.id.send_task_btn);
        sendTaskBtn.setOnClickListener(v -> {
            sendTaskToOSDK();
        });
    }

    private void sendTaskToOSDK() {
        ToastUtils.setResultToToast("Sending...");
        FlightController flightController = getFlightController();
        if(flightController != null)
            sendData("Cenas", flightController);
    }

    // Assuming this code is inside an Activity or Fragment
    private void sendData(String text, FlightController flightController) {
        // 1. Get the data bytes
        byte[] data = text.getBytes();

        // 2. Safety Check for the @Size constraint (1-100 bytes)
        if (data.length < 1 || data.length > 100) {
            Toast.makeText(this.getContext(), "Data must be between 1 and 100 bytes", Toast.LENGTH_SHORT).show();
            return;
        }

        // 3. Access the OnboardSDKDevice (usually via FlightController)
        if (flightController != null) {
            flightController.sendDataToOnboardSDKDevice(data, new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                    // We are now on a BACKGROUND thread

                    // Inside sendData onResult:
                    if (djiError == null) {
                        handler.post(() -> {
                            Toast.makeText(context.getApplicationContext(), "Send Success!", Toast.LENGTH_SHORT).show();
                        });
                    } else {
                        final String errorDesc = djiError.getDescription();
                        handler.post(() -> {
                            Toast.makeText(context.getApplicationContext(), "Send Failed: " + errorDesc, Toast.LENGTH_LONG).show();
                        });
                    }
                }
            });
        } else {
            Log.e("DJI_SDK", "Flight Controller not initialized");
        }
    }

    public void sendData(final byte[] data) {
        ToastUtils.setResultToToast("Send data (" + data.length + ") : " + ByteArrayUtils.byteArrayToString(data) + "MOC");
    }

    private void initAirLink() {
        BaseProduct baseProduct = DJISDKManager.getInstance().getProduct();
        if (null != baseProduct && null != baseProduct.getAirLink()) {
            airLink = baseProduct.getAirLink();
        }
    }

    private void initAllKeys() {
        extEnabledKey = AirLinkKey.createLightbridgeLinkKey(AirLinkKey.IS_EXT_VIDEO_INPUT_PORT_ENABLED);
        lbBandwidthKey = AirLinkKey.createLightbridgeLinkKey(AirLinkKey.BANDWIDTH_ALLOCATION_FOR_LB_VIDEO_INPUT_PORT);
        hdmiBandwidthKey =
                AirLinkKey.createLightbridgeLinkKey(AirLinkKey.BANDWIDTH_ALLOCATION_FOR_HDMI_VIDEO_INPUT_PORT);
        mainCameraBandwidthKey = AirLinkKey.createLightbridgeLinkKey(AirLinkKey.BANDWIDTH_ALLOCATION_FOR_LEFT_CAMERA);
        assignSourceToPrimaryChannelKey = AirLinkKey.createOcuSyncLinkKey(AirLinkKey.ASSIGN_SOURCE_TO_PRIMARY_CHANNEL);
        primaryVideoBandwidthKey = AirLinkKey.createOcuSyncLinkKey(AirLinkKey.BANDWIDTH_ALLOCATION_FOR_PRIMARY_VIDEO);
    }

    private void initCallbacks() {
        setBandwidthCallback = new SetCallback() {
            @Override
            public void onSuccess() {
                ToastUtils.setResultToToast("Set key value successfully");
                if (fpvVideoFeed != null) {
                    fpvVideoFeed.changeSourceResetKeyFrame();
                }
            }

            @Override
            public void onFailure(@NonNull DJIError error) {
                ToastUtils.setResultToToast("Failed to set: " + error.getDescription());
            }
        };

        setExtEnableCallback = new SetCallback() {
            @Override
            public void onSuccess() {
                updateExtSwitchValue(null);
            }

            @Override
            public void onFailure(@NonNull DJIError error) {
                updateExtSwitchValue(null);
            }
        };

        allocSourceCallback = new ActionCallback() {
            @Override
            public void onSuccess() {
                ToastUtils.setResultToToast("Perform action successfully");
            }

            @Override
            public void onFailure(@NonNull DJIError error) {
                ToastUtils.setResultToToast("Failed to action: " + error.getDescription());
            }
        };
    }

    private void updateExtSwitchValue(Object value) {
        if (value == null && KeyManager.getInstance() != null) {
            value = KeyManager.getInstance().getValue(extEnabledKey);
        }
        final Object switchValue = value;
        if (switchValue != null) {
            ControlView.this.post(new Runnable() {
                @Override
                public void run() {
                }
            });
        }
    }


    private void setUpListeners() {
        sourceListener = new VideoFeeder.PhysicalSourceListener() {
            @Override
            public void onChange(VideoFeeder.VideoFeed videoFeed, PhysicalSource newPhysicalSource) {
            }
        };

        setVideoFeederListeners(true);
    }

    private void tearDownListeners() {
        setVideoFeederListeners(false);
    }

    private void setVideoFeederListeners(boolean isOpen) {
        if (VideoFeeder.getInstance() == null) return;

        final BaseProduct product = DJISDKManager.getInstance().getProduct();
        if (product != null) {
            VideoFeeder.VideoDataListener secondaryVideoDataListener =
                    fpvVideoFeed.registerLiveVideo(VideoFeeder.getInstance().getSecondaryVideoFeed(), false);

            if (isOpen) {
                String newText =
                        "Primary Source: ";
                if (Helper.isMultiStreamPlatform()) {
                    String newTextFpv = "Secondary Source: ";
                }
                VideoFeeder.getInstance().addPhysicalSourceListener(sourceListener);
            } else {
                VideoFeeder.getInstance().removePhysicalSourceListener(sourceListener);
                if (Helper.isMultiStreamPlatform()) {
                    VideoFeeder.getInstance()
                            .getSecondaryVideoFeed()
                            .removeVideoDataListener(secondaryVideoDataListener);
                }
            }
        }
    }

    @Override
    public void onClick(View view) {
    }

    @Override
    public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
    }



    @Override
    public int getDescription() {
        return R.string.component_listview_video_feeder;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        // Ensure the KeyManager is initialized before the widgets try to use it
        if (KeyManager.getInstance() == null) {
            Log.e("DJI_SDK", "KeyManager is null! Widgets may crash.");
        }
        DJISampleApplication.getEventBus().post(new MainActivity.RequestStartFullScreenEvent());
    }

    @Override
    protected void onDetachedFromWindow() {
        DJISampleApplication.getEventBus().post(new MainActivity.RequestEndFullScreenEvent());
        tearDownListeners();
        super.onDetachedFromWindow();
    }

    @NonNull
    @Override
    public String getHint() {
        return this.getClass().getSimpleName() + ".java";
    }
}
