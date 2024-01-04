package jsc.org.lib.camera;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;

import java.io.IOException;

public final class CameraMaskBuilder {
    public static final int SHAPE_NONE = -1;
    public static final int SHAPE_CIRCLE = 0;
    public static final int SHAPE_SQUARE = 1;
    public static final int SHAPE_RECTANGLE = 2;
    public static final int SHAPE_DRAWABLE = 3;
    public static final int EDGE_STYLE_NONE = -1;
    public static final int EDGE_STYLE_DEFAULT = 0;
    public static final int EDGE_STYLE_ANGLE = 1;
    public static final int EDGE_OUTLINE_SOURCE = 1;
    private int frameWidth;         //帧宽度
    private int frameHeight;        //帧高度
    private int viewWidth;          //宽度
    private int viewHeight;         //高度
    public int color = 0xCC000000;              //遮罩层颜色
    public int maskShape = SHAPE_CIRCLE;          //镂空形状
    public float transparentRatio = 0.75f; //镂空最小边占比
    public Bitmap bitmap = null;          //镂空区域绘制位图
    public int maskEdgeStyle = EDGE_STYLE_NONE;      //镂空边缘类型
    public int maskEdgeOutline;    //镂空中间轮廓
    public int maskEdgeOutlineColor = 0x80000000; //镂空中间轮廓边缘颜色
    public float maskEdgeWidth = 4.0f;      //镂空边缘宽度
    public int maskEdgeColor = 0xFF4477FC;        //镂空边缘颜色
    private final Rect transparentRect = new Rect(); //镂空坐标
    private final Rect transparentRectInFrame = new Rect();//帧镂空坐标

    public CameraMaskBuilder() {

    }

    public int getFrameWidth() {
        return frameWidth;
    }

    public int getFrameHeight() {
        return frameHeight;
    }

    public int getViewWidth() {
        return viewWidth;
    }

    public int getViewHeight() {
        return viewHeight;
    }

    public Rect getTransparentRect() {
        return transparentRect;
    }

    public Rect getTransparentRectInFrame() {
        return transparentRectInFrame;
    }

    public void frameSize(int frameWidth, int frameHeight) {
        this.frameWidth = frameWidth;
        this.frameHeight = frameHeight;
    }

    public void viewSize(int viewWidth, int viewHeight) {
        this.viewWidth = viewWidth;
        this.viewHeight = viewHeight;
    }

    public boolean isCircle() {
        return maskShape == SHAPE_CIRCLE;
    }

    public boolean isSquare() {
        return maskShape == SHAPE_SQUARE;
    }

    public boolean isRectangle() {
        return maskShape == SHAPE_RECTANGLE;
    }

    private void transparentArea(boolean isLandscape) {
        Point outSize = new Point();
        switch (maskShape) {
            case SHAPE_CIRCLE:
            case SHAPE_SQUARE:
                transparentArea11(isLandscape, outSize);
                break;
            case SHAPE_RECTANGLE:
            case SHAPE_DRAWABLE:
                transparentArea34(isLandscape, outSize);
                break;
            case SHAPE_NONE:
                transparentRect.set(0, 0, 0, 0);
                transparentRectInFrame.set(0, 0, 0, 0);
                break;
        }
        if (outSize.x > 0 && outSize.y > 0) {
            int left = (viewWidth - outSize.x) / 2;
            int top = (viewHeight - outSize.y) / 2;
            Rect rect = new Rect(left, top, left + outSize.x, top + outSize.y);
            transparentRect.set(
                    Math.max(0, rect.left),
                    Math.max(0, rect.top),
                    Math.min(viewWidth, rect.right),
                    Math.min(viewHeight, rect.bottom));
        }
    }

    //固定裁剪比例为1:1
    private void transparentArea11(boolean isLandscape, Point outSize) {
        //固定裁剪比例为1:1
        int realSize;
        int left, top;
        if (isLandscape) {
            //取高度的特定比例
            int cs = (int) (viewHeight * transparentRatio);
            //取4的整数倍
            cs = cs / 4 * 4;
            if (outSize != null) {
                outSize.x = cs;
                outSize.y = cs;
            }

            //取高度的特定比例
            realSize = (int) (frameHeight * transparentRatio);
            //取4的整数倍
            realSize = realSize / 4 * 4;
            left = (frameWidth - realSize) / 2;
            top = (frameHeight - realSize) / 2;
        } else {
            //取宽度的特定比例
            int cs = (int) (viewWidth * transparentRatio);
            //取4的整数倍
            cs = cs / 4 * 4;
            if (outSize != null) {
                outSize.x = cs;
                outSize.y = cs;
            }

            //取高度的特定比例
            realSize = (int) (frameHeight * transparentRatio);
            //取4的整数倍
            realSize = realSize / 4 * 4;
            left = (frameHeight - realSize) / 2;
            top = (frameWidth - realSize) / 2;
        }
        transparentRectInFrame.set(left, top, left + realSize, top + realSize);
    }

    //固定裁剪比例为3:4
    private void transparentArea34(boolean isLandscape, Point outSize) {
        //固定裁剪比例为3:4
        int realWidth, realHeight;
        int left, top;
        if (isLandscape) {
            //取高度的特定比例
            int ch = (int) (viewHeight * transparentRatio);
            int cw = ch * 3 / 4;
            //四舍五入？
            cw = cw % 12 > 0 ? cw + 12 : cw;
            //宽取12的整数倍
            cw = cw / 12 * 12;
            ch = cw / 3 * 4;
            if (outSize != null) {
                outSize.x = cw;
                outSize.y = ch;
            }

            //取高度的特定比例
            realHeight = (int) (frameHeight * transparentRatio);
            realWidth = realHeight * 3 / 4;
            int multi = Math.min(realWidth / 3, realHeight / 4);
            if (multi % 4 > 2) {
                multi = multi + 1;
            }
            //取4的整数倍
            multi = multi / 4 * 4;
            realWidth = 3 * multi;
            realHeight = 4 * multi;
            left = (frameWidth - realWidth) / 2;
            top = (frameHeight - realHeight) / 2;
        } else {
            //取宽度的特定比例
            int cw = (int) (viewWidth * transparentRatio);
            //四舍五入？
            cw = cw % 12 > 0 ? cw + 12 : cw;
            int ch = cw * 4 / 3;
            if (outSize != null) {
                outSize.x = cw;
                outSize.y = ch;
            }

            //取宽度的特定比例
            realWidth = (int) (frameHeight * transparentRatio);
            realHeight = realWidth * 4 / 3;
            int multi = Math.min(realWidth / 3, realHeight / 4);
            if (multi % 4 > 2) {
                multi = multi + 1;
            }
            //取4的整数倍
            multi = multi / 4 * 4;
            realWidth = 3 * multi;
            realHeight = 4 * multi;
            left = (frameHeight - realWidth) / 2;
            top = (frameWidth - realHeight) / 2;
        }
        transparentRectInFrame.set(left, top, left + realWidth, top + realHeight);
    }

    public Bitmap build(Context context, boolean isLandscape) {
        if (viewWidth <= 0 || viewHeight <= 0) {
            throw new IllegalArgumentException("Invalid view size. See \"viewSize(int viewWidth, int viewHeight)\".");
        }
        transparentArea(isLandscape);
        if (maskShape == SHAPE_NONE) return null;

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(color);
        Bitmap target = Bitmap.createBitmap(viewWidth, viewHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(target);
        canvas.drawRect(0, 0, viewWidth, viewHeight, paint);

        switch (maskShape) {
            case SHAPE_NONE:
                break;
            case SHAPE_CIRCLE:
                float centerX = viewWidth / 2.0f;
                float centerY = viewHeight / 2.0f;
                float radius = Math.min(transparentRect.width(), transparentRect.height()) / 2.0f;
                paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
                canvas.drawCircle(centerX, centerY, radius, paint);
                if (maskEdgeStyle != EDGE_STYLE_NONE) {
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setXfermode(null);
                    paint.setColor(maskEdgeColor);
                    paint.setStrokeWidth(maskEdgeWidth);
                    radius = radius + maskEdgeWidth / 2;
                    canvas.drawCircle(centerX, centerY, radius, paint);
                }
                break;
            case SHAPE_SQUARE:
            case SHAPE_RECTANGLE:
                paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
                canvas.drawRect(transparentRect, paint);
                if (maskEdgeStyle != EDGE_STYLE_NONE) {
                    if (maskEdgeStyle == EDGE_STYLE_ANGLE) {
                        drawAngleEdge(canvas, paint);
                    } else {
                        drawDefaultEdge(canvas, paint);
                    }
                }
                if (maskShape == SHAPE_RECTANGLE && maskEdgeOutline == EDGE_OUTLINE_SOURCE) {
                    try {
                        int tempW = transparentRect.width();
                        int tempH = transparentRect.height();
                        Bitmap outline = BitmapFactory.decodeStream(context.getAssets().open("c_default_camera_mask_outline.png"));
                        Bitmap outlineNew = Bitmap.createBitmap(tempW, tempH, Bitmap.Config.ARGB_8888);
                        Canvas outlineCanvas = new Canvas(outlineNew);
                        float ratio = 480 * 1.0f / transparentRectInFrame.width();
                        int otw = (int) (tempW * ratio);
                        int ret = otw % 3;
                        otw = otw - ret;
                        int oth = otw / 3 * 4;
                        int x = (tempW - otw) / 2;
                        int y = (tempH - oth) / 2;
                        outlineCanvas.drawBitmap(outline, new Rect(0, 0, outline.getWidth(), outline.getHeight()), new Rect(x, y, x + otw, y + oth), null);
                        paint.setXfermode(null);
                        canvas.drawBitmap(outlineNew, transparentRect.left, transparentRect.top, null);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                break;
            case SHAPE_DRAWABLE:
                paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
                if (bitmap == null) {
                    try {
                        bitmap = BitmapFactory.decodeStream(context.getAssets().open("c_default_camera_mask.png"));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                if (bitmap != null) {
                    canvas.drawBitmap(bitmap, new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight()), transparentRect, paint);
                } else {
                    canvas.drawRect(transparentRect, paint);
                }
                if (maskEdgeStyle != EDGE_STYLE_NONE) {
                    if (maskEdgeStyle == EDGE_STYLE_ANGLE) {
                        drawAngleEdge(canvas, paint);
                    } else {
                        drawDefaultEdge(canvas, paint);
                    }
                }
                break;
        }
        return target;
    }

    private void drawDefaultEdge(Canvas canvas, Paint paint) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setXfermode(null);
        paint.setColor(maskEdgeColor);
        paint.setStrokeWidth(maskEdgeWidth);
        float half = maskEdgeWidth / 2;
        RectF rectF = new RectF();
        rectF.left = transparentRect.left - half;
        rectF.top = transparentRect.top - half;
        rectF.right = transparentRect.right + half;
        rectF.bottom = transparentRect.bottom + half;
        canvas.drawRect(rectF, paint);
    }

    private void drawAngleEdge(Canvas canvas, Paint paint) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setXfermode(null);
        paint.setColor(maskEdgeColor);
        paint.setStrokeWidth(maskEdgeWidth);
        float half = maskEdgeWidth / 2;
        float aw = Math.min(transparentRect.width(), transparentRect.height()) / 12.0f;
        PointF lt = new PointF(transparentRect.left - half, transparentRect.top - half);
        PointF rt = new PointF(transparentRect.right + half, transparentRect.top - half);
        PointF lb = new PointF(transparentRect.left - half, transparentRect.bottom + half);
        PointF rb = new PointF(transparentRect.right + half, transparentRect.bottom + half);
        Path path = new Path();
        //left top
        path.reset();
        path.moveTo(lt.x + aw, lt.y);
        path.lineTo(lt.x, lt.y);
        path.lineTo(lt.x, lt.y + aw);
        canvas.drawPath(path, paint);
        //right top
        path.reset();
        path.moveTo(rt.x - aw, rt.y);
        path.lineTo(rt.x, rt.y);
        path.lineTo(rt.x, rt.y + aw);
        canvas.drawPath(path, paint);
        //left bottom
        path.reset();
        path.moveTo(lb.x + aw, lb.y);
        path.lineTo(lb.x, lb.y);
        path.lineTo(lb.x, lb.y - aw);
        canvas.drawPath(path, paint);
        //right bottom
        path.reset();
        path.moveTo(rb.x - aw, rb.y);
        path.lineTo(rb.x, rb.y);
        path.lineTo(rb.x, rb.y - aw);
        canvas.drawPath(path, paint);
    }
}
