package jsc.org.lib.camera.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicYuvToRGB;
import android.renderscript.Type;

import androidx.annotation.NonNull;

import jsc.org.lib.camera.entry.YuvFrame;

/**
 * 避免临时创建大量的对象（可能会导致OutOfMemoryError）
 */
public class YuvTransfer {

    private static YuvTransfer instance = null;
    private RenderScript rs = null;
    private ScriptIntrinsicYuvToRGB yuvToRgbIntrinsic = null;
    private Type.Builder yuvType = null;
    private Type.Builder rgbaType = null;
    private int tw = 0;
    private int th = 0;
    private Bitmap outBitmap = null;

    private YuvTransfer() {
    }

    public static YuvTransfer getInstance() {
        //第一个判空（如果是空，就不必再进入同步代码块了，提升效率）
        if (instance == null) {
            //这里加锁，是为了防止多线程的情况下出现实例化多个对象的情况
            synchronized (YuvTransfer.class) {
                //第二个判空（如果是空，就实例化对象）
                if (instance == null) {
                    //新建实例
                    instance = new YuvTransfer();
                }
            }
        }
        return instance;
    }

    public void init(@NonNull Context context) {
        if (rs == null) {
            rs = RenderScript.create(context.getApplicationContext());
            yuvToRgbIntrinsic = ScriptIntrinsicYuvToRGB.create(rs, Element.U8_4(rs));
            yuvType = new Type.Builder(rs, Element.U8(rs));
            rgbaType = new Type.Builder(rs, Element.RGBA_8888(rs));
        }
    }

    public Bitmap transfer(YuvFrame frame) {
        return frame == null ? null : transfer(frame.yuvData, frame.width, frame.height);
    }

    public Bitmap transfer(byte[] yuvData, int width, int height) {
        if (yuvData == null || yuvData.length == 0) {
            return null;
        }

        yuvType.setX(yuvData.length);
        Allocation in = Allocation.createTyped(rs, yuvType.create(), Allocation.USAGE_SCRIPT);
        rgbaType.setX(width).setY(height);
        Allocation out = Allocation.createTyped(rs, rgbaType.create(), Allocation.USAGE_SCRIPT);
        in.copyFrom(yuvData);
        yuvToRgbIntrinsic.setInput(in);
        yuvToRgbIntrinsic.forEach(out);
        //bitmap不可用高频创建
        if (outBitmap == null) {
            tw = width;
            th = height;
            outBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        } else {
            if (tw != width || th != height) {
                tw = width;
                th = height;
                outBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            }
        }
        out.copyTo(outBitmap);

        in.destroy();
        out.destroy();
        return outBitmap;
    }

    private void destroy() {
        if (rs != null) {
            rs.destroy();
            rs = null;
        }
        if (yuvToRgbIntrinsic != null) {
            yuvToRgbIntrinsic.destroy();
            yuvToRgbIntrinsic = null;
        }
    }

    public static void destroyInstance() {
        if (instance != null) {
            instance.destroy();
            instance = null;
        }
    }
}
