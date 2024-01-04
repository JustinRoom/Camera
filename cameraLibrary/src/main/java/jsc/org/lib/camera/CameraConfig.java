package jsc.org.lib.camera;

import android.os.Parcel;
import android.os.Parcelable;

public final class CameraConfig implements Parcelable {

    /**相机Id*/
    public int cameraId = 0;
    /**前置摄像头：画面额外旋转角度*/
    public int frontExtraDisplayOri;
    /**后置摄像头：画面额外旋转角度*/
    public int backgroundExtraDisplayOri;
    /**预览延迟*/
    public long previewDelay = 0L;
    /**帧回调延迟*/
    public long frameCallbackDelay = 0L;
    /**是否开启扫码动画:1->开启*/
    public boolean enableScanAnim = false;

    public CameraConfig() {
    }

    public CameraConfig(Parcel in) {
        cameraId = in.readInt();
        frontExtraDisplayOri = in.readInt();
        backgroundExtraDisplayOri = in.readInt();
        previewDelay = in.readLong();
        frameCallbackDelay = in.readLong();
        enableScanAnim = in.readInt() == 1;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(cameraId);
        dest.writeInt(frontExtraDisplayOri);
        dest.writeInt(backgroundExtraDisplayOri);
        dest.writeLong(previewDelay);
        dest.writeLong(frameCallbackDelay);
        dest.writeInt(enableScanAnim ? 1 : 0);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<CameraConfig> CREATOR = new Creator<CameraConfig>() {
        @Override
        public CameraConfig createFromParcel(Parcel in) {
            return new CameraConfig(in);
        }

        @Override
        public CameraConfig[] newArray(int size) {
            return new CameraConfig[size];
        }
    };
}
