package com.valmont.core;

public class FaceMatchResult {
    public int index;
    public byte similarity;
    public boolean bIsUpdate;

    public FaceMatchResult(int index, byte similarity, boolean bIsUpdate) {
        this.index = index;
        this.similarity = similarity;
        this.bIsUpdate = bIsUpdate;
    }
}
