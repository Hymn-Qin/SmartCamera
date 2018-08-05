package com.zzdc.abb.smartcamera.util;

import java.util.ArrayList;

public class BufferPool<T extends BufferPool.Buf> {
    private static final String TAG = BufferPool.class.getSimpleName();
    private int mMaxBufs = 10;
    private String mName = null;
    private boolean mDebug = false;
    private Class<T> mType;
    private ArrayList<T> mEmpty = new ArrayList<>();
    private ArrayList<T> mUsed = new ArrayList<>();

    public BufferPool(Class<T> type, int defaultCount) {
        mType = type;
        mName = mType.getSimpleName();
        for (int i=0; i<defaultCount; i++) {
            T buf = createBuf();
            if (buf != null) {
                buf.setPool(this);
                mEmpty.add(buf);
            }
        }
    }

    public synchronized T getBuf() {
        debug( "getBuf");
        T buf;
        if (mEmpty.size() > 0) {
            buf = mEmpty.remove(0);
        } else {
            if (mUsed.size() >= mMaxBufs) {
                buf = mUsed.remove(0);
            } else {
                buf = createBuf();
            }
        }
        if (buf != null) {
            mUsed.add(buf);
            buf.increaseRef();
        }
        dump();

        return buf;
    }

    public synchronized T getBuf(int size) {
        T buf = getBuf();
        buf.updateSize(size);
        return buf;
    }

    synchronized void backBuf(T buf) {
        debug("backBuf");
        if (buf != null) {
            if (mUsed.remove(buf)) {
                mEmpty.add(buf);
            } else {
                LogTool.w(TAG, mName + ". backBuf. The buffer is not belong to the pool.");
            }
        }
        dump();
    }

    public void setDebug(boolean debug) {
        mDebug = debug;
    }

    private T createBuf() {
        T buf = null;
        try {
            buf = mType.newInstance();
        } catch (InstantiationException e) {
            LogTool.w(TAG, mName + ". Create buffer with exception. ", e);
        } catch (IllegalAccessException e) {
            LogTool.w(TAG, mName + ". Create buffer with exception. ", e);
        }
        return buf;
    }

    private void dump() {
        String d = "Total (" + (mEmpty.size() + mUsed.size()) + ") buffers; "
                + mEmpty.size() + " empty buffers; "
                + mUsed.size() + " used buffers.";

        debug(d);
    }

    public static class Buf {
        private int mRef = 0;
        private BufferPool mPool;
        //private int track;

        void setPool(BufferPool pool) {
            mPool = pool;
        }

        protected void updateSize(int size) {}

        public synchronized int increaseRef() {
            //debug(getCallStatck());
            debug("increaseRef. " + mRef + "->" + (mRef+1));
            mRef++;
            return mRef;
        }

        public synchronized int decreaseRef() {
            //debug(getCallStatck());
            if (mRef > 0) {
                debug( "decreaseRef. " + mRef + "->" + (mRef-1));
                mRef--;
                if (mRef == 0) {
                    if (mPool != null) {
                        mPool.backBuf(this);
                    } else {
                        LogTool.w(TAG, poolName() + ". decreaseRef. The pool is null!");
                    }
                }
            } else {
                LogTool.w(TAG, poolName() + ". decreaseRef. The buffer has been back to pool early");
            }
            return mRef;
        }

        protected void debug(String msg) {
			if (null != mPool) {
				mPool.debug(msg);
			}
        }

        private String poolName() {
            return (mPool!=null)? mPool.mName: null;
        }

/*        public int getTrack() {
            return track;
        }

        public void setTrack(int value) {
            track = value;
        }*/
    }

    void debug(String msg) {
        if (mDebug) {
            LogTool.d(TAG, mName + ". " + msg);
        }
    }

    static String getCallStatck() {
        StringBuilder sb = new StringBuilder();
        Throwable ex = new Throwable();
        StackTraceElement[] stackElements = ex.getStackTrace();
        if (stackElements != null) {
            for (int i = 1; i < Math.min(stackElements.length, 3); i++) {
                int dot = stackElements[i].getClassName().lastIndexOf(".");
                sb.append(stackElements[i].getClassName().substring(dot+1)).append(".");
                sb.append(stackElements[i].getLineNumber()).append(".");
                sb.append(stackElements[i].getMethodName()).append(" <- ");
            }
        }
        return sb.toString();
    }
}
