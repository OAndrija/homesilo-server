package com.andrija.homesiloserver.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "file_metadata")
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
public class FileMetadata {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    //Original name of the user's file
    @Column(nullable = false)
    private String originalFileName;

    //The name that's saved in the File System
    @Column(nullable = false)
    private String storedFileName;

    @Column(nullable = false)
    private String contentType;

    @Column(nullable = false)
    private long size;
}
