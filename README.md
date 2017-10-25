# Luban

[![Build Status](https://travis-ci.org/Curzibn/Luban.svg?branch=master)](https://travis-ci.org/Curzibn/Luban)
[ ![Download](https://api.bintray.com/packages/curzibn/maven/Luban/images/download.svg) ](https://bintray.com/curzibn/maven/Luban/_latestVersion)

<div align="right">
<a href="Translation/README-EN.md">:book: English Documentation</a>
</div>

`Luban`（鲁班） —— `Android`图片压缩工具，仿微信朋友圈压缩策略。
# 修改说明
这个fork对原有Luban进行了一些修改

1.引用support中的exifinterface包，原有的可能存在安全问题

2.大幅度调整压缩方法

原有方法
~~~
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
  File compress() throws IOException {
    BitmapFactory.Options options = new BitmapFactory.Options();
    options.inSampleSize = computeSize();

    Bitmap tagBitmap = BitmapFactory.decodeFile(srcImg, options);
    ByteArrayOutputStream stream = new ByteArrayOutputStream();

    tagBitmap = rotatingImage(tagBitmap);
    tagBitmap.compress(Bitmap.CompressFormat.JPEG, 60, stream);
    tagBitmap.recycle();

    FileOutputStream fos = new FileOutputStream(tagImg);
    fos.write(stream.toByteArray());
    fos.flush();
    fos.close();
    stream.close();

    return tagImg;
  }
~~~
本fork改动的部分
~~~
    private float calculateScaleSize(Bitmap bitmap) {
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
~~~
## 压缩方法调整说明
### 旧方法中的操作为：
宽高比1至0.5625时 ：

  当L<1664时不缩放（L代指长边）
  
  当1664<=L<4990时，2倍缩放。缩放的结果是使长边范围在832-2495之间
  
  当4990<L<10240时，4倍缩放。缩放的结果是使长边范围在1247.5-2560之间
  
  当L>10240时，n倍缩放，缩放结果使L<=1280；
  
宽高比0.5625至0.5时 ：当L<=1280时不缩放，当L>1280时n倍缩放，缩放结果使L<=1280

宽高比0.5至0时 ：（以S代指短边）当S<=1280时不缩放 当S>1280时n倍缩放，缩放结果使S<=1280

然后统一使用60%的质量压缩

### 调整的方法
原有方法在宽高比1至0.5625范围内多次调用会重复压缩到比较小的尺寸，这里为了简化代码和尽量保持图片的清晰，把这种情况与0.5625至0.5的部分合并

改为：

  宽高比1至0.5时 ：当L<=1280时不缩放，当L>1280时n倍缩放，缩放结果使L<=1280
  
  宽高比0.5至0时 ：（以S代指短边）当S<=1280时不缩放 当S>1280时n倍缩放，缩放结果使S<=1280
  
然后如果调整后仍旧有一边的长度大于1280，则使用70%质量压缩，否则使用90%质量压缩

# 项目描述

目前做`App`开发总绕不开图片这个元素。但是随着手机拍照分辨率的提升，图片的压缩成为一个很重要的问题。单纯对图片进行裁切，压缩已经有很多文章介绍。但是裁切成多少，压缩成多少却很难控制好，裁切过头图片太小，质量压缩过头则显示效果太差。

于是自然想到`App`巨头“微信”会是怎么处理，`Luban`（鲁班）就是通过在微信朋友圈发送近100张不同分辨率图片，对比原图与微信压缩后的图片逆向推算出来的压缩算法。

因为有其他语言也想要实现`Luban`，所以描述了一遍[算法步骤](/DESCRIPTION.md)。

因为是逆向推算，效果还没法跟微信一模一样，但是已经很接近微信朋友圈压缩后的效果，具体看以下对比！

# 效果与对比

内容 | 原图 | `Luban` | `Wechat`
---- | ---- | ------ | ------
截屏 720P |720*1280,390k|720*1280,87k|720*1280,56k
截屏 1080P|1080*1920,2.21M|1080*1920,104k|1080*1920,112k
拍照 13M(4:3)|3096*4128,3.12M|1548*2064,141k|1548*2064,147k
拍照 9.6M(16:9)|4128*2322,4.64M|1032*581,97k|1032*581,74k
滚动截屏|1080*6433,1.56M|1080*6433,351k|1080*6433,482k

# 导入

```sh
compile 'top.zibin:Luban:1.1.3'
```

# 使用

### 异步调用

`Luban`内部采用`IO`线程进行图片压缩，外部调用只需设置好结果监听即可：

```java
Luban.with(this)
        .load(photos)                                   // 传人要压缩的图片列表
        .ignoreBy(100)                                  // 忽略不压缩图片的大小
        .setTargetDir(getPath())                        // 设置压缩后文件存储位置
        .setCompressListener(new OnCompressListener() { //设置回调
          @Override
          public void onStart() {
            // TODO 压缩开始前调用，可以在方法内启动 loading UI
          }

          @Override
          public void onSuccess(File file) {
            // TODO 压缩成功后调用，返回压缩后的图片文件
          }

          @Override
          public void onError(Throwable e) {
            // TODO 当压缩过程出现问题时调用
          }
        }).launch();    //启动压缩
```

### 同步调用

同步方法请尽量避免在主线程调用以免阻塞主线程，下面以rxJava调用为例

```java
Flowable.just(photos)
    .observeOn(Schedulers.io())
    .map(new Function<List<String>, List<File>>() {
      @Override public List<File> apply(@NonNull List<String> list) throws Exception {
        // 同步方法直接返回压缩后的文件
        return Luban.with(MainActivity.this).load(list).get();
      }
    })
    .observeOn(AndroidSchedulers.mainThread())
    .subscribe();
```

# License

    Copyright 2016 Zheng Zibin
    
    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at
    
        http://www.apache.org/licenses/LICENSE-2.0
    
    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
