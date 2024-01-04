package jsc.org.lib.camera.utils;

import android.graphics.Rect;
import android.hardware.Camera;

import androidx.annotation.NonNull;

import jsc.org.lib.camera.entry.YuvFrame;

public final class YuvUtils {

    public static void dealOriginalFrameIfN(@NonNull YuvFrame frame, byte[] yuvResult) {
        if (frame.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            mirrorYuv420(frame.yuvData, frame.width, frame.height);
        }
        rotateYuv420(frame.yuvData, frame.width, frame.height, frame.frameRotation, yuvResult);
        clipYuv420(frame.yuvData, frame.width, frame.height, frame.clipRect, frame);
    }

    public static void rotateYuv420(byte[] yuvData, int width, int height, int rotation, byte[] yuvResult) {
        int len = width * height * 3 / 2;
        if (yuvResult == null)
            throw new IllegalArgumentException("'yuvResult' mustn't be null.");
        if (yuvResult.length != len)
            throw new IllegalArgumentException("'yuvResult' has invalid length.");
        rotation = rotation % 360;
        //调整为顺时针旋转
        rotation = (360 + rotation) % 360;
        switch (rotation) {
            case 0:
                System.arraycopy(yuvData, 0, yuvResult, 0, len);
                break;
            case 90:
                rotateYuv420_90(yuvData, width, height, yuvResult);
                break;
            case 180:
                rotateYuv420_180(yuvData, width, height, yuvResult);
                break;
            case 270:
                rotateYuv420_270(yuvData, width, height, yuvResult);
                break;
        }
    }

    private static void rotateYuv420_270(byte[] yuvData, int width, int height, byte[] yuvResult) {
        // Rotate the Y luma
        int i = 0;
        for (int x = width - 1; x >= 0; x--) {
            for (int y = 0; y < height; y++) {
                yuvResult[i] = yuvData[y * width + x];
                i++;
            }
        }// Rotate the U and V color components
        i = width * height;
        for (int x = width - 1; x > 0; x = x - 2) {
            for (int y = 0; y < height / 2; y++) {
                yuvResult[i] = yuvData[(width * height) + (y * width) + (x - 1)];
                i++;
                yuvResult[i] = yuvData[(width * height) + (y * width) + x];
                i++;
            }
        }
    }

    private static void rotateYuv420_180(byte[] yuvData, int width, int height, byte[] yuvResult) {
        int i = 0;
        int count = 0;
        for (i = width * height - 1; i >= 0; i--) {
            yuvResult[count] = yuvData[i];
            count++;
        }
        for (i = width * height * 3 / 2 - 1; i >= width * height; i -= 2) {
            yuvResult[count++] = yuvData[i - 1];
            yuvResult[count++] = yuvData[i];
        }
    }

    private static void rotateYuv420_90(byte[] yuvData, int width, int height, byte[] yuvResult) {
        int i = 0;
        for (int x = 0; x < width; x++) {
            for (int y = height - 1; y >= 0; y--) {
                yuvResult[i] = yuvData[y * width + x];
                i++;
            }
        }
        i = width * height * 3 / 2 - 1;
        for (int x = width - 1; x > 0; x = x - 2) {
            for (int y = 0; y < height / 2; y++) {
                yuvResult[i] = yuvData[(width * height) + (y * width) + x];
                i--;
                yuvResult[i] = yuvData[(width * height) + (y * width) + (x - 1)];
                i--;
            }
        }
    }

    //>>>mirror
    public static void mirrorYuv420(byte[] yuvData, int width, int height) {
        int i;
        int left, right;
        byte temp;
        int startPos = 0;
// mirror Y
        for (i = 0; i < height; i++) {
            left = startPos;
            right = startPos + width - 1;
            while (left < right) {
                temp = yuvData[left];
                yuvData[left] = yuvData[right];
                yuvData[right] = temp;
                left++;
                right--;
            }
            startPos += width;
        }
// mirror U and V
        int offset = width * height;
        startPos = 0;
        for (i = 0; i < height / 2; i++) {
            left = offset + startPos;
            right = offset + startPos + width - 2;
            while (left < right) {
                temp = yuvData[left];
                yuvData[left] = yuvData[right];
                yuvData[right] = temp;
                left++;
                right--;
                temp = yuvData[left];
                yuvData[left] = yuvData[right];
                yuvData[right] = temp;
                left++;
                right--;
            }
            startPos += width;
        }
    }

    //>>>clip
    public static void clipYuv420(byte[] yuvData, int width, int height, Rect clipRect, YuvFrame result) {
        if (clipRect != null) {
            clipYuv420(yuvData, width, height, clipRect.left, clipRect.top, clipRect.width(), clipRect.height(), result);
        }
    }

    /**
     * NV21裁剪  算法效率 3ms
     *
     * @param yuvData 源数据
     * @param width   源宽
     * @param height  源高
     * @param left    顶点坐标
     * @param top     顶点坐标
     * @param clip_w  裁剪后的宽
     * @param clip_h  裁剪后的高
     * @return 裁剪后的数据
     */
    public static void clipYuv420(byte[] yuvData, int width, int height, int left, int top, int clip_w, int clip_h, YuvFrame result) {
        if (clip_w <= 0 || clip_h <= 0
                || left > width || top > height
                || left + clip_w > width
                || top + clip_h > height) {
            if (result != null) {
                result.yuvData = null;
                result.width = 0;
                result.height = 0;
            }
            return;
        }
        //取偶
        int x = left / 4 * 4, y = top / 4 * 4;
        int w = clip_w / 4 * 4, h = clip_h / 4 * 4;
        int y_unit = w * h;
        int uv = y_unit / 2;
        byte[] yuvResult = new byte[y_unit + uv];
        int uv_index_dst = w * h - y / 2 * w;
        int uv_index_src = width * height + x;
        for (int i = y; i < y + h; i++) {
            System.arraycopy(yuvData, i * width + x, yuvResult, (i - y) * w, w);//y内存块复制
            if (i % 2 == 0) {
                System.arraycopy(yuvData, uv_index_src + (i >> 1) * width, yuvResult, uv_index_dst + (i >> 1) * w, w);//uv内存块复制
            }
        }
        if (result != null) {
            result.yuvData = yuvResult;
            result.width = w;
            result.height = h;
        }
    }
}
