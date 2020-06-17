package com.valmont.core;

public class EnrollResult {
    public int retcode;
    public int photoIndex;
    public int quality;

    public EnrollResult(int retcode, int photoIndex, int quality) {
        this.retcode = retcode;
        this.photoIndex = photoIndex;
        this.quality = quality;
    }
}
