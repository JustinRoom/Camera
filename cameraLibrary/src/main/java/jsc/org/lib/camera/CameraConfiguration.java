package jsc.org.lib.camera;

import android.os.Parcel;
import android.os.Parcelable;

public final class CameraConfiguration implements Parcelable {

    /**前置摄像头：画面额外旋转角度*/
    public int frontExtraDisplayOri;
    /**后置摄像头：画面额外旋转角度*/
    public int backgroundExtraDisplayOri;

    public CameraConfiguration() {
    }

    public CameraConfiguration(Parcel in) {
        frontExtraDisplayOri = in.readInt();
        backgroundExtraDisplayOri = in.readInt();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(frontExtraDisplayOri);
        dest.writeInt(backgroundExtraDisplayOri);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<CameraConfiguration> CREATOR = new Creator<CameraConfiguration>() {
        @Override
        public CameraConfiguration createFromParcel(Parcel in) {
            return new CameraConfiguration(in);
        }

        @Override
        public CameraConfiguration[] newArray(int size) {
            return new CameraConfiguration[size];
        }
    };
}
