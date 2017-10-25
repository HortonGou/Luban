package top.zibin.luban;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.support.annotation.NonNull;
import android.support.media.ExifInterface;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Responsible for starting compress and managing active and cached resources.
 */
class Engine {
    private ExifInterface srcExif;
    private String srcImg;
    private File tagImg;
    private int srcWidth;
    private int srcHeight;

    Engine(String srcImg, File tagImg) throws IOException {
        if (Checker.isJPG(srcImg)) {
            this.srcExif = new ExifInterface(srcImg);
        }
        this.tagImg = tagImg;
        this.srcImg = srcImg;

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        options.inSampleSize = 1;

        BitmapFactory.decodeFile(srcImg, options);
        this.srcWidth = options.outWidth;
        this.srcHeight = options.outHeight;
    }

    private int computeSize() {
        srcWidth = srcWidth % 2 == 1 ? srcWidth + 1 : srcWidth;
        srcHeight = srcHeight % 2 == 1 ? srcHeight + 1 : srcHeight;

        int longSide = Math.max(srcWidth, srcHeight);
        int shortSide = Math.min(srcWidth, srcHeight);

        float scale = ((float) shortSide / longSide);
        if (scale <= 1 && scale > 0.5625) {
            if (longSide < 1664) {
                return 1;
            } else if (longSide >= 1664 && longSide < 4990) {
                return 2;
            } else if (longSide > 4990 && longSide < 10240) {
                return 4;
            } else {
                return longSide / 1280 == 0 ? 1 : longSide / 1280;
            }
        } else if (scale <= 0.5625 && scale > 0.5) {
            return longSide / 1280 == 0 ? 1 : longSide / 1280;
        } else {
            return (int) Math.ceil(longSide / (1280.0 / scale));
        }
    }

    private float calculateScaleSize(Bitmap bitmap) {
//        srcWidth = srcWidth % 2 == 1 ? srcWidth + 1 : srcWidth;
//        srcHeight = srcHeight % 2 == 1 ? srcHeight + 1 : srcHeight;

        int longSide = Math.max(bitmap.getWidth(), bitmap.getHeight());
        int shortSide = Math.min(bitmap.getWidth(), bitmap.getHeight());

        float scale = ((float) shortSide / longSide);
        if (scale >= 0.5f && longSide > 1280) {
            return 1280f / longSide;
        } else if (scale < 0.5f && shortSide > 1280) {
            return 1280f / shortSide;
        }
        return 1f;
    }

    private Bitmap getUnOutOfMemoryBitmap() {
        int inSampleSize = 1;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        options.inSampleSize = 1;

        BitmapFactory.decodeFile(srcImg, options);
        int srcWidth = options.outWidth;
        int srcHeight = options.outHeight;
        int w = srcWidth;
        int h = srcHeight;
        while (!canCreate(w, h)) {
            inSampleSize++;
            w = srcWidth / inSampleSize;
            h = srcHeight / inSampleSize;
        }
        BitmapFactory.Options options2 = new BitmapFactory.Options();
        options2.inSampleSize = inSampleSize;
        return BitmapFactory.decodeFile(srcImg, options2);
    }

    File compress() throws IOException {
        //采样
        Bitmap tagBitmap = getUnOutOfMemoryBitmap();
        //缩放
        float scale = calculateScaleSize(tagBitmap);
        Log.e("luban", "scale : " + scale);
        if (scale != 1f) {
            Matrix matrix = new Matrix();
            matrix.setScale(scale, scale);
            tagBitmap = transformBitmap(tagBitmap, matrix);
        }
        //旋转
        tagBitmap = rotatingImage(tagBitmap);
        //压缩
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        if (tagBitmap.getWidth() > 1280 || tagBitmap.getHeight() > 1280) {
            tagBitmap.compress(Bitmap.CompressFormat.JPEG, 70, stream);
        } else {
            tagBitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream);
        }

        tagBitmap.recycle();

        FileOutputStream fos = new FileOutputStream(tagImg);
        fos.write(stream.toByteArray());
        fos.flush();
        fos.close();
        stream.close();

        return tagImg;
    }

    private Bitmap transformBitmap(@NonNull Bitmap bitmap, @NonNull Matrix transformMatrix) {
        try {
            Bitmap converted = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), transformMatrix, false);
            if (!bitmap.sameAs(converted)) {
                bitmap = converted;
            }
        } catch (OutOfMemoryError error) {
            Log.e("luban", "transformBitmap: " + error);
        }
        return bitmap;
    }

    private boolean canCreate(int w, int h) {
        boolean canScale = checkMemory(w, h);
        if (!canScale) {
            Log.e("Luban", "no enough memory!");
        }
        return canScale;
    }

    private boolean checkMemory(int width, int height) {
        Runtime runtime = Runtime.getRuntime();
        long free = runtime.maxMemory() - runtime.totalMemory() + runtime.freeMemory();
        int allocation = (width * height) << 2;
        Log.d("Luban", "free : " + (free >> 20) + "MB, need : " + (allocation >> 20) + "MB");
        return allocation < free;
    }

    private Bitmap rotatingImage(Bitmap bitmap) {
        if (srcExif == null) return bitmap;

        Matrix matrix = new Matrix();
        int angle = 0;
        int orientation = srcExif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                angle = 90;
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                angle = 180;
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                angle = 270;
                break;
        }

        matrix.postRotate(angle);
        if (angle == 0) {
            return bitmap;
        } else {
            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        }

    }
}