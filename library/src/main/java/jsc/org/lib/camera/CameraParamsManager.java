package jsc.org.lib.camera;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.camera2.CaptureRequest;
import android.text.TextUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;

/**
 * <pre class="preprint">
 *   Copyright JiangShiCheng
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 * </pre>
 *
 * @author jsc
 * @createDate 2022/4/15
 */
public class CameraParamsManager {
    private static CameraParamsManager instance = null;
    public static final String CONFIG_VERSION = "configVersion";
    public static final String DEFAULT_CAMERA_ID = "defaultCameraId";
    public static final String PORT = "port";
    public static final String LAND = "land";
    public static final String VIDEO_ROTATION = "VideoRotation";
    public static final String VIDEO_MIRROR = "videoMirror";
    public static final String FRAME_MIRROR = "frameMirror";
    public static final String FRAME_ROTATION = "FrameRotation";
    public static final String FLASH_MODEL = "flashModel";

    private Context appContext;
    private SharedPreferences mPreferences = null;

    private CameraParamsManager() {
    }

    public static CameraParamsManager getInstance() {
        if (instance == null) {
            instance = new CameraParamsManager();
        }
        return instance;
    }

    public void init(Context context) {
        init(context, "defaultCameraParams.json");
    }

    public void init(Context context, String configFileName) {
        if (mPreferences == null) {
            appContext = context.getApplicationContext();
            mPreferences = appContext.getSharedPreferences("sp_camera_config.data", Context.MODE_PRIVATE);
        }
        String version = configFileName.replace(".json", "");
        String oldVersion = mPreferences.getString(CONFIG_VERSION, "");
        if (!oldVersion.equals(version)) {
            //read assets config file
            //special json format
            StringBuilder builder = new StringBuilder();
            try {
                InputStream is = context.getAssets().open(configFileName);
                InputStreamReader isr = new InputStreamReader(is);
                BufferedReader br = new BufferedReader(isr);
                String line = null;
                while ((line = br.readLine()) != null) {
                    builder.append(line.trim());
                }
                is.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            String json = builder.toString();
            if (TextUtils.isEmpty(json))
                throw new IllegalArgumentException("Invalid configurations.");

            try {
                SharedPreferences.Editor editor = mPreferences.edit();
                //clear all old configurations
                editor.clear();
                JSONObject obj = new JSONObject(json);
                editor.putString(DEFAULT_CAMERA_ID, obj.optString("defaultCameraId"));
                //port
                editor.putInt(videoRotationKey(PORT), obj.optInt("portVideoRotation"));
                editor.putInt(frameRotationKey(PORT, "0"), obj.optInt("portFrameRotation0"));
                editor.putInt(frameRotationKey(PORT, "1"), obj.optInt("portFrameRotation1"));
                //land
                editor.putInt(videoRotationKey(LAND), obj.optInt("landVideoRotation"));
                editor.putInt(frameRotationKey(LAND, "0"), obj.optInt("landFrameRotation0"));
                editor.putInt(frameRotationKey(LAND, "1"), obj.optInt("landFrameRotation1"));
                //video mirror
                editor.putInt(VIDEO_MIRROR + "0", obj.optInt("videoMirror0"));
                editor.putInt(VIDEO_MIRROR + "1", obj.optInt("videoMirror1"));
                //frame mirror
                editor.putInt(FRAME_MIRROR + "0", obj.optInt("frameMirror0"));
                editor.putInt(FRAME_MIRROR + "1", obj.optInt("frameMirror1"));

                editor.putInt(FLASH_MODEL, CaptureRequest.FLASH_MODE_OFF);
                editor.putString(CONFIG_VERSION, version);
                editor.apply();
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    private String videoRotationKey(String screenDirection) {
        return screenDirection + VIDEO_ROTATION;
    }

    private String frameRotationKey(String screenDirection, String mCameraId) {
        return screenDirection + FRAME_ROTATION + mCameraId;
    }

    public String getDefaultCameraId() {
        requireNotNull();
        return mPreferences.getString(DEFAULT_CAMERA_ID, "0");
    }

    public void updateDefaultCameraId(String mCameraId) {
        requireNotNull();
        mPreferences.edit().putString(DEFAULT_CAMERA_ID, mCameraId).apply();
    }

    public int getVideoRotation(String screenDirection) {
        requireNotNull();
        return mPreferences.getInt(videoRotationKey(screenDirection), 0);
    }

    public void updateVideoRotation(String screenDirection, int rotation) {
        requireNotNull();
        mPreferences.edit().putInt(videoRotationKey(screenDirection), rotation).apply();
    }

    public int getVideoMirror(String mCameraId) {
        requireNotNull();
        return mPreferences.getInt(VIDEO_MIRROR + mCameraId, 1);
    }

    public void updateVideoMirror(String mCameraId, int value) {
        requireNotNull();
        mPreferences.edit().putInt(VIDEO_MIRROR + mCameraId, value).apply();
    }

    public int getFrameMirror(String mCameraId) {
        requireNotNull();
        return mPreferences.getInt(FRAME_MIRROR + mCameraId, 1);
    }

    public void updateFrameMirror(String mCameraId, int value) {
        requireNotNull();
        mPreferences.edit().putInt(FRAME_MIRROR + mCameraId, value).apply();
    }

    public int getFrameRotation(String screenDirection, String mCameraId) {
        requireNotNull();
        return mPreferences.getInt(frameRotationKey(screenDirection, mCameraId), 0);
    }

    public void updateFrameRotation(String screenDirection, String mCameraId, int rotation) {
        requireNotNull();
        mPreferences.edit().putInt(frameRotationKey(screenDirection, mCameraId), rotation).apply();
    }

    public int getFlashModel() {
        requireNotNull();
        return mPreferences.getInt(FLASH_MODEL, CaptureRequest.FLASH_MODE_OFF);
    }

    public void updateFlashModel(int model) {
        requireNotNull();
        mPreferences.edit().putInt(FLASH_MODEL, model).apply();
    }

    public String getCustomCameraId(String key) {
        requireNotNull();
        return mPreferences.getString(key, getDefaultCameraId());
    }

    public void updateCustomCameraId(String key, String mCustomCameraId) {
        requireNotNull();
        mPreferences.edit().putString(key, mCustomCameraId).apply();
    }

    public void updateCustomDefaultCameraId(String key, String mCustomCameraId) {
        requireNotNull();
        if (!mPreferences.contains(key)) {
            mPreferences.edit().putString(key, mCustomCameraId).apply();
        }
    }

    public String getCustomCameraIds(String key) {
        requireNotNull();
        return mPreferences.getString(key, "0:1");
    }

    public void updateCustomCameraIds(String key, String mCustomCameraId) {
        requireNotNull();
        mPreferences.edit().putString(key, mCustomCameraId).apply();
    }

    public void saveParams2Local() {
        try {
            JSONObject obj = new JSONObject();
            obj.put("defaultCameraId", getDefaultCameraId());
            obj.put(videoRotationKey(PORT), getVideoRotation(PORT));
            obj.put(frameRotationKey(PORT, "0"), getFrameRotation(PORT, "0"));
            obj.put(frameRotationKey(PORT, "1"), getFrameRotation(PORT, "1"));
            obj.put(videoRotationKey(LAND), getVideoRotation(LAND));
            obj.put(frameRotationKey(LAND, "0"), getFrameRotation(LAND, "0"));
            obj.put(frameRotationKey(LAND, "1"), getFrameRotation(LAND, "1"));
            obj.put(VIDEO_MIRROR + "0", getVideoMirror("0"));
            obj.put(VIDEO_MIRROR + "1", getVideoMirror("1"));
            File dir = appContext.getExternalFilesDir("c1");
            File file = new File(dir, System.currentTimeMillis() + ".json");
            boolean cr = file.createNewFile();
            RandomAccessFile raf = new RandomAccessFile(file, "rwd");
            raf.write(obj.toString().getBytes());
            raf.close();
        } catch (JSONException | IOException e) {
            e.printStackTrace();
        }
    }

    private void requireNotNull() {
        if (mPreferences == null)
            throw new NullPointerException("Please call \"init(Context context)\" firstly.");
    }
}
