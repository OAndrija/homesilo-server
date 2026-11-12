package com.andrija.homesiloserver.folder.dto;

/** What a folder holds, all the way down: its subfolders, its files and their total size. */
public record FolderSummaryResponse(long folders, long files, long bytes) {
}
