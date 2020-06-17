package com.valmont.core;

public class MatchResult {
    public boolean bNewVisit;
    public boolean bIsMatched;
    public int id;
    public byte similarity;
    public int quality;
    public boolean bIsUpdate;

    public MatchResult(boolean bNewVisit, boolean bIsMatched, int id, byte similarity, int quality, boolean bIsUpdate) {
        this.bNewVisit = bNewVisit;
        this.bIsMatched = bIsMatched;
        this.id = id;
        this.similarity = similarity;
        this.quality = quality;
        this.bIsUpdate = bIsUpdate;
    }
}

