//package com.valmont.core;
//
////import com.face_six.fa6.standalone.domain.entities.FrameData;
//
//import com.google.android.gms.vision.face.Face;
//
//import java.util.Random;
//
//public class FaceProcessor {
//    public static int CHECK_STATE_NORMAL = 0;
//    public static int CHECK_STATE_BLINK = 1;
//    public static int CHECK_STATE_SMILE = 2;
//    public static int CHECK_STATE_FRONTAL = 3;
//    public static int CHECK_STATE_LIVENESS_OK = 4;
//    public static MatchStatus curMatchStatus;
//    public static boolean bIsEnrollMode;
//    public static boolean existPendingData;
//    public static byte[] featureDataForEnroll;
//    public static int nMatchedSubjectID;
//    public static byte nMatchedSubjectScore;
//    public static int nPrevFaceTrackingID;
//    public static int nCheckedSameFrameCount;
//
//    static {
//        curMatchStatus = new MatchStatus(CHECK_STATE_NORMAL, 0.0F, 0.0F);
//        nMatchedSubjectID = -1;
//        nPrevFaceTrackingID = -1;
//    }
//
//    public static void resetMatchStatus() {
//        curMatchStatus.status = CHECK_STATE_NORMAL;
//        nPrevFaceTrackingID = -1;
//        nMatchedSubjectID = -1;
//        nMatchedSubjectScore = 0;
//        nCheckedSameFrameCount = 0;
//    }
//
//    public static float[] extractLandmarks(Face face) {
//        float[] landmarks = new float[12];
//
//        for (int i = 0; i < 6; i++) {
//            float x = face.getLandmarks().get(i).getPosition().x;
//            float y = face.getLandmarks().get(i).getPosition().y;
//
//            landmarks[i * 2] = x;
//            landmarks[i * 2 + 1] = y;
//        }
//        return landmarks;
//    }
//
//    public static boolean processQualityEvaluate(FrameData frameData) {
//        byte[] grayImg = frameData.getGrayImage();
//        float[] landmarks = extractLandmarks(frameData.getFace());
//        int width = frameData.getMetadata().getWidth();
//        int height = frameData.getMetadata().getHeight();
//        FaceEngine.faceGetImageQuality(grayImg, width, height, landmarks, frameData.getQualityInfo());
//        if (bIsEnrollMode) {
//            int[] qualityInfo = frameData.getQualityInfo();
//            if (Math.abs(qualityInfo[2]) > 25 || Math.abs(qualityInfo[1]) > 15) {
//                curMatchStatus.status = CHECK_STATE_FRONTAL;
//                return false;
//            }
//            curMatchStatus.status = CHECK_STATE_NORMAL;
//        }
//        return true;
//    }
//
//    public static boolean checkMatchStatus(Face face, boolean bFirst) {
//        if (bIsEnrollMode) {
//            return false;
//        }
//        if (bFirst) {
//            int randVal = (new Random(System.currentTimeMillis())).nextInt();
//            if (randVal % 2 == 0) {
//                curMatchStatus.status = CHECK_STATE_BLINK;
//                curMatchStatus.param1 = face.getIsLeftEyeOpenProbability();
//                curMatchStatus.param2 = face.getIsRightEyeOpenProbability();
//            } else {
//                curMatchStatus.status = CHECK_STATE_SMILE;
//                curMatchStatus.param1 = face.getIsSmilingProbability();
//            }
//        } else {
//            if (curMatchStatus.status == CHECK_STATE_BLINK) {
//                float diff1 = face.getIsLeftEyeOpenProbability() - curMatchStatus.param1;
//                float diff2 = face.getIsRightEyeOpenProbability() - curMatchStatus.param2;
//                if (diff1 > 0.7F && diff2 > 0.7F) {
//                    return true;
//                }
//                curMatchStatus.param1 = Math.min(curMatchStatus.param1, face.getIsLeftEyeOpenProbability());
//                curMatchStatus.param2 = Math.min(curMatchStatus.param2, face.getIsRightEyeOpenProbability());
//            } else if (curMatchStatus.status == CHECK_STATE_SMILE) {
//                float diff = face.getIsSmilingProbability() - curMatchStatus.param1;
//                if (face.getIsSmilingProbability() > 0.8F && diff > 0.7F) {
//                    return true;
//                }
//                curMatchStatus.param1 = Math.min(curMatchStatus.param1, face.getIsSmilingProbability());
//            }
//        }
//        return false;
//    }
//
//    public static FaceMatchResult findFaceMatchInEnroll(FrameData frame, byte[] enrollFtData, int nEnrollCount) {
//        byte[] byteImg = frame.getGrayImage();
//        float[] landmarks = extractLandmarks(frame.getFace());
//        int width = frame.getMetadata().getWidth();
//        int height = frame.getMetadata().getHeight();
//        byte[] ftData = new byte[FaceEngine.ENCODE_FT_SIZE];
//        int nRet = FaceEngine.faceFeatureExtract(byteImg, width, height, landmarks, ftData);
//        if (nRet == FaceEngine.FACEAPI_FAIL_EXTRACT || bIsEnrollMode) {
//            return new FaceMatchResult(-1, (byte)0, false);
//        }
//        FaceMatchResult matchResult = new FaceMatchResult(-1, (byte)0, false);
//        int nMatch = FaceEngine.matchFaceOneToN(ftData, enrollFtData, nEnrollCount, matchResult, FaceEngine.MEDIUM_LEVEL);
//        if (nMatch == FaceEngine.FACEAPI_OK) {
//            return matchResult;
//        }
//        return new FaceMatchResult(-1, (byte) 0, false);
//    }
//
//    public static MatchResult processMatch(FrameData frame, byte[] enrollFtData, int nEnrollCount) {
//        int user_id = -1;
//        byte similarity = 0;
//        boolean isUpdate = false;
//        boolean isMatched = false;
//        boolean isNewVisit = false;
//        int nQuality = frame.getQualityInfo()[0];
//        if (nPrevFaceTrackingID != frame.getFace().getId()) {
//            resetMatchStatus();
//        }
//
//        if (curMatchStatus.status == CHECK_STATE_NORMAL ) {
//            FaceMatchResult matchResult = findFaceMatchInEnroll(frame, enrollFtData, nEnrollCount);
//            if (matchResult.index >= 0) {
//                nMatchedSubjectID = matchResult.index;
//                nMatchedSubjectScore = matchResult.similarity;
//                nCheckedSameFrameCount = 0;
//                checkMatchStatus(frame.getFace(), true);
//            } else if (frame.getQualityInfo()[0] > 70) {
//                nCheckedSameFrameCount++;
//                // fail recognition for 3 times
//                if( nCheckedSameFrameCount == 3 ) {
//                    isNewVisit = true;
//                    curMatchStatus.status = CHECK_STATE_NORMAL;
//                }
//            }
//        } else if (curMatchStatus.status == CHECK_STATE_LIVENESS_OK ) {
//            FaceMatchResult matchResult = findFaceMatchInEnroll(frame, enrollFtData, nEnrollCount);
//            if (matchResult.index >= 0 && nMatchedSubjectID == matchResult.index) {
//                isMatched = true;
//                isNewVisit = true;
//                similarity = matchResult.similarity;
//                user_id = matchResult.index;
//                resetMatchStatus();
//            } else if (frame.getQualityInfo()[0] > 70) {
//                nCheckedSameFrameCount++;
//                // fail recognition for 3 times after check liveness success
//                if( nCheckedSameFrameCount == 3 ) {
//                    resetMatchStatus();
//                }
//            }
//        } else if (curMatchStatus.status == CHECK_STATE_BLINK ||
//                curMatchStatus.status == CHECK_STATE_SMILE) {
//            if( nQuality > 65 && checkMatchStatus(frame.getFace(), false) == true ) {
//                isMatched = true;
//                isNewVisit = true;
//                similarity = nMatchedSubjectScore;
//                user_id = nMatchedSubjectID;
//                resetMatchStatus();
//            }
//        }
//        nPrevFaceTrackingID = frame.getFace().getId();
//        return new MatchResult(isNewVisit, isMatched, user_id, similarity, nQuality, isUpdate);
//    }
//
//    public static EnrollResult processEnroll(FrameData frame) {
//        byte[] byteImg = frame.getGrayImage();
//        float[] landmarks = extractLandmarks(frame.getFace());
//        int width = frame.getMetadata().getWidth();
//        int height = frame.getMetadata().getHeight();
//        byte[] ftData = new byte[FaceEngine.ENROLL_FT_SIZE];
//        int[] qualityInfo = frame.getQualityInfo();
//        EnrollResult result = new EnrollResult(FaceEngine.FACEAPI_ENROLL_CONTINUE, 0, 0);
//        int nRet = FaceEngine.faceEnrollProc(byteImg, width, height, landmarks, qualityInfo, ftData);
//
//        if (nRet < FaceEngine.FACEAPI_OK) {
//            result.retcode = nRet;
//            return result;
//        }
//
//        featureDataForEnroll = ftData;
//        result.retcode = FaceEngine.FACEAPI_OK;
//        result.photoIndex = nRet;
//        result.quality = qualityInfo[0];
//        return result;
//    }
//
//    public static int checkEnroll(byte[] faceFeatSearch, byte[] faceFeatDB, int nEnrollCount, int nSecurityLevel) {
//        return FaceEngine.faceCheckEnroll(faceFeatSearch, faceFeatDB, nEnrollCount, nSecurityLevel);
//    }
//
//    public static byte[] getEnrollFeature() {
//        return featureDataForEnroll;
//    }
//
//    public static void faceNoDetected() {
//        resetMatchStatus();
//    }
//}
