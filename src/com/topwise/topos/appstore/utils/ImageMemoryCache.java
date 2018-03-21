package com.topwise.topos.appstore.utils;

import android.graphics.Bitmap;
import android.util.LruCache;

import java.lang.ref.SoftReference;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public class ImageMemoryCache {
    /**
     * 从内存读取数据速度是最快的，为了更大限度使用内存，这里使用了两层缓存。
     * 硬引用缓存不会轻易被回收，用来保存常用数据，不常用的转入软引用缓存。
     */
    private static final int SOFT_CACHE_SIZE = 50;  //软引用缓存容量
    private static LruCache<String, Bitmap> mLruCache;  //硬引用缓存
    private static LinkedHashMap<String, SoftReference<Bitmap>> mSoftCache;  //软引用缓存
                                                                                          
    public ImageMemoryCache() {
        int cacheSize = 1024 * 1024 * 50;
        mLruCache = new LruCache<String, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                if (value != null)
                    return value.getRowBytes() * value.getHeight() * 4;
                else
                    return 0;
            }
                                                                                          
            @Override
            protected void entryRemoved(boolean evicted, String key, Bitmap oldValue, Bitmap newValue) {
                if (oldValue != null)
                    // 硬引用缓存容量满的时候，会根据LRU算法把最近没有被使用的图片转入此软引用缓存
                    mSoftCache.put(key, new SoftReference<Bitmap>(oldValue));
            }
        };
        mSoftCache = new LinkedHashMap<String, SoftReference<Bitmap>>(SOFT_CACHE_SIZE, 0.75f, true) {
            private static final long serialVersionUID = 6040103833179403725L;
            @Override
            protected boolean removeEldestEntry(Entry<String, SoftReference<Bitmap>> eldest) {
                if (size() > SOFT_CACHE_SIZE){    
                    return true;
                }  
                return false; 
            }
        };
    }
    
    /**
     * 从缓存中获取图片
     */
    public Bitmap getBitmapFromCache(String url) {
        Bitmap bitmap;
        //先从硬引用缓存中获取
        synchronized (mLruCache) {
            bitmap = mLruCache.get(url);
            if (bitmap != null) {
                //如果找到的话，把元素移到LinkedHashMap的最前面，从而保证在LRU算法中是最后被删除
                mLruCache.remove(url);
                mLruCache.put(url, bitmap);
                return bitmap;
            }
        }
        //如果硬引用缓存中找不到，到软引用缓存中找
        synchronized (mSoftCache) { 
            SoftReference<Bitmap> bitmapReference = mSoftCache.get(url);
            if (bitmapReference != null) {
                bitmap = bitmapReference.get();
                if (bitmap != null) {
                    //将图片移回硬缓存
                    mLruCache.put(url, bitmap);
                    mSoftCache.remove(url);
                    return bitmap;
                } else {
                    mSoftCache.remove(url);
                }
            }
        }
        return null;
    } 
                                                                                  
    /**
     * 添加图片到缓存
     */
    public void addBitmapToCache(String url, Bitmap bitmap) {
        if (bitmap != null) {
            synchronized (mLruCache) {
                mLruCache.put(url, bitmap);
            }
        }
    }

    public void removeBitmapFromCache(String url) {
        synchronized (mLruCache) {
            Bitmap b = mLruCache.get(url);
            if (b != null) {
                b.recycle();
                b = null;
            }
            mLruCache.remove(url);
            SoftReference<Bitmap> sr = mSoftCache.get(url);
            if (sr != null) {
                Bitmap softBitmap = sr.get();
                if (softBitmap != null) {
                    softBitmap.recycle();
                    softBitmap = null;
                }
                sr.clear();
            }
            mSoftCache.remove(url);
        }
    }
                                                                                  
    public void clearCache() {
        Iterator it = mSoftCache.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry entry = (Map.Entry) it.next();
            String key = (String) entry.getKey();
            SoftReference<Bitmap> value = (SoftReference<Bitmap>) entry.getValue();
            if (mLruCache.get(key) == null && value != null) {
                Bitmap b = value.get();
                try {
                    b.recycle();
                } catch (Exception e) {
                }
                b = null;
                value.clear();
            }
        }
        mSoftCache.clear();
    }
}