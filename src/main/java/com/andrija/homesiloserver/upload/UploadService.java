package com.andrija.homesiloserver.upload;

import com.andrija.homesiloserver.common.config.HomesiloProperties;
import com.andrija.homesiloserver.common.exception.ApiException;
import com.andrija.homesiloserver.common.exception.BadRequestException;
import com.andrija.homesiloserver.common.exception.ConflictException;
import com.andrija.homesiloserver.common.exception.NotFoundException;
import com.andrija.homesiloserver.common.exception.QuotaExceededException;
import com.andrija.homesiloserver.common.exception.StorageException;
import com.andrija.homesiloserver.file.FileMetadataRepository;
import com.andrija.homesiloserver.file.FileService;
import com.andrija.homesiloserver.file.FileService.UploadSource;
import com.andrija.homesiloserver.file.dto.FileMetadataResponse;
import com.andrija.homesiloserver.folder.FolderService;
import com.andrija.homesiloserver.upload.dto.StartUploadRequest;
import com.andrija.homesiloserver.upload.dto.UploadSessionResponse;
import com.andrija.homesiloserver.user.User;
import com.andrija.homesiloserver.user.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.unit.DataSize;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Uploads sent in chunks. A proxy in front of HomeSilo may cap the size of one
 * request (Cloudflare's free plan at 100 MB), and a long upload over a shaky
 * connection shouldn't have to start over, so big files go in pieces:
 *
 * <ol>
 *   <li>{@link #start}: name, size and folder; the server says how to split the file.</li>
 *   <li>{@link #putChunk} for each piece, in any order, retried as often as needed.
 *       Each is written at its place in a temporary file.</li>
 *   <li>{@link #complete} once all are in: the file then goes through the same
 *       steps as a one-request upload (quota, dedupe, type, thumbnail).</li>
 * </ol>
 *
 * If the connection drops, {@link #status} says which chunks arrived, so only the
 * rest are sent again. Sessions left idle for a day are cleared out.
 */
@Slf4j
@Service
public class UploadService {

    /** Unfinished uploads per account at a time. */
    static final int MAX_OPEN_SESSIONS = 20;
    static final Duration IDLE_LIMIT = Duration.ofDays(1);

    private final UploadSessionRepository sessions;
    private final FileService fileService;
    private final FolderService folderService;
    private final UserService userService;
    private final FileMetadataRepository files;
    private final TransactionTemplate transactions;
    private final long maxFileSize;
    /** 32 MB by default: well under the 100 MB a proxy may allow per request, big enough to keep requests few. */
    private final int chunkSize;
    private final Path dir;
    /** Sessions being completed right now, so a double "complete" can't store the file twice. */
    private final Set<UUID> completing = ConcurrentHashMap.newKeySet();

    public UploadService(UploadSessionRepository sessions, FileService fileService, FolderService folderService,
                         UserService userService, FileMetadataRepository files,
                         PlatformTransactionManager transactionManager, HomesiloProperties properties,
                         @Value("${spring.servlet.multipart.max-file-size:5GB}") DataSize maxFileSize,
                         @Value("${homesilo.storage.upload-chunk-size:32MB}") DataSize chunkSize) {
        this.sessions = sessions;
        this.fileService = fileService;
        this.folderService = folderService;
        this.userService = userService;
        this.files = files;
        this.transactions = new TransactionTemplate(transactionManager);
        this.maxFileSize = maxFileSize.toBytes();
        this.chunkSize = Math.toIntExact(chunkSize.toBytes());
        this.dir = Path.of(properties.storage().basePath()).toAbsolutePath().normalize().resolve(".uploads");
        try {
            Files.createDirectories(dir);
        } catch (IOException ex) {
            throw new StorageException("Could not create the upload directory " + dir, ex);
        }
    }

    public UploadSessionResponse start(UUID ownerId, StartUploadRequest request) {
        if (request.size() > maxFileSize) {
            throw new QuotaExceededException("Files can be at most " + DataSize.ofBytes(maxFileSize).toGigabytes()
                    + " GB here");
        }
        if (sessions.countByOwnerId(ownerId) >= MAX_OPEN_SESSIONS) {
            throw new ConflictException("Too many uploads in progress. Wait for some to finish, then try again.");
        }
        if (request.folderId() != null) folderService.getWritable(request.folderId(), ownerId);

        User owner = userService.getEntity(ownerId);
        long promised = files.sumSizeByOwnerId(ownerId) + sessions.sumSizeByOwnerId(ownerId);
        String name = FileService.sanitizeFileName(request.fileName());
        if (promised + request.size() > owner.getStorageQuotaBytes()) {
            throw new QuotaExceededException("Not enough storage space left for '" + name + "'");
        }

        LocalDateTime now = LocalDateTime.now();
        UploadSession session = new UploadSession();
        session.setId(UUID.randomUUID());
        session.setOwnerId(ownerId);
        session.setFolderId(request.folderId());
        session.setFileName(name);
        session.setSize(request.size());
        session.setChunkSize(chunkSize);
        session.setChunkCount(Math.toIntExact((request.size() + chunkSize - 1) / chunkSize));
        session.setCreatedAt(now);
        session.setUpdatedAt(now);
        sessions.save(session);
        log.debug("Upload {} started: '{}' ({} bytes, {} chunks) for user {}",
                session.getId(), name, request.size(), session.getChunkCount(), ownerId);
        return UploadSessionResponse.from(session, List.of());
    }

    public UploadSessionResponse status(UUID ownerId, UUID sessionId) {
        UploadSession session = find(ownerId, sessionId);
        return UploadSessionResponse.from(session, sessions.receivedChunks(sessionId));
    }

    /**
     * Writes one chunk at its place in the file. The request body is read before
     * any transaction starts, so a slow connection doesn't hold a database
     * connection while it trickles in.
     */
    public void putChunk(UUID ownerId, UUID sessionId, int index, long contentLength, InputStream body) {
        UploadSession session = find(ownerId, sessionId);
        if (index < 0 || index >= session.getChunkCount()) {
            throw new BadRequestException("This upload has chunks 0 to " + (session.getChunkCount() - 1));
        }
        long expected = session.expectedLength(index);
        if (contentLength >= 0 && contentLength != expected) {
            throw new BadRequestException("Chunk " + index + " should be " + expected + " bytes, not " + contentLength);
        }

        long written = write(session, index, expected, body);
        if (written != expected) {
            throw new BadRequestException("Chunk " + index + " arrived incomplete (" + written + " of " + expected
                    + " bytes). Send it again.");
        }
        transactions.executeWithoutResult(status -> {
            sessions.recordChunk(sessionId, index);
            sessions.touch(sessionId, LocalDateTime.now());
        });
    }

    /** Stores the finished file. If storing fails (say, the quota is full), the chunks stay for another try. */
    public FileMetadataResponse complete(UUID ownerId, UUID sessionId) {
        UploadSession session = find(ownerId, sessionId);
        if (!completing.add(sessionId)) {
            throw new ConflictException("This upload is already being finished");
        }
        try {
            long missing = session.getChunkCount() - sessions.countReceivedChunks(sessionId);
            if (missing > 0) {
                throw new ConflictException(missing == 1 ? "1 chunk hasn't arrived yet"
                        : missing + " chunks haven't arrived yet");
            }
            Path part = partFile(sessionId);
            if (!Files.exists(part) || Files.size(part) != session.getSize()) {
                throw new UploadBrokenException();
            }
            FileMetadataResponse stored = fileService.upload(
                    new UploadSource(session.getFileName(), session.getSize(), new FileSystemResource(part)),
                    ownerId, session.getFolderId());
            discard(session);
            return stored;
        } catch (IOException ex) {
            throw new StorageException("Could not read the uploaded chunks", ex);
        } finally {
            completing.remove(sessionId);
        }
    }

    public void cancel(UUID ownerId, UUID sessionId) {
        discard(find(ownerId, sessionId));
    }

    /**
     * Nightly: uploads nobody has touched for a day, and leftover pieces whose
     * session is gone (its account was deleted, say).
     */
    public int clearAbandoned() {
        LocalDateTime cutoff = LocalDateTime.now().minus(IDLE_LIMIT);
        List<UploadSession> idle = sessions.findByUpdatedAtBefore(cutoff);
        idle.forEach(this::discard);

        int orphans = 0;
        Instant old = Instant.now().minus(IDLE_LIMIT);
        try (DirectoryStream<Path> parts = Files.newDirectoryStream(dir, "*.part")) {
            for (Path part : parts) {
                String id = part.getFileName().toString().replace(".part", "");
                boolean known = isUuid(id) && sessions.existsById(UUID.fromString(id));
                FileTime modified = Files.getLastModifiedTime(part);
                if (!known && modified.toInstant().isBefore(old)) {
                    Files.deleteIfExists(part);
                    orphans++;
                }
            }
        } catch (IOException ex) {
            log.warn("Could not sweep {} for leftover uploads", dir, ex);
        }
        if (!idle.isEmpty() || orphans > 0) {
            log.info("Cleared {} abandoned upload(s) and {} leftover piece(s)", idle.size(), orphans);
        }
        return idle.size() + orphans;
    }

    private long write(UploadSession session, int index, long expected, InputStream body) {
        long offset = (long) index * session.getChunkSize();
        byte[] buffer = new byte[64 * 1024];
        long written = 0;
        try (FileChannel channel = FileChannel.open(partFile(session.getId()),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            channel.position(offset);
            int read;
            while ((read = body.read(buffer)) != -1) {
                if (written + read > expected) {
                    throw new BadRequestException("Chunk " + index + " is bigger than " + expected + " bytes");
                }
                ByteBuffer slice = ByteBuffer.wrap(buffer, 0, read);
                while (slice.hasRemaining()) channel.write(slice);
                written += read;
            }
            return written;
        } catch (IOException ex) {
            throw new StorageException("Could not save chunk " + index, ex);
        }
    }

    private void discard(UploadSession session) {
        sessions.deleteById(session.getId());
        try {
            Files.deleteIfExists(partFile(session.getId()));
        } catch (IOException ex) {
            log.warn("Could not delete the pieces of upload {}", session.getId(), ex);
        }
    }

    private UploadSession find(UUID ownerId, UUID sessionId) {
        return sessions.findByIdAndOwnerId(sessionId, ownerId)
                .orElseThrow(() -> new NotFoundException("This upload isn't in progress any more. Start it again."));
    }

    /** Named only by the session's id, so nothing the client sends ends up in a path. */
    private Path partFile(UUID sessionId) {
        return dir.resolve(sessionId + ".part");
    }

    private static boolean isUuid(String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    /** The pieces on disk don't add up to the file (the disk was cleaned meanwhile, say). */
    static class UploadBrokenException extends ApiException {
        UploadBrokenException() {
            super(HttpStatus.CONFLICT, "The uploaded pieces are incomplete on the server. Start the upload again.");
        }
    }
}
