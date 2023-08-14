package com.jsc.camera;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.TextView;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.jsc.camera.databinding.ActivityMainBinding;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import jsc.org.lib.camera.CameraFragment;
import jsc.org.lib.camera.CameraMaskBuilder;
import jsc.org.lib.camera.CameraParamsManager;
import jsc.org.lib.camera.entry.YuvFrame;
import jsc.org.lib.camera.utils.SoundPoolPlayer;
import jsc.org.lib.camera.utils.YuvTransfer;
import jsc.org.lib.camera.utils.YuvUtils;

public class MainActivity extends BaseActivity {

    ActivityMainBinding binding = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.btnOpenCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkCameraPermission();
            }
        });
        binding.btnOpenCamera.setClipToOutline(true);
        binding.btnOpenCamera.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                float radius = view.getHeight() / 2.0f;
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
            }
        });
        CameraParamsManager.getInstance().init(getApplicationContext());
        YuvTransfer.getInstance().init(getApplicationContext());
        SoundPoolPlayer.getInstance().register(getApplicationContext());
    }

    @Override
    public void onLazyLoad() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 0x10);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        YuvTransfer.destroyInstance();
    }

    private void checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 0x10);
        } else {
            showCamera();
        }
    }

    private Bitmap bitmap = null;
    private boolean processing = false;
    private final Rect mValidRect = new Rect();
    private final YuvFrame mFrameCache = new YuvFrame();
    private final ExecutorService mService = Executors.newSingleThreadExecutor();
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    private void showCamera() {
        Bundle arguments = new Bundle();
        arguments.putString("customCameraIdKey", getClass().getSimpleName());
        arguments.putLong("previewDelayTime", 200);
        arguments.putLong("frameDelayTime", 400);
        arguments.putBoolean("withScanAnim", true);
        CameraFragment fragment = new CameraFragment();
        fragment.setArguments(arguments);
        fragment.setCallback(new CameraFragment.CameraLifeCycleCallback() {
            @Override
            public void onViewCreated(Map<String, View> viewCache) {
                //初始化views
                //可在这里控制view的显隐、属性
                viewCache.get("root").setBackgroundColor(Color.BLACK);
                ((TextView) viewCache.get("subTitle")).setText("拍照");
                ((TextView) viewCache.get("innerTips")).setText("请看摄像头");
            }

            @Override
            public void onOpenCamera() {

            }

            @Override
            public void notFoundCamera() {

            }

            @Override
            public void onBack() {
                //这里是点击控件"backPage"回调
                Fragment f = getSupportFragmentManager().findFragmentByTag("_camera");
                if (f != null) {
                    getSupportFragmentManager().beginTransaction().remove(f).commit();
                }
            }

            @Override
            public void onCreateMask(boolean isLandscape, CameraMaskBuilder builder) {

            }

            @Override
            public void onMaskCreated(boolean isLandscape, CameraMaskBuilder builder) {
                mValidRect.set(builder.getTransparentRectInFrame());
            }

            @Override
            public void frame(int facing, boolean mirror, int mVideoOrientation, int mFrameOrientation, byte[] yuvData, int width, int height) {

            }

            @Override
            public void onShutter(int facing, int displayOrientation, int mFrameOrientation, byte[] yuvData, int width, int height) {
                if (!processing) {
                    SoundPoolPlayer.getInstance().playShutterVoice();
                    processing = true;
                    mFrameCache.facing = facing;
                    mFrameCache.yuvData = yuvData;
                    mFrameCache.width = width;
                    mFrameCache.height = height;
                    mFrameCache.clipRect = new Rect(mValidRect);
                    mFrameCache.frameRotation = mFrameOrientation;
                    mService.submit(new Runnable() {
                        @Override
                        public void run() {
                            YuvUtils.dealOriginalFrameIfN(mFrameCache, false);
                            bitmap = YuvTransfer.getInstance().transfer(mFrameCache);
                            mHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    binding.ivImg.setImageBitmap(bitmap);
                                }
                            });
                            processing = false;
                        }
                    });
                }
            }

            @Override
            public void onCameraClosed() {

            }
        });
        getSupportFragmentManager().beginTransaction().add(R.id.fy_camera_container, fragment, "_camera").commit();
    }
}