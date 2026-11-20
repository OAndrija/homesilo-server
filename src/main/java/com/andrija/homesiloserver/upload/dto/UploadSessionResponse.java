package com.andrija.homesiloserver.upload.dto;

import com.andrija.homesiloserver.upload.UploadSession;

import java.util.List;
import java.util.UUID;

/**
 * Where an upload stands: how to split the file, and which chunks the server
 * already has, so an interrupted upload sends only the rest.
 */
public record UploadSessionResponse(
        UUID id,
        String fileName,
        long size,
        int chunkSize,
        int chunkCount,
        List<Integer> receivedChunks
) {
    public static UploadSessionResponse from(UploadSession session, List<Integer> received) {
        return new UploadSessionResponse(session.getId(), session.getFileName(), session.getSize(),
                session.getChunkSize(), session.getChunkCount(), received);
    }
}
