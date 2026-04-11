package org.example;

public class StaticFileResult {

    private final byte[] fileBytes;
    private final int statusCode;

    public StaticFileResult(byte[] fileBytes, int statusCode) {
        this.fileBytes = fileBytes;
        this.statusCode = statusCode;
    }

    public byte[] getFileBytes() {
        return fileBytes;
    }

    public int getStatusCode() {
        return statusCode;
    }
}