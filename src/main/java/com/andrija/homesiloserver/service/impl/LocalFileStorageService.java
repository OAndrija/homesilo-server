package com.andrija.homesiloserver.service.impl;

import com.andrija.homesiloserver.exception.FileStorageException;
import com.andrija.homesiloserver.service.FileStorageService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;


@Service
@Slf4j
public class LocalFileStorageService implements FileStorageService {

    @Value("${storage.base-path}")
    private String storageLocation;

    private Path rootLocation;

    @PostConstruct
    public void init() {
        rootLocation = Paths.get(storageLocation).toAbsolutePath().normalize();
        try {
            Files.createDirectories(rootLocation);
        } catch (IOException ex) {
            throw new FileStorageException("Could not initialize storage location", ex);
        }
    }

    @Override
    public String store(MultipartFile file, UUID ownerId, String storedFileName) {
        if (file.isEmpty()) {
            throw new FileStorageException("Cannot store an empty file");
        }

        Path ownerDirectory = resolveOwnerDirectory(ownerId);
        createDirectory(ownerDirectory);

        Path destinationFile = resolveSafePath(ownerDirectory, storedFileName);

        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, destinationFile, StandardCopyOption.REPLACE_EXISTING);
            log.info("Stored file '{}' for owner '{}'", storedFileName, ownerId);
            return storedFileName;
        } catch (IOException e) {
            throw new FileStorageException("Could not store file", e);
        }
    }

    @Override
    public Resource load(UUID ownerId, String storedFileName) {
        Path ownerDirectory = resolveOwnerDirectory(ownerId);
        Path filePath = resolveSafePath(ownerDirectory, storedFileName);

        try {
            Resource resource = new UrlResource(filePath.toUri());

            if (!resource.exists() || !resource.isReadable()) {
                throw new FileStorageException("File not found or not readable " + storedFileName);
            }

            return resource;
        } catch (MalformedURLException e) {
            throw new FileStorageException("Could not read file: " + storedFileName, e);
        }
    }

    @Override
    public void delete(UUID ownerId, String storedFileName) {
        Path ownerDirectory = resolveOwnerDirectory(ownerId);
        Path filePath = resolveSafePath(ownerDirectory, storedFileName);

        try {
            Files.deleteIfExists(filePath);
            log.info("Deleted file '{}' for owner '{}'", storedFileName, ownerId);
        } catch (IOException e) {
            throw new FileStorageException("Failed to delete file", e);
        }
    }

    private Path resolveOwnerDirectory(UUID ownerId) {
        return rootLocation.resolve(ownerId.toString()).normalize();
    }

    private Path resolveSafePath(Path ownerDirectory, String storedFileName) {
        Path targetPath = ownerDirectory.resolve(storedFileName).normalize();

        if (!targetPath.getParent().equals(ownerDirectory)) {
            throw new FileStorageException("Cannot store file outside specified directory");
        }

        return targetPath;
    }

    private void createDirectory(Path path) {
        try {
            Files.createDirectories(path);
        } catch (IOException e) {
            throw new FileStorageException("Could not create directory: " + path, e);
        }
    }
}
