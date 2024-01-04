package jsc.org.lib.camera.entry;

import android.graphics.Rect;

public final class YuvFrame {
    public int facing = -1;
    public int frameRotation;
    public int mirror;
    public Rect clipRect = null;
    public byte[] yuvData = null;
    public int width;
    public int height;
}
