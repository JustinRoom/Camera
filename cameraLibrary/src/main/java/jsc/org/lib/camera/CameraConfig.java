package jsc.org.lib.camera;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Size;

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
    /**指定相机分辨率:宽*/
    public int previewWidth;
    /**指定相机分辨率:高*/
    public int previewHeight;

    public CameraConfig() {
    }

    public CameraConfig(Parcel in) {
        cameraId = in.readInt();
        frontExtraDisplayOri = in.readInt();
        backgroundExtraDisplayOri = in.readInt();
        previewDelay = in.readLong();
        frameCallbackDelay = in.readLong();
        enableScanAnim = in.readInt() == 1;
        previewWidth = in.readInt();
        previewHeight = in.readInt();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(cameraId);
        dest.writeInt(frontExtraDisplayOri);
        dest.writeInt(backgroundExtraDisplayOri);
        dest.writeLong(previewDelay);
        dest.writeLong(frameCallbackDelay);
        dest.writeInt(enableScanAnim ? 1 : 0);
        dest.writeInt(previewWidth);
        dest.writeInt(previewHeight);
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
