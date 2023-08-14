package jsc.org.lib.camera;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Outline;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CameraFragment extends Fragment {

    private final static int MSG_ID_FOCUS = 0x6665;
    private final static int MSG_ID_FRAME_DELAY = 0x6666;
    private final String CUSTOM_CAMERA_ID_KEY = "customCameraIdKey";
    private final String TAG = getClass().getSimpleName();
    private final Map<String, View> viewCache = new HashMap<>();
    private final CameraMaskBuilder mCameraMaskBuilder = new CameraMaskBuilder();
    private boolean initialized;

    private String mCameraIdMode = "none";
    private Camera mCamera = null;
    private int mCameraId;
    private int mVideoRotation;
    private int mFrameRotation;
    private int mFrameMirror;
    private int previewWidth = 0;
    private int previewHeight = 0;
    private int vw = 0;
    private int vh = 0;
    private VideoRatio mRatio;
    private final CameraFrame lastFrame = new CameraFrame();
    private CameraLifeCycleCallback callback = null;
    private Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            if (msg.what == MSG_ID_FOCUS) {
                //auto focus
                autoFocus();
            } else if (msg.what == MSG_ID_FRAME_DELAY) {
                frameDelayed = true;
            }
        }
    };
    private boolean isAutoFocusMode = false;
    private final Camera.AutoFocusCallback focusCallback = new Camera.AutoFocusCallback() {
        @Override
        public void onAutoFocus(boolean success, Camera camera) {
            Log.d(TAG, "onAutoFocus: " + success);
            if (isAutoFocusMode) {
                //auto focus after 2 seconds.
                sendAutoFocusMessage(2_000L);
            }
        }
    };
    private boolean paused = false;
    private final Camera.PreviewCallback previewCallback = new Camera.PreviewCallback() {
        @Override
        public void onPreviewFrame(byte[] yuvData, Camera camera) {
            if (frameDelayTime > 0L && !frameDelayed) {
                mHandler.sendEmptyMessageDelayed(MSG_ID_FRAME_DELAY, frameDelayTime);
                return;
            }
            lastFrame.nv21 = yuvData;
            lastFrame.width = previewWidth;
            lastFrame.height = previewHeight;
            if (!paused && callback != null) {
                callback.frame(mCameraId, mFrameMirror == -1, mVideoRotation, mFrameRotation, yuvData, previewWidth, previewHeight);
            }
        }
    };

    private String screenDirection;
    private boolean frameDelayed = false;//是否延迟时间返回帧
    private long frameDelayTime = 0L;//延迟返回帧时间

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        screenDirection = isLandscape() ? CameraParamsManager.LAND : CameraParamsManager.PORT;
        String mCameraIdStr = null;
        if (getArguments() != null) {
            if (getArguments().containsKey("tempCameraId")) {
                mCameraIdMode = "temp";
                mCameraIdStr = getArguments().getString("tempCameraId");
            } else if (getArguments().containsKey(CUSTOM_CAMERA_ID_KEY)) {
                mCameraIdMode = "custom";
                String key = getArguments().getString(CUSTOM_CAMERA_ID_KEY);
                mCameraIdStr = CameraParamsManager.getInstance().getCustomCameraId(key);
            }
            if (getArguments().containsKey("frameDelayTime")) {
                frameDelayTime = Math.max(getArguments().getLong("frameDelayTime"), 0L);
            }
        }
        if (TextUtils.isEmpty(mCameraIdStr)) {
            mCameraIdMode = "none";
            mCameraIdStr = CameraParamsManager.getInstance().getDefaultCameraId();
        }
        mCameraId = Integer.parseInt(mCameraIdStr);
    }

    private int backgroundColor() {
        return getArguments() == null ? Color.TRANSPARENT : getArguments().getInt("backgroundColor", Color.TRANSPARENT);
    }

    private <L extends View.OnClickListener> void clickListen(String viewKey, L l) {
        View view = viewCache.get(viewKey);
        if (view != null) {
            view.setOnClickListener(l);
        }
    }

    private void visible(String viewKey) {
        View view = viewCache.get(viewKey);
        if (view != null) {
            boolean visible = getArguments() == null
                    || getArguments().getBoolean(viewKey + "Visible", true);
            view.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    private void resize(String viewKey, int width, int height, boolean invalidateImmediately) {
        View view = viewCache.get(viewKey);
        if (view != null) {
            ViewGroup.LayoutParams params = view.getLayoutParams();
            params.width = width;
            params.height = height;
            if (invalidateImmediately) {
                view.setLayoutParams(params);
            }
        }
    }

    private void image(String viewKey, Bitmap bitmap) {
        View view = viewCache.get(viewKey);
        if (view instanceof ImageView) {
            ((ImageView) view).setImageBitmap(bitmap);
        }
    }

    private void text(String viewKey, boolean fromArguments, CharSequence temp) {
        View view = viewCache.get(viewKey);
        if (view instanceof TextView) {
            if (fromArguments) {
                CharSequence text = getArguments() == null ? "" : getArguments().getCharSequence(viewKey + "Text", "");
                ((TextView) view).setText(text);
            } else {
                ((TextView) view).setText(temp);
            }
        }
    }

    private void textColor(String viewKey, int defaultColor) {
        View view = viewCache.get(viewKey);
        if (view instanceof TextView) {
            int textColor = getArguments() == null ? defaultColor : getArguments().getInt(viewKey + "TextColor", defaultColor);
            ((TextView) view).setTextColor(textColor);
        }
    }

    private void enableView(String viewKey, boolean enable) {
        View view = viewCache.get(viewKey);
        if (view != null) {
            view.setEnabled(enable);
        }
    }

    private void selectedView(String viewKey, boolean selected) {
        View view = viewCache.get(viewKey);
        if (view != null) {
            view.setSelected(selected);
        }
    }

    private boolean isComponentVisible(String viewKey) {
        View view = viewCache.get(viewKey);
        return view != null && view.getVisibility() == View.VISIBLE;
    }

    private void enableButtons(boolean enable) {
        enableView("backPage", enable);
        enableView("flashTorch", enable);
        enableView("switch", enable);
        enableView("setting", enable);
        enableView("shutter", enable);
    }

    private void updateInnerTipsViewLocation(int vh, int tvh, Rect centerLocation) {
        View view = viewCache.get("innerTips");
        if (view != null) {
            int topMargin = 0;
            if (centerLocation.width() <= 0
                    || centerLocation.height() <= 0) {
                topMargin = (vh - tvh) / 2 + tvh * 4 / 5;
            } else {
                topMargin = (vh - tvh) / 2 + centerLocation.bottom + (tvh - centerLocation.height()) / 8;
            }
            ViewGroup.LayoutParams params = view.getLayoutParams();
            if (params instanceof ViewGroup.MarginLayoutParams) {
                ((ViewGroup.MarginLayoutParams) params).topMargin = topMargin;
            }
        }
    }

    private void updateVideoRotationSelectedState(int videoRotation) {
        selectedView("videoRotation0", videoRotation == 0);
        selectedView("videoRotation90", videoRotation == 90);
        selectedView("videoRotation180", videoRotation == 180);
        selectedView("videoRotation270", videoRotation == 270);
    }

    private void updateVideoMirrorSelectedState(int value) {
        selectedView("videoMirrorOpen", value == -1);
        selectedView("videoMirrorClose", value == 1);
    }

    private void updateFrameMirrorSelectedState(int value) {
        selectedView("frameMirrorOpen", value == -1);
        selectedView("frameMirrorClose", value == 1);
    }

    private void updateFrameRotationSelectedState(int frameRotation) {
        selectedView("frameRotation0", frameRotation == 0);
        selectedView("frameRotation90", frameRotation == 90);
        selectedView("frameRotation180", frameRotation == 180);
        selectedView("frameRotation270", frameRotation == 270);
    }

    private void updateMaskShapeSelectedState(int shape) {
        selectedView("maskShapeNone", shape == CameraMaskBuilder.SHAPE_NONE);
        selectedView("maskShapeCircle", shape == CameraMaskBuilder.SHAPE_CIRCLE);
        selectedView("maskShapeSquare", shape == CameraMaskBuilder.SHAPE_SQUARE);
        selectedView("maskShapeRectangle", shape == CameraMaskBuilder.SHAPE_RECTANGLE);
        selectedView("maskShapeDrawable", shape == CameraMaskBuilder.SHAPE_DRAWABLE);
    }

    private void updateMaskEdgeSelectedState(int style) {
        selectedView("maskEdgeNone", style == CameraMaskBuilder.EDGE_STYLE_NONE);
        selectedView("maskEdgeDefault", style == CameraMaskBuilder.EDGE_STYLE_DEFAULT);
        selectedView("maskEdgeAngle", style == CameraMaskBuilder.EDGE_STYLE_ANGLE);
    }

    private void showViewWithAnimation(final View view) {
        if (view != null) {
            if (view.getVisibility() == View.VISIBLE) return;
            view.setVisibility(View.VISIBLE);
            TranslateAnimation animation = new TranslateAnimation(
                    Animation.RELATIVE_TO_SELF, 0.0f,
                    Animation.RELATIVE_TO_SELF, 0.0f,
                    Animation.RELATIVE_TO_SELF, -1.0f,
                    Animation.RELATIVE_TO_SELF, 0.0f);
            animation.setDuration(300);
            view.startAnimation(animation);
        }
    }

    private void hideViewWithAnimation(final View view) {
        if (view != null) {
            if (view.getVisibility() != View.VISIBLE) return;
            TranslateAnimation animation = new TranslateAnimation(
                    Animation.RELATIVE_TO_SELF, 0.0f,
                    Animation.RELATIVE_TO_SELF, 0.0f,
                    Animation.RELATIVE_TO_SELF, 0.0f,
                    Animation.RELATIVE_TO_SELF, -1.0f);
            animation.setDuration(300);
            animation.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {

                }

                @Override
                public void onAnimationEnd(Animation animation) {
                    view.setVisibility(View.GONE);
                }

                @Override
                public void onAnimationRepeat(Animation animation) {

                }
            });
            view.startAnimation(animation);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.c_fragment_camera, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (!initialized) {
            initialized = true;
            //防点击穿透
            view.setClickable(true);
            view.setFocusable(true);

            viewCache.put("root", view);
            viewCache.put("display", view.findViewById(R.id.texture_view));
            viewCache.put("mask", view.findViewById(R.id.iv_mask));
            viewCache.put("scannerContainer", view.findViewById(R.id.fy_scanner_container));
            viewCache.put("scanner", view.findViewById(R.id.iv_scanner));
            viewCache.put("backPage", view.findViewById(R.id.tv_back_page));
            viewCache.put("subTitle", view.findViewById(R.id.tv_sub_title));
            viewCache.put("cameraParams", view.findViewById(R.id.tv_camera_params));
            viewCache.put("solution", view.findViewById(R.id.tv_solution));
            viewCache.put("innerTips", view.findViewById(R.id.tv_inner_tips));
            viewCache.put("shutter", view.findViewById(R.id.iv_shutter));

            viewCache.put("flashTorch", view.findViewById(R.id.iv_flash_torch));
            viewCache.put("switch", view.findViewById(R.id.iv_switch));
            viewCache.put("setting", view.findViewById(R.id.iv_setting));
            viewCache.put("settingContainer", view.findViewById(R.id.cl_setting_container));
            viewCache.put("settingTitle", view.findViewById(R.id.tv_setting_title));
            viewCache.put("videoRotation0", view.findViewById(R.id.tv_video_rotation_0));
            viewCache.put("videoRotation90", view.findViewById(R.id.tv_video_rotation_90));
            viewCache.put("videoRotation180", view.findViewById(R.id.tv_video_rotation_180));
            viewCache.put("videoRotation270", view.findViewById(R.id.tv_video_rotation_270));
            viewCache.put("videoMirrorOpen", view.findViewById(R.id.tv_video_mirror_open));
            viewCache.put("videoMirrorClose", view.findViewById(R.id.tv_video_mirror_close));
            viewCache.put("frameMirrorOpen", view.findViewById(R.id.tv_frame_mirror_open));
            viewCache.put("frameMirrorClose", view.findViewById(R.id.tv_frame_mirror_close));
            viewCache.put("frameRotation0", view.findViewById(R.id.tv_frame_rotation_0));
            viewCache.put("frameRotation90", view.findViewById(R.id.tv_frame_rotation_90));
            viewCache.put("frameRotation180", view.findViewById(R.id.tv_frame_rotation_180));
            viewCache.put("frameRotation270", view.findViewById(R.id.tv_frame_rotation_270));
            //mask
            viewCache.put("labelMaskShape", view.findViewById(R.id.tv_label_mask_shape));
            viewCache.put("maskShapeNone", view.findViewById(R.id.tv_mask_shape_none));
            viewCache.put("maskShapeCircle", view.findViewById(R.id.tv_mask_shape_circle));
            viewCache.put("maskShapeSquare", view.findViewById(R.id.tv_mask_shape_square));
            viewCache.put("maskShapeRectangle", view.findViewById(R.id.tv_mask_shape_rectangle));
            viewCache.put("maskShapeDrawable", view.findViewById(R.id.tv_mask_shape_drawable));
            viewCache.put("maskEdgeNone", view.findViewById(R.id.tv_mask_edge_none));
            viewCache.put("maskEdgeDefault", view.findViewById(R.id.tv_mask_edge_default));
            viewCache.put("maskEdgeAngle", view.findViewById(R.id.tv_mask_edge_angle));
            final View.OnClickListener videoRotationListener = new View.OnClickListener() {

                @Override
                public void onClick(View v) {
                    hideViewWithAnimation(viewCache.get("settingContainer"));
                    if (v.isSelected()) return;
                    int id = v.getId();
                    int index = -1;
                    if (id == R.id.tv_video_rotation_0) {
                        index = 0;
                    } else if (id == R.id.tv_video_rotation_90) {
                        index = 1;
                    } else if (id == R.id.tv_video_rotation_180) {
                        index = 2;
                    } else if (id == R.id.tv_video_rotation_270) {
                        index = 3;
                    }
                    mVideoRotation = index * 90;
                    CameraParamsManager.getInstance().updateVideoRotation(screenDirection, mVideoRotation);
                    updateVideoRotationSelectedState(mVideoRotation);
                    if (mCamera != null) {
                        mCamera.setDisplayOrientation(calCameraDisplayOrientation(mVideoRotation));
                    }
                }
            };
            clickListen("videoRotation0", videoRotationListener);
            clickListen("videoRotation90", videoRotationListener);
            clickListen("videoRotation180", videoRotationListener);
            clickListen("videoRotation270", videoRotationListener);
            final View.OnClickListener videoMirrorListener = new View.OnClickListener() {

                @Override
                public void onClick(View v) {
                    hideViewWithAnimation(viewCache.get("settingContainer"));
                    if (v.isSelected()) return;
                    int id = v.getId();
                    int value = 0;
                    if (id == R.id.tv_video_mirror_open) {
                        value = -1;
                    } else if (id == R.id.tv_video_mirror_close) {
                        value = 1;
                    }
                    CameraParamsManager.getInstance().updateVideoMirror(String.valueOf(mCameraId), value);
                    updateVideoMirrorSelectedState(value);
                    ((TextureView) viewCache.get("display")).setScaleX(value);
                }
            };
            clickListen("videoMirrorOpen", videoMirrorListener);
            clickListen("videoMirrorClose", videoMirrorListener);
            View.OnClickListener frameMirrorListener = new View.OnClickListener() {

                @Override
                public void onClick(View v) {
                    hideViewWithAnimation(viewCache.get("settingContainer"));
                    if (v.isSelected()) return;
                    int id = v.getId();
                    int value = 0;
                    if (id == R.id.tv_frame_mirror_open) {
                        value = -1;
                    } else if (id == R.id.tv_frame_mirror_close) {
                        value = 1;
                    }
                    mFrameMirror = value;
                    CameraParamsManager.getInstance().updateFrameMirror(String.valueOf(mCameraId), value);
                    updateFrameMirrorSelectedState(value);
                }
            };
            clickListen("frameMirrorOpen", frameMirrorListener);
            clickListen("frameMirrorClose", frameMirrorListener);
            View.OnClickListener frameRotationListener = new View.OnClickListener() {

                @Override
                public void onClick(View v) {
                    hideViewWithAnimation(viewCache.get("settingContainer"));
                    if (v.isSelected()) return;
                    int id = v.getId();
                    int index = -1;
                    if (id == R.id.tv_frame_rotation_0) {
                        index = 0;
                    } else if (id == R.id.tv_frame_rotation_90) {
                        index = 1;
                    } else if (id == R.id.tv_frame_rotation_180) {
                        index = 2;
                    } else if (id == R.id.tv_frame_rotation_270) {
                        index = 3;
                    }
                    mFrameRotation = index * 90;
                    CameraParamsManager.getInstance().updateFrameRotation(screenDirection, String.valueOf(mCameraId), mFrameRotation);
                    updateFrameRotationSelectedState(mFrameRotation);
                }
            };
            clickListen("frameRotation0", frameRotationListener);
            clickListen("frameRotation90", frameRotationListener);
            clickListen("frameRotation180", frameRotationListener);
            clickListen("frameRotation270", frameRotationListener);
            View.OnClickListener maskShapeListener = new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    hideViewWithAnimation(viewCache.get("settingContainer"));
                    if (v.isSelected()) return;
                    int id = v.getId();
                    int maskShape = -100;
                    if (id == R.id.tv_mask_shape_none) {
                        maskShape = CameraMaskBuilder.SHAPE_NONE;
                    } else if (id == R.id.tv_mask_shape_circle) {
                        maskShape = CameraMaskBuilder.SHAPE_CIRCLE;
                    } else if (id == R.id.tv_mask_shape_square) {
                        maskShape = CameraMaskBuilder.SHAPE_SQUARE;
                    } else if (id == R.id.tv_mask_shape_rectangle) {
                        maskShape = CameraMaskBuilder.SHAPE_RECTANGLE;
                    } else if (id == R.id.tv_mask_shape_drawable) {
                        maskShape = CameraMaskBuilder.SHAPE_DRAWABLE;
                    }
                    updateMaskShapeSelectedState(maskShape);
                    mCameraMaskBuilder.maskShape = maskShape;
                    ViewGroup.LayoutParams params = viewCache.get("display").getLayoutParams();
                    updateMaskUI(params.width, params.height);
                }
            };
            clickListen("maskShapeNone", maskShapeListener);
            clickListen("maskShapeCircle", maskShapeListener);
            clickListen("maskShapeSquare", maskShapeListener);
            clickListen("maskShapeRectangle", maskShapeListener);
            clickListen("maskShapeDrawable", maskShapeListener);
            View.OnClickListener maskEdgeListener = new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    hideViewWithAnimation(viewCache.get("settingContainer"));
                    if (v.isSelected()) return;
                    int id = v.getId();
                    int maskEdgeStyle = -100;
                    if (id == R.id.tv_mask_edge_none) {
                        maskEdgeStyle = CameraMaskBuilder.EDGE_STYLE_NONE;
                    } else if (id == R.id.tv_mask_edge_default) {
                        maskEdgeStyle = CameraMaskBuilder.EDGE_STYLE_DEFAULT;
                    } else if (id == R.id.tv_mask_edge_angle) {
                        maskEdgeStyle = CameraMaskBuilder.EDGE_STYLE_ANGLE;
                    }
                    updateMaskEdgeSelectedState(maskEdgeStyle);
                    mCameraMaskBuilder.maskEdgeStyle = maskEdgeStyle;
                    ViewGroup.LayoutParams params = viewCache.get("display").getLayoutParams();
                    updateMaskUI(params.width, params.height);
                }
            };
            clickListen("maskEdgeNone", maskEdgeListener);
            clickListen("maskEdgeDefault", maskEdgeListener);
            clickListen("maskEdgeAngle", maskEdgeListener);

            visible("mask");
            visible("backPage");
            visible("setting");
            visible("solution");
            visible("shutter");

            View switchView = viewCache.get("switch");
            assert switchView != null;
            boolean switchVisible = true;
            if (getArguments() != null && getArguments().containsKey("switchVisible")) {
                switchVisible = getArguments().getBoolean("switchVisible");
            }
            switchView.setVisibility(switchVisible ? View.VISIBLE : View.GONE);

            textColor("subTitle", Color.WHITE);
            textColor("solution", Color.WHITE);
            textColor("innerTips", Color.WHITE);
            view.setBackgroundColor(backgroundColor());
            text("subTitle", true, "");

            //resize views
            Point outSize = new Point();
            WindowManager wm = (WindowManager) view.getContext().getSystemService(Context.WINDOW_SERVICE);
            wm.getDefaultDisplay().getRealSize(outSize);
            int size0 = Math.min(outSize.x, outSize.y) / 10;
            size0 = size0 / 2 * 2;
            resize("flashTorch", size0, size0, true);
            resize("switch", size0, size0, true);
            resize("setting", size0, size0, true);
            int size1 = Math.min(outSize.x, outSize.y) * 3 / 20;
            size1 = size1 / 2 * 2;
            resize("shutter", size1, size1, true);

            View.OnClickListener l = new View.OnClickListener() {
                @Override
                public void onClick(final View v) {
                    if (v.getId() == R.id.tv_back_page) {
                        //返回
                        if (callback != null) {
                            callback.onBack();
                        }
                    } else if (v.getId() == R.id.iv_flash_torch) {
                        //手电筒
                        boolean enable = !v.isSelected();
                        if (changeFlashTorch(enable)) {
                            v.setSelected(enable);
                        }
                    } else if (v.getId() == R.id.iv_switch) {
                        //前后置摄像头切换
                        if (Camera.getNumberOfCameras() < 2) {
                            int targetCameraId = 1 - mCameraId;
                            Toast.makeText(v.getContext().getApplicationContext(), targetCameraId == Camera.CameraInfo.CAMERA_FACING_FRONT ? "前置摄像头不可用" : "后置摄像头不可用", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        enableButtons(false);
                        switchCamera(v);
                    } else if (v.getId() == R.id.iv_setting) {
                        //设置
//                        showViewWithAnimation(viewCache.get("settingContainer"));
                        viewCache.get("settingContainer").setVisibility(View.VISIBLE);
                    } else if (v.getId() == R.id.iv_shutter) {
                        //拍照
                        shutter();
                    } else if (v.getId() == R.id.iv_mask) {
                        //setting close
                        hideViewWithAnimation(viewCache.get("settingContainer"));
                        //auto focus
                        try {
                            if (mCamera != null) {
                                mCamera.cancelAutoFocus();
                            }
                        } catch (Exception ignore) {

                        }
                        if (isAutoFocusMode) {
                            sendAutoFocusMessage(0L);
                        } else {
                            autoFocus();
                        }
                    }
                }
            };
            clickListen("backPage", l);
            clickListen("flashTorch", l);
            clickListen("switch", l);
            clickListen("setting", l);
            clickListen("shutter", l);
            clickListen("mask", l);

            ((TextureView) viewCache.get("display")).setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
                @Override
                public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                    if (vw <= 0) {
                        vw = width;
                        vh = height;
                    }
                    executeOpenCameraAction(surface);
                }

                @Override
                public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

                }

                @Override
                public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                    closeCamera();
                    //clear message queue
                    mHandler.removeMessages(MSG_ID_FOCUS);
                    mHandler.removeMessages(MSG_ID_FRAME_DELAY);
                    return true;
                }

                @Override
                public void onSurfaceTextureUpdated(SurfaceTexture surface) {

                }
            });
            if (callback != null) {
                callback.onViewCreated(viewCache);
            }
        }
    }

    private void changeViewSize(String key, int size) {
        View view = viewCache.get(key);
        if (view != null) {
            ViewGroup.LayoutParams params = view.getLayoutParams();
            params.width = size;
            params.height = size;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onPause() {
        super.onPause();
        paused = true;
        if (mCamera != null) {
            mCamera.stopPreview();
        }
        frameDelayed = false;
    }

    @Override
    public void onResume() {
        super.onResume();
        paused = false;
        if (mCamera != null) {
            mCamera.startPreview();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mHandler != null) {
            mHandler.removeCallbacksAndMessages(null);
            mHandler = null;
        }
    }

    private void switchCamera(final View v) {
        frameDelayed = false;
        mCameraId = 1 - mCameraId;
        //如果需要，更新本地数据
        if ("custom".equals(mCameraIdMode) && getArguments() != null) {
            String key = getArguments().getString(CUSTOM_CAMERA_ID_KEY);
            if (!TextUtils.isEmpty(key)) {
                CameraParamsManager.getInstance().updateCustomCameraId(key, String.valueOf(mCameraId));
            }
        }
        if (mHandler != null) {
            mHandler.removeMessages(MSG_ID_FOCUS);
            mHandler.removeMessages(MSG_ID_FRAME_DELAY);
        }
        if (mCamera != null) {
            mCamera.cancelAutoFocus();
        }
        closeCamera();
        selectedView("flashTorch", false);
        v.postDelayed(new Runnable() {
            @Override
            public void run() {
                executeOpenCameraAction(((TextureView) viewCache.get("display")).getSurfaceTexture());
                enableButtons(true);
                if (mCamera == null) return;
                sendAutoFocusMessage(250L);
            }
        }, 300);
    }

    private void shutter() {
        //stop scan animation
        View scannerView = viewCache.get("scanner");
        Animation animation = scannerView == null ? null : scannerView.getAnimation();
        if (animation != null && animation.hasStarted()) {
            animation.cancel();
        }
        stopPreview();
        if (callback != null) {
            callback.onShutter(mCameraId, mVideoRotation, mFrameRotation, lastFrame.nv21, lastFrame.width, lastFrame.height);
        }
        startPreview();
        //restart the scan animation
        if (animation != null) {
            animation.start();
        }
    }

    private boolean changeFlashTorch(boolean enable) {
        if (mCamera == null) return false;
        Camera.Parameters parameters = mCamera.getParameters();
        List<String> supportedFlashModes = parameters.getSupportedFlashModes();
        if (supportedFlashModes == null || supportedFlashModes.isEmpty()) return false;
        if (enable && supportedFlashModes.contains(Camera.Parameters.FLASH_MODE_TORCH)) {
            parameters.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
            mCamera.setParameters(parameters);
            return true;
        }
        if (!enable && supportedFlashModes.contains(Camera.Parameters.FLASH_MODE_OFF)) {
            parameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
            mCamera.setParameters(parameters);
            return true;
        }
        return false;
    }

    private void executeOpenCameraAction(SurfaceTexture surface) {
        try {
            openCameraIfNot();
        } catch (RuntimeException e) {
            mCamera = null;
            if (ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(getActivity(), new String[]{Manifest.permission.CAMERA}, 0);
            } else {
                showOpenCameraFailedDialog(e.getLocalizedMessage(), "相机硬件不稳定或CPU温度过高？可尝试打开系统相机查看情况。");
            }
            return;
        }
        if (callback != null) {
            callback.onOpenCamera();
        }
        initAndStartPreview(surface);
    }

    private void initAndStartPreview(SurfaceTexture surface) {
        long delay = getArguments() == null ? 0L : getArguments().getLong("previewDelayTime");
        //after open camera,waite a minute
        //getParameters() throw runtime exception maybe sometime.
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    initCameraParams();
                } catch (RuntimeException e) {
                    showGetParametersFailedDialog(e.getLocalizedMessage());
                    return;
                }
                setSurface(surface);
                startPreview();
                autoFocus();
            }
        }, delay);
    }

    private void openCameraIfNot() {
        if (mCamera != null) {
            return;
        }

        int numberOfCameras = Camera.getNumberOfCameras();
        if (mCameraId < 0 || mCameraId >= numberOfCameras) {
            mCameraId = numberOfCameras - 1;
        }
        if (mCameraId < 0) {
            if (callback != null) {
                callback.notFoundCamera();
            }
            return;
        }
        mCamera = Camera.open(mCameraId);
        mVideoRotation = CameraParamsManager.getInstance().getVideoRotation(screenDirection);
        mFrameRotation = CameraParamsManager.getInstance().getFrameRotation(screenDirection, String.valueOf(mCameraId));
        ((TextView) viewCache.get("settingTitle")).setText(mCameraId == Camera.CameraInfo.CAMERA_FACING_FRONT ? "设置：前置摄像头" : "设置：后置摄像头");
        updateVideoRotationSelectedState(mVideoRotation);
        updateVideoMirrorSelectedState(CameraParamsManager.getInstance().getVideoMirror(String.valueOf(mCameraId)));
        mFrameMirror = CameraParamsManager.getInstance().getFrameMirror(String.valueOf(mCameraId));
        updateFrameMirrorSelectedState(mFrameMirror);
        updateFrameRotationSelectedState(mFrameRotation);
        ((TextureView) viewCache.get("display")).setScaleX(CameraParamsManager.getInstance().getVideoMirror(String.valueOf(mCameraId)));
        mCamera.setDisplayOrientation(calCameraDisplayOrientation(mVideoRotation));
    }

    private void showOpenCameraFailedDialog(String message, String reason) {
        new AlertDialog.Builder(getActivity())
                .setTitle("调起相机失败")
                .setMessage(String.format(Locale.US, "%s\n\n%s", message, reason))
                .setCancelable(false)
                .setNegativeButton("取消", null)
                .setPositiveButton("重试", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        executeOpenCameraAction(((TextureView) viewCache.get("display")).getSurfaceTexture());
                    }
                }).show();
    }

    private void showGetParametersFailedDialog(String message) {
        new AlertDialog.Builder(getActivity())
                .setTitle("获取相机硬件参数失败")
                .setMessage(String.format(Locale.US, "%s\n相机硬件不稳定？请联系设备厂家或重试。", message))
                .setCancelable(false)
                .setNegativeButton("取消", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        closeCamera();
                    }
                })
                .setPositiveButton("重试", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        initAndStartPreview(((TextureView) viewCache.get("display")).getSurfaceTexture());
                    }
                }).show();
    }

    private void initCameraParams() {
        Camera.Parameters parameters = mCamera.getParameters();
        //show flash button or not
        List<String> supportedFlashModes = parameters.getSupportedFlashModes();
        boolean supported = supportedFlashModes != null && supportedFlashModes.contains(Camera.Parameters.FLASH_MODE_TORCH);
        viewCache.get("flashTorch").setVisibility(supported ? View.VISIBLE : View.GONE);

        parameters.setRecordingHint(false);
        parameters.setPreviewFormat(ImageFormat.NV21);
        Camera.Size size = isLandscape() ? getBestLandscapeSupportedSize(parameters.getSupportedPreviewSizes(), vw, vh)
                : getBestPortraitSupportedSize(parameters.getSupportedPreviewSizes(), vw, vh);
        if (size == null) {
            //didn't find the suitable preview size
            //tell user not support.
            mCamera.release();
            mCamera = null;
            new AlertDialog.Builder(getActivity())
                    .setTitle("设备不支持")
                    .setMessage("此设备不具备特定的分辨率比例(16:9、4:3、1:1)。")
                    .setPositiveButton("我知道了", null)
                    .setCancelable(false)
                    .show();
            return;
        }
        previewWidth = size.width;
        previewHeight = size.height;
        parameters.setPreviewSize(previewWidth, previewHeight);
        //对焦模式设置
        List<String> supportedFocusModes = parameters.getSupportedFocusModes();
        if (supportedFocusModes != null && supportedFocusModes.size() > 0) {
            if (supportedFocusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            } else if (supportedFocusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
                parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
            } else if (supportedFocusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
                parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
            }
        }
        isAutoFocusMode = Camera.Parameters.FOCUS_MODE_AUTO.equals(parameters.getFocusMode());
        mCamera.setParameters(parameters);
        mCamera.setPreviewCallback(previewCallback);
        onConfigPreviewSize(previewWidth, previewHeight);
    }

    private void sendAutoFocusMessage(long delay) {
        if (mHandler != null) {
            mHandler.removeMessages(MSG_ID_FOCUS);
            mHandler.sendEmptyMessageDelayed(MSG_ID_FOCUS, delay);
        }
    }

    /**
     * Camera的对焦模式：
     * FOCUS_MODE_AUTO
     * 自动对焦模式，应用需要调用autoFocus(AutoFocusCallback)开始对焦，只会对焦一次，对焦成功会有回调。
     * FOCUS_MODE_INFINITY
     * 无穷对焦模式，应用很少，不能调用autoFocus(AutoFocusCallback)方法。
     * FOCUS_MODE_MACRO
     * 特写镜头对焦模式，应用需要调用autoFocus(AutoFocusCallback)开始对焦
     * FOCUS_MODE_FIXED
     * 固定焦点模式，焦点不可调用时都是在这种模式，如果Camera能够自动对焦，这种模式会固定焦点，通常应用于超焦距对焦。这种模式不能调用autoFocus(AutoFocusCallback)。
     * FOCUS_MODE_EDOF
     * 扩展景深模式
     * FOCUS_MODE_CONTINUOUS_VIDEO
     * 连续自动对焦模式，主要用于录制视频过程中，Camera会不断地尝试聚焦，这是录制视频时对焦模式的最好选择，在设置了Camera的参数后就开始自动对焦，但是调用takePicture时不一定已经对焦完成。
     * FOCUS_MODE_CONTINUOUS_PICTURE
     * 这种模式是对 FOCUS_MODE_CONTINUOUS_VIDEO连续自动对焦应用于拍照的扩展。Camera会不停的尝试连续对焦，对焦频率会比FOCUS_MODE_CONTINUOUS_VIDEO频繁，当设置了camera参数后开始对焦。
     * 注意:
     * 如果想要重新开始自动聚焦，需要首先调用cancelAutoFocus，然后设置自动对焦模式，在调用autoFocus（AutoFocusCallback）
     * 该模式下可调用autoFocus(AutoFocusCallback)，如果当前正在对焦扫描，focus回调函数将在它完成对焦是回调；如果没有正在对焦扫描，将立即放回。autoFocus函数调用后对焦区域是固定的，
     * 如果应用想要重新开启自动连续对焦，需要首先调用cancelAutoFocus，重新开始预览无法开启自动连续对焦，需要重新调用autoFocus，如果想要停止自动连续对焦，应用可以修改对焦模式。
     * FOCUS_MODE_AUTO，FOCUS_MODE_CONTINUOUS_VIDEO，FOCUS_MODE_CONTINUOUS_PICTURE通常较为常用。
     * 对焦的意义就是在手机晃动，移动或者改变位置时，拍摄画面依然清晰，如果不进行对焦则画面会很模糊
     */
    private void autoFocus() {
        if (mCamera != null) {
            mCamera.autoFocus(focusCallback);
        }
    }

    private int calCameraDisplayOrientation(int mVideoRotation) {
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(mCameraId, info);
        int result = 0;
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + mVideoRotation) % 360;
            result = (360 - result) % 360;
        } else {
            result = (info.orientation + 360 - mVideoRotation) % 360;
        }
        text("cameraParams",
                false,
                new StringBuilder()
                        .append("摄\u2000像\u2000头:").append(mCameraId == Camera.CameraInfo.CAMERA_FACING_FRONT ? "前置" : "后置")
                        .append("\n")
                        .append("相机旋转:")
                        .append(info.orientation)
                        .append("\n")
                        .append("视频旋转:")
                        .append(result)
                        .append("\n")
                        .append("帧\u2000旋\u2000转:")
                        .append(mFrameRotation));
        return result;
    }

    /**
     * 计算横屏最佳分辨率
     *
     * @param sizes
     * @param viewWidth
     * @param viewHeight
     * @return
     */
    public Camera.Size getBestLandscapeSupportedSize(List<Camera.Size> sizes, final int viewWidth, final int viewHeight) {
        if (sizes == null || sizes.isEmpty() || viewWidth * viewHeight == 0) {
            return null;
        }
        //计算与16:9、4:3、1:1最相近的的比例
        final float ratio = vw * 1.0f / vh;
        List<VideoRatio> list = new ArrayList<>();
        list.add(new VideoRatio(16, 9));
        list.add(new VideoRatio(4, 3));
        list.add(new VideoRatio(1, 1));
        Collections.sort(list, new Comparator<VideoRatio>() {
            @Override
            public int compare(VideoRatio o1, VideoRatio o2) {
                float val1 = Math.abs(o1.width * 1.0f / o1.height - ratio);
                float val2 = Math.abs(o2.width * 1.0f / o2.height - ratio);
                if (val1 < val2) {
                    return -1;
                } else if (val1 > val2) {
                    return 1;
                }
                return 0;
            }
        });

        //集合3种比例的分辨率
        List<Camera.Size> list169 = new ArrayList<>();
        List<Camera.Size> list43 = new ArrayList<>();
        List<Camera.Size> list11 = new ArrayList<>();
        Map<String, List<Camera.Size>> map = new HashMap<>();
        map.put("16_9", list169);
        map.put("4_3", list43);
        map.put("1_1", list11);
        for (Camera.Size size : sizes) {
            if (isEqualRatio(size, 16, 9)) {
                list169.add(size);
            }
            if (isEqualRatio(size, 4, 3)) {
                list43.add(size);
            }
            if (isEqualRatio(size, 1, 1)) {
                list11.add(size);
            }
        }
        //升序排序
        Comparator<Camera.Size> comparator = new Comparator<Camera.Size>() {
            @Override
            public int compare(Camera.Size o1, Camera.Size o2) {
                int val1 = o1.width * o1.height;
                int val2 = o2.width * o2.height;
                if (val1 < val2) {
                    return -1;
                } else if (val1 > val2) {
                    return 1;
                }
                return 0;
            }
        };
        Collections.sort(list169, comparator);
        Collections.sort(list43, comparator);
        Collections.sort(list11, comparator);

        for (int i = 0; i < 3; i++) {
            mRatio = list.get(i);
            String key = mRatio.key();
            List<Camera.Size> tempList = map.get(key);
            if (tempList == null) continue;
            if (tempList.size() == 0) continue;
            return latestSupportedSize(tempList);
        }

        mRatio = null;
        return null;
    }

    /**
     * 计算竖屏最佳分辨率
     *
     * @param sizes
     * @param viewWidth
     * @param viewHeight
     * @return
     */
    public Camera.Size getBestPortraitSupportedSize(List<Camera.Size> sizes, final int viewWidth, final int viewHeight) {
        if (sizes == null || sizes.isEmpty() || viewWidth * viewHeight == 0) {
            return null;
        }
        //计算与9:16、3:4、1:1最相近的的比例
        final float ratio = vw * 1.0f / vh;
        List<VideoRatio> list = new ArrayList<>();
        list.add(new VideoRatio(9, 16));
        list.add(new VideoRatio(3, 4));
        list.add(new VideoRatio(1, 1));
        Collections.sort(list, new Comparator<VideoRatio>() {
            @Override
            public int compare(VideoRatio o1, VideoRatio o2) {
                float val1 = Math.abs(o1.width * 1.0f / o1.height - ratio);
                float val2 = Math.abs(o2.width * 1.0f / o2.height - ratio);
                if (val1 < val2) {
                    return -1;
                } else if (val1 > val2) {
                    return 1;
                }
                return 0;
            }
        });

        //集合3种比例的分辨率
        List<Camera.Size> list916 = new ArrayList<>();
        List<Camera.Size> list34 = new ArrayList<>();
        List<Camera.Size> list11 = new ArrayList<>();
        Map<String, List<Camera.Size>> map = new HashMap<>();
        map.put("9_16", list916);
        map.put("3_4", list34);
        map.put("1_1", list11);
        for (Camera.Size size : sizes) {
            if (isEqualRatio(size, 16, 9)) {
                list916.add(size);
            }
            if (isEqualRatio(size, 4, 3)) {
                list34.add(size);
            }
            if (isEqualRatio(size, 1, 1)) {
                list11.add(size);
            }
        }
        //升序排序
        Comparator<Camera.Size> comparator = new Comparator<Camera.Size>() {
            @Override
            public int compare(Camera.Size o1, Camera.Size o2) {
                int val1 = o1.width * o1.height;
                int val2 = o2.width * o2.height;
                if (val1 < val2) {
                    return -1;
                } else if (val1 > val2) {
                    return 1;
                }
                return 0;
            }
        };
        Collections.sort(list916, comparator);
        Collections.sort(list34, comparator);
        Collections.sort(list11, comparator);

        for (int i = 0; i < 3; i++) {
            mRatio = list.get(i);
            String key = mRatio.key();
            List<Camera.Size> tempList = map.get(key);
            if (tempList == null) continue;
            if (tempList.size() == 0) continue;
            return latestSupportedSize(tempList);
        }

        mRatio = null;
        return null;
    }

    private Camera.Size latestSupportedSize(List<Camera.Size> list) {
        List<Camera.Size> copy = new ArrayList<>(list);
        Point outSize = new Point();
        WindowManager wm = (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);
        wm.getDefaultDisplay().getRealSize(outSize);
        final int max = Math.max(outSize.x, outSize.y);
        final int min = Math.min(outSize.x, outSize.y);
        Collections.sort(copy, new Comparator<Camera.Size>() {
            @Override
            public int compare(Camera.Size o1, Camera.Size o2) {
                int val1 = (o1.width - max) + (o1.height - min);
                int val2 = (o2.width - max) + (o2.height - min);
                if (val1 < val2) {
                    return -1;
                } else if (val1 > val2) {
                    return 1;
                }
                return 0;
            }
        });
        for (Camera.Size size : copy) {
            int val = (size.width - max) + (size.height - min);
            if (val >= 0) return size;
        }
        return list.get(list.size() - 1);
    }

    private boolean isEqualRatio(Camera.Size size, int wr, int hr) {
        return size.width * hr == size.height * wr;
    }

    private void setSurface(SurfaceTexture surface) {
        if (mCamera != null) {
            try {
                mCamera.setPreviewTexture(surface);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void closeCamera() {
        if (mCamera != null) {
            mCamera.setPreviewCallback(null);
        }
        stopPreview();
        releaseCamera();
        if (callback != null) {
            callback.onCameraClosed();
        }
    }

    public void setCallback(CameraLifeCycleCallback callback) {
        this.callback = callback;
    }

    public void startPreview() {
        if (mCamera != null) {
            try {
                mCamera.setPreviewCallback(previewCallback);
                mCamera.startPreview();
            } catch (Exception e) {
                Toast.makeText(getContext(), "打开相机预览失败：请检查系统自带相机是否可用。", Toast.LENGTH_SHORT).show();
            }
        }
    }

    public void stopPreview() {
        if (mCamera != null) {
            mCamera.setPreviewCallback(null);
            mCamera.stopPreview();
        }
    }

    private void releaseCamera() {
        if (mCamera != null) {
            mCamera.setPreviewCallback(null);
            mCamera.release();
            mCamera = null;
        }
    }

    protected void onConfigPreviewSize(int previewWidth, int previewHeight) {
        text("solution", false, String.format(Locale.US, "%1d x %2d", previewWidth, previewHeight));
        int multi0 = vw / mRatio.width;
        int multi1 = vh / mRatio.height;
        int multi = Math.min(multi0, multi1);
        int tvw = mRatio.width * multi;
        int tvh = mRatio.height * multi;
        resize("display", tvw, tvh, true);
        resize("mask", tvw, tvh, true);
        mCameraMaskBuilder.frameSize(previewWidth, previewHeight);
        mCameraMaskBuilder.viewSize(tvw, tvh);
        updateMaskShapeSelectedState(mCameraMaskBuilder.maskShape);
        updateMaskEdgeSelectedState(mCameraMaskBuilder.maskEdgeStyle);
        updateMaskUI(tvw, tvh);
    }

    private void updateMaskUI(int tvw, int tvh) {
        if (callback != null) {
            callback.onCreateMask(isLandscape(), mCameraMaskBuilder);
        }
        image("mask", mCameraMaskBuilder.build(getContext(), isLandscape()));
        updateInnerTipsViewLocation(vh, tvh, mCameraMaskBuilder.getTransparentRect());
        if (callback != null) {
            callback.onMaskCreated(isLandscape(), mCameraMaskBuilder);
        }

        //>>>update scanner ui
        View scannerContainerView = viewCache.get("scannerContainer");
        View scannerView = viewCache.get("scanner");
        if (scannerContainerView == null || scannerView == null) {
            return;
        }
        //stop animation first
        Animation animation = scannerView.getAnimation();
        if (animation != null) {
            animation.cancel();
            scannerView.setAnimation(null);
        }
        //update scanner container's visibility
        boolean visible = getArguments() != null && getArguments().getBoolean("withScanAnim", false);
        visible = visible && (mCameraMaskBuilder.isCircle() || mCameraMaskBuilder.isSquare() || mCameraMaskBuilder.isRectangle());
        scannerContainerView.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (!visible) return;

        Rect rect = mCameraMaskBuilder.getTransparentRect();
        Bitmap target = null;
        try {
            Bitmap segment = BitmapFactory.decodeStream(getContext().getAssets().open("c_default_scanner_bar.png"));
            assert segment != null;
            int maxBitmapCount = rect.width() / segment.getWidth();
            int rest = rect.width() % segment.getWidth();
            if (rest > 0) {
                maxBitmapCount++;
            }
            int x = 0;
            target = Bitmap.createBitmap(rect.width(), segment.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(target);
            for (int i = 0; i < maxBitmapCount; i++) {
                canvas.drawBitmap(segment, x, 0, null);
                x += segment.getWidth();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        //update scanner container's location
        ViewGroup.LayoutParams containerParams = scannerContainerView.getLayoutParams();
        containerParams.width = rect.width();
        containerParams.height = rect.height();
        if (containerParams instanceof ViewGroup.MarginLayoutParams) {
            ((ViewGroup.MarginLayoutParams) containerParams).topMargin = (vh - tvh) / 2 + rect.top;
        }
        scannerContainerView.setLayoutParams(containerParams);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            scannerContainerView.setClipToOutline(mCameraMaskBuilder.isCircle());
            if (mCameraMaskBuilder.isCircle()) {
                scannerContainerView.setOutlineProvider(new ViewOutlineProvider() {
                    @Override
                    public void getOutline(View view, Outline outline) {
                        outline.setOval(new Rect(0, 0, view.getWidth(), view.getHeight()));
                    }
                });
            }
        }
        //update scanner's src and it's size.
        ((ImageView) scannerView).setImageBitmap(target);
        ViewGroup.LayoutParams params = scannerView.getLayoutParams();
        params.width = containerParams.width;
        params.height = target == null ? ViewGroup.LayoutParams.WRAP_CONTENT : target.getHeight();
        scannerView.setLayoutParams(params);
        scannerView.setTranslationY(0f);
        TranslateAnimation translateAnimation = new TranslateAnimation(
                0f, 0f,
                -params.height, rect.height());
        translateAnimation.setDuration(2_500);
        translateAnimation.setRepeatCount(Animation.INFINITE);
        scannerView.setAnimation(translateAnimation);
        translateAnimation.start();
    }

    public final void updateMaskUIAgain() {
        updateMaskUI(mCameraMaskBuilder.getViewWidth(), mCameraMaskBuilder.getViewHeight());
    }

    public final boolean isLandscape() {
        return getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    public interface CameraLifeCycleCallback {

        void onViewCreated(Map<String, View> viewCache);

        /**
         * It will be called before opening camera.
         */
        void onOpenCamera();

        /**
         * No camera?<br> Not support?
         */
        void notFoundCamera();

        void onBack();

        /**
         * 创建带镂空层的相机遮罩。
         * Called in main thread.
         *
         * @param isLandscape
         * @param builder     遮罩层Bitmap构建器
         */
        void onCreateMask(boolean isLandscape, final CameraMaskBuilder builder);

        /**
         * 成功创建带镂空层的相机遮罩。
         *
         * @param isLandscape 屏幕方向
         * @param builder     遮罩构建器
         */
        void onMaskCreated(boolean isLandscape, final CameraMaskBuilder builder);

        /**
         * Every frame call back.
         *
         * @param facing
         * @param mirror
         * @param mVideoOrientation
         * @param mFrameOrientation
         * @param yuvData           frame data-nv21 format
         * @param width             frame width
         * @param height            frame height
         */
        void frame(int facing, boolean mirror, int mVideoOrientation, int mFrameOrientation, byte[] yuvData, int width, int height);

        /**
         * Every frame call back.
         *
         * @param facing
         * @param displayOrientation
         * @param mFrameOrientation
         * @param yuvData            frame data-nv21 format
         * @param width              frame width
         * @param height             frame height
         */
        void onShutter(int facing, int displayOrientation, int mFrameOrientation, byte[] yuvData, int width, int height);

        /**
         * Call back after closing camera.
         */
        void onCameraClosed();
    }

    public static class CameraFrame {
        public byte[] nv21;
        public int width;
        public int height;

        public CameraFrame() {

        }

        public CameraFrame(byte[] nv21, int width, int height) {
            this.nv21 = nv21;
            this.width = width;
            this.height = height;
        }
    }

    private static class VideoRatio {
        int width;
        int height;

        public VideoRatio(int width, int height) {
            this.width = width;
            this.height = height;
        }

        public String key() {
            return width + "_" + height;
        }
    }
}