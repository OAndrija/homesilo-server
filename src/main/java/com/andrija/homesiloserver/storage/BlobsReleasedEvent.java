package com.andrija.homesiloserver.storage;

import java.util.Collection;
import java.util.UUID;

/** Published when file entries are permanently deleted; their blobs may now be unreferenced. */
public record BlobsReleasedEvent(UUID ownerId, Collection<String> hashes) {}
