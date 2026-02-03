package com.dji.sdk.Matrice210App.internal.view;

import static com.dji.sdk.Matrice210App.internal.utils.ModuleVerificationUtil.getFlightController;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.dji.sdk.Matrice210App.BuildConfig;
import com.dji.sdk.Matrice210App.R;
import com.dji.sdk.Matrice210App.internal.controller.DJISampleApplication;
import com.dji.sdk.Matrice210App.internal.controller.MainActivity;
import com.dji.sdk.Matrice210App.internal.utils.Helper;
import com.dji.sdk.Matrice210App.internal.utils.ToastUtils;
import com.dji.sdk.Matrice210App.internal.utils.VideoFeedView;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;

import dji.common.airlink.PhysicalSource;
import dji.common.error.DJIError;
import dji.keysdk.AirLinkKey;
import dji.keysdk.KeyManager;
import dji.keysdk.callback.ActionCallback;
import dji.keysdk.callback.SetCallback;
import dji.sdk.airlink.AirLink;
import dji.sdk.base.BaseProduct;
import dji.sdk.camera.VideoFeeder;
import dji.sdk.flightcontroller.FlightController;
import dji.sdk.sdkmanager.DJISDKManager;

import com.dji.mapkit.core.maps.DJIMap;

import dji.ux.beta.map.widget.map.MapWidget;

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
        setupOSDKReceiver();
    }

    private void onViewClick(View view) {
    }

    private void initUI() {
        cameraListTitle = (TextView) findViewById(R.id.camera_index_title);
        fpvVideoFeed = (VideoFeedView) findViewById(R.id.fpv_video_feed);
        fpvCoverView = findViewById(R.id.fpv_cover_view);
        fpvVideoFeed.setCoverView(fpvCoverView);
        findViewById(R.id.radar_widget).setClickable(false);

        mapWidget = findViewById(R.id.map_widget);

        MapWidget.OnMapReadyListener onMapReadyListener = map -> {
            map.setMapType(DJIMap.MapType.NORMAL);
            mapWidget.setMapCenterLock(MapWidget.MapCenterLock.AIRCRAFT);
            mapWidget.setAircraftMarkerEnabled(true);
            mapWidget.setHomeMarkerEnabled(true);
            mapWidget.setFlightPathEnabled(true);
            mapWidget.onResume();
        };

        String token = BuildConfig.MAPBOX_API_TOKEN;
        try {
            com.mapbox.mapboxsdk.Mapbox.getInstance(this.getContext(), token);
            mapWidget.initMapboxMap(token, onMapReadyListener);
            mapWidget.getUserAccountLoginWidget().setVisibility(View.GONE);
        } catch (Exception e) {
            Log.e("DJI_MAP", "Mapbox initialization failed: " + e.getMessage());
        }

        sendTaskBtn = findViewById(R.id.send_task_btn);
        sendTaskBtn.setOnClickListener(v -> {
            sendTaskToOSDK();
        });

        setSwapLogic();
    }

    private void sendTaskToOSDK() {
        FlightController flightController = getFlightController();
        if(flightController != null)
            sendData("Hello OSDK", flightController);
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
            flightController.sendDataToOnboardSDKDevice(data, djiError -> {
                if (djiError == null) {
                    handler.post(() -> {
                        Toast.makeText(context.getApplicationContext(), "Msg Sent: " + text, Toast.LENGTH_SHORT).show();
                    });
                } else {
                    final String errorDesc = djiError.getDescription();
                    handler.post(() -> {
                        Toast.makeText(context.getApplicationContext(), "Send Failed: " + errorDesc, Toast.LENGTH_LONG).show();
                    });
                }
            });
        } else {
            Log.e("DJI_SDK", "Flight Controller not initialized");
        }
    }

    private void setupOSDKReceiver() {
        FlightController flightController = getFlightController();
        if(flightController != null) {
            flightController.setOnboardSDKDeviceDataCallback(new FlightController.OnboardSDKDeviceDataCallback() {
                @Override
                public void onReceive(byte[] bytes) {
                    // This runs on a BACKGROUND thread
                    String data = ByteArrayUtils.byteArrayToString(bytes);

                    handler.post(() -> {
                        // Limit log length for toast
                        String display = data.length() > 20 ? data.substring(0, 20) + "..." : data;
                        ToastUtils.setResultToToast("Msg Received: " + display);
                    });
                }
            });
            Log.d("DJI_SDK", "OSDK Data Callback Registered");
        }
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
        mapWidget.onDestroy();
        super.onDetachedFromWindow();
    }

    @NonNull
    @Override
    public String getHint() {
        return this.getClass().getSimpleName() + ".java";
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setSwapLogic() {
        View map_gesture_shield = findViewById(R.id.map_gesture_shield);
        View map_fpv_shield = findViewById(R.id.fpv_cover_view);
        final GestureDetector detector = new GestureDetector(getContext(), new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(@NonNull MotionEvent e) {
                Log.d("DJI_DEBUG", "Double tap detected!");
                swapMap();
                return true;
            }

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                return false;
            }
        });

        map_gesture_shield.setOnTouchListener((v, event) -> {
            detector.onTouchEvent(event);
            if (mapWidget != null) {
                mapWidget.dispatchTouchEvent(event);
            }
            return true;
        });
        map_fpv_shield.setOnTouchListener((v, event) -> detector.onTouchEvent(event));
    }

    private void swapMap() {
        ConstraintLayout root_layout = findViewById(R.id.root_control_layout);
        if (root_layout == null) return;

        ConstraintSet set = new ConstraintSet();
        set.clone(root_layout);

        if (isMapMini) {
            applyConstraints(set, R.id.map_container, 0.85f, 0.70f, true);
            applyConstraints(set, R.id.video_feed_container, 0f, 0f, false);
            isMapMini = false;
        } else {
            applyConstraints(set, R.id.video_feed_container, 0.85f, 0.70f, true);
            applyConstraints(set, R.id.map_container, 0f, 0f, false);
            isMapMini = true;
        }

        set.applyTo(root_layout);
    }

    private void applyConstraints(ConstraintSet set, int viewId, float wPercent, float hPercent, boolean isBig) {
        set.clear(viewId);

        if (isBig) {
            set.constrainWidth(viewId, ConstraintSet.MATCH_CONSTRAINT);
            set.constrainHeight(viewId, ConstraintSet.MATCH_CONSTRAINT);

            set.constrainPercentWidth(viewId, wPercent);
            set.constrainPercentHeight(viewId, hPercent);

            set.connect(viewId, ConstraintSet.TOP, R.id.remaining_flight_time_widget, ConstraintSet.BOTTOM);
            set.connect(viewId, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START);
            set.connect(viewId, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END);
            set.connect(viewId, ConstraintSet.BOTTOM, R.id.dash_group_container, ConstraintSet.TOP);
        } else {
            set.constrainWidth(viewId, dpToPx(200));
            set.constrainHeight(viewId, dpToPx(130));
            set.connect(viewId, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM);
            set.connect(viewId, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END);
            set.clear(viewId, ConstraintSet.TOP);
        }
    }

    // Helper to ensure sizes are correct across different screen densities
    private int dpToPx(int dp) {
        float density = getContext().getResources().getDisplayMetrics().density;
        return Math.round((float) dp * density);
    }
}
