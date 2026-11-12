package com.andrija.homesiloserver.thumbnail;

import java.util.UUID;

/** An image was just stored; its thumbnail can be made once the upload commits. */
public record ThumbnailWanted(UUID ownerId, String hash, String contentType, long size) {}
