package com.andrija.homesiloserver.file;

import com.andrija.homesiloserver.common.exception.BadRequestException;
import com.andrija.homesiloserver.folder.Folder;
import com.andrija.homesiloserver.folder.FolderRepository;
import com.andrija.homesiloserver.folder.FolderService;
import com.andrija.homesiloserver.storage.BlobStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Builds zip downloads of files and whole folder trees. Planning happens inside
 * a read-only transaction; the bytes are streamed afterwards straight from the
 * blob store to the response, so archive size is bounded by disk, not memory.
 */
@Service
@RequiredArgsConstructor
public class ArchiveService {

    private static final int MAX_ENTRIES = 10_000;

    private final FileMetadataRepository fileRepository;
    private final FolderRepository folderRepository;
    private final FolderService folderService;
    private final BlobStorage blobStorage;

    @Transactional(readOnly = true)
    public ArchivePlan plan(Collection<UUID> fileIds, Collection<UUID> folderIds, UUID ownerId) {
        if (fileIds.isEmpty() && folderIds.isEmpty()) {
            throw new BadRequestException("Select at least one file or folder to download");
        }

        NameRegistry names = new NameRegistry();
        List<Entry> entries = new ArrayList<>();

        if (!fileIds.isEmpty()) {
            for (FileMetadata file : fileRepository.findByOwnerIdAndIdInAndTrashedFalse(ownerId, fileIds)) {
                entries.add(Entry.file(names.claim("", file.getOriginalFileName()), file.getStoredFileName()));
            }
        }

        String archiveName = "homesilo-download.zip";
        for (UUID folderId : folderIds) {
            Folder root = folderService.getOwned(folderId, ownerId);
            if (root.isTrashed()) continue;
            String rootPath = names.claim("", root.getName()) + "/";
            addFolderTree(root, rootPath, ownerId, names, entries);
            if (folderIds.size() == 1 && fileIds.isEmpty()) {
                archiveName = root.getName() + ".zip";
            }
        }

        if (entries.isEmpty()) {
            throw new BadRequestException("Nothing to download");
        }
        if (entries.size() > MAX_ENTRIES) {
            throw new BadRequestException("Too many items for one archive (limit " + MAX_ENTRIES + ")");
        }
        return new ArchivePlan(archiveName, ownerId, entries);
    }

    public void write(ArchivePlan plan, OutputStream out) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (Entry entry : plan.entries()) {
                zip.putNextEntry(new ZipEntry(entry.path()));
                if (entry.blobHash() != null) {
                    try (InputStream in = blobStorage.load(plan.ownerId(), entry.blobHash()).getInputStream()) {
                        in.transferTo(zip);
                    }
                }
                zip.closeEntry();
            }
        }
    }

    private void addFolderTree(Folder root, String rootPath, UUID ownerId, NameRegistry names, List<Entry> entries) {
        Map<UUID, String> paths = new HashMap<>();
        paths.put(root.getId(), rootPath);
        entries.add(Entry.directory(rootPath));

        List<UUID> level = List.of(root.getId());
        while (!level.isEmpty() && entries.size() <= MAX_ENTRIES) {
            for (FileMetadata file : fileRepository.findByOwnerIdAndFolderIdInAndTrashedFalse(ownerId, level)) {
                String dir = paths.get(file.getFolder().getId());
                entries.add(Entry.file(dir + names.claim(dir, file.getOriginalFileName()), file.getStoredFileName()));
            }
            List<UUID> next = new ArrayList<>();
            for (Folder child : folderRepository.findChildren(ownerId, level, false)) {
                String parentPath = paths.get(child.parentFolderId());
                String path = parentPath + names.claim(parentPath, child.getName()) + "/";
                paths.put(child.getId(), path);
                entries.add(Entry.directory(path));
                next.add(child.getId());
            }
            level = next;
        }
    }

    public record ArchivePlan(String fileName, UUID ownerId, List<Entry> entries) {}

    /** {@code blobHash} is {@code null} for directory entries. */
    public record Entry(String path, String blobHash) {
        static Entry file(String path, String blobHash) {
            return new Entry(path, blobHash);
        }

        static Entry directory(String path) {
            return new Entry(path, null);
        }
    }

    /** Keeps names unique per directory: {@code a.txt}, {@code a (1).txt}, ... (case-insensitive, like most filesystems). */
    private static final class NameRegistry {
        private final Map<String, Set<String>> taken = new HashMap<>();

        String claim(String directory, String rawName) {
            String name = rawName.replace('/', '_').replace('\\', '_');
            Set<String> used = taken.computeIfAbsent(directory, d -> new HashSet<>());
            String candidate = name;
            int dot = name.lastIndexOf('.');
            String base = dot > 0 ? name.substring(0, dot) : name;
            String ext = dot > 0 ? name.substring(dot) : "";
            for (int i = 1; !used.add(candidate.toLowerCase(Locale.ROOT)); i++) {
                candidate = base + " (" + i + ")" + ext;
            }
            return candidate;
        }
    }
}
