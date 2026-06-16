package com.andrija.homesiloserver.service;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface FileStorageService {
    String store(MultipartFile file, UUID ownerId, String storedFileName);
    Resource load(UUID ownerId, String storedFileName);
    void delete(UUID ownerId, String storedFileName);
}
