package com.auralink.api.v1.painting;

public record PaintingImageResponse(String assetId, String mimeType, Long fileSize, Integer width, Integer height, String contentUrl, String downloadUrl) {
}
