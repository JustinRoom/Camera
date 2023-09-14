package jsc.org.lib.camera.entry;

import android.graphics.Rect;

public final class YuvFrame {
    public int facing;
    public int frameRotation;
    public int mirror;
    public Rect clipRect;
    public byte[] yuvData;
    public int width;
    public int height;
}
