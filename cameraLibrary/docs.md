# Camera
相机封装。调用快捷方便。
支持圆形，正方形，长方形，人脸形等镂空区域。
支持扫描动画。

### 1、调用相机
```
    private Bitmap bitmap = null;
    private boolean processing = false;
    private final Rect mValidRect = new Rect();
    private final YuvFrame mFrameCache = new YuvFrame();
    private final YuvFrame mFrameResult = new YuvFrame();
    private byte[] yuvResult = null;
    private final ExecutorService mService = Executors.newSingleThreadExecutor();
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    private void showCamera() {
        Bundle arguments = new Bundle();
        CameraConfig config = new CameraConfig();
        config.cameraId = 0;
        config.frontExtraDisplayOri = 0;
        config.backgroundExtraDisplayOri = 0;
        config.previewWidth = 1280;
        config.previewHeight = 720;
        config.previewDelay = 200L;
        config.frameCallbackDelay = 400L;
        config.enableScanAnim = true;
        arguments.putParcelable("config", config);
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
            public void onCameraSwitched(int cameraId) {

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
            public void frame(int cameraId, boolean mirror, int rotation, byte[] yuvData, int width, int height) {

            }

            @Override
            public void onShutter(int cameraId, int rotation, byte[] yuvData, int width, int height) {
                if (!processing) {
                    processing = true;
                    SoundPoolPlayer.getInstance().playShutterVoice();
                    mFrameCache.facing = cameraId;
                    mFrameCache.width = width;
                    mFrameCache.height = height;
                    mFrameCache.frameRotation = rotation;
                    mFrameCache.clipRect = new Rect(mValidRect);
                    mFrameCache.mirror = cameraId;
                    if (mFrameCache.yuvData == null || mFrameCache.yuvData.length != yuvData.length) {
                        mFrameCache.yuvData = new byte[yuvData.length];
                    }
                    System.arraycopy(yuvData, 0, mFrameCache.yuvData, 0, yuvData.length);
                    mService.submit(new Runnable() {
                        @Override
                        public void run() {
                            if (yuvResult == null || yuvResult.length != mFrameCache.yuvData.length) {
                                yuvResult = new byte[mFrameCache.yuvData.length];
                            }
                            if (mFrameCache.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                                YuvUtils.mirrorYuv420(mFrameCache.yuvData, mFrameCache.width, mFrameCache.height);
                            }
                            YuvUtils.rotateYuv420(mFrameCache.yuvData, mFrameCache.width, mFrameCache.height, mFrameCache.frameRotation, yuvResult);
                            boolean interchanged = (mFrameCache.frameRotation / 90) % 2 == 1;
                            if (interchanged) {
                                YuvUtils.clipYuv420(yuvResult, mFrameCache.height, mFrameCache.width, mFrameCache.clipRect, mFrameResult);
                            } else {
                                YuvUtils.clipYuv420(yuvResult, mFrameCache.width, mFrameCache.height, mFrameCache.clipRect, mFrameResult);
                            }
                            bitmap = YuvTransfer.getInstance().transfer(mFrameResult);
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
```