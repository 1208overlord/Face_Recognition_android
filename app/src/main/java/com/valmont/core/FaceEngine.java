package com.valmont.core;

public class FaceEngine {
    public static int LOWEST_LEVEL = 0;
    public static int LOW_LEVEL = 1;
    public static int MEDIUM_LEVEL = 2;
    public static int HIGH_LEVEL = 3;
    public static int HIGHEST_LEVEL = 4;


    public static int FACEAPI_OK = 0;
    public static int FACEAPI_GENRAL_ERROR = -1;
    public static int FACEAPI_POSE_NOT_FRONTAL = -2;
    public static int FACEAPI_FAIL_ENROLL = -3;
    public static int FACEAPI_FAIL_EXTRACT = -4;
    public static int FACEAPI_ENROLL_CONTINUE = -5;
    public static int FACEAPI_FAIL_IDENTIFY = -7;


    public static int ENCODE_FT_SIZE = 1028;
    public static int ENROLL_FT_SIZE = 1028;

    public static Object engineLockExtract = new Object();
    public static Object engineLockQuality = new Object();

    static {
        System.loadLibrary("FaceEngine");
    }

    private static FaceEngine s_Instance = null;
    public static FaceEngine getInstance() {
        if (s_Instance == null) {
            s_Instance = new FaceEngine();
        }
        return s_Instance;
    }

    public static int initEngineLibrary() {
        return getInstance().jniFaceInitialize();
    }

    public static void releaseEngineLibrary() {
        getInstance().jniFaceRelease();
    }

    public static int faceGetImageQuality(byte[] image, int width, int height, float[] landmarks, int[] qualityInfo) {
        synchronized (engineLockQuality) {
            return getInstance().jniGetImageQuality(image, width, height, landmarks, qualityInfo);
        }
    }

    public static int faceFeatureExtract(byte[] image, int width, int height, float[] landmarks, byte[] faceFeat) {
        synchronized (engineLockExtract) {
            return getInstance().jniFaceFeatureExtract(image, width, height, landmarks, faceFeat);
        }
    }

    public static int faceEnrollProc(byte[] image, int width, int height, float[] landmarks, int[] qualityInfo, byte[] faceFeat) {
        synchronized (engineLockExtract) {
            return getInstance().jniEnrollNewFaces(image, width, height, landmarks, qualityInfo, faceFeat);
        }
    }

    public static int faceCheckEnroll(byte[] faceFeatSearch, byte[] faceFeatDB, int nEnrollCount, int nSecurityLevel) {
        synchronized (engineLockExtract) {
            return getInstance().jniCheckEnroll(faceFeatSearch, faceFeatDB, nEnrollCount, nSecurityLevel);
        }
    }

    public static int matchFaceOneToN(byte[] faceFeatSearch, byte[] faceFeatDB, int nEnrollCount, FaceMatchResult result, int nSecurityLevel) {
        synchronized (engineLockExtract) {
            int[] matchIdx = new int[1];
            byte[] similarity = new byte[1];

            int ret = getInstance().jniMatchFaceOneToN(faceFeatSearch, faceFeatDB,
                    nEnrollCount, matchIdx, similarity, nSecurityLevel);

            result.index = matchIdx[0];
            result.similarity = similarity[0];
            result.bIsUpdate = false;
            return ret;
        }
    }

    //face engine functions
    public native int jniFaceInitialize();

    public native void jniFaceRelease();

    public native int jniGetImageQuality(byte[] image, int width, int height, float[] landmarks, int[] qualityInfo);

    public native int jniFaceFeatureExtract(byte[] image, int width, int height, float[] landmarks, byte[] faceFeat);

    public native int jniEnrollNewFaces(byte[] image, int width, int height, float[] landmarks, int[] qualityInfo, byte[] faceFeat);

    public native int jniCheckEnroll(byte[] faceFeatSearch, byte[] faceFeatDB, int nEnrollCount, int nSecurityLevel);

    public native int jniMatchFaceOneToN(byte[] faceFeatSearch, byte[] faceFeatDB, int nEnrollCount, int[] matchID, byte[] similarity, int nSecurityLevel);
}
