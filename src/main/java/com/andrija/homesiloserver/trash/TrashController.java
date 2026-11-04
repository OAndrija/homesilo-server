package com.andrija.homesiloserver.trash;

import com.andrija.homesiloserver.security.ServerUserDetails;
import com.andrija.homesiloserver.trash.TrashService.PurgeResult;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/trash")
@RequiredArgsConstructor
public class TrashController {

    private final TrashService trashService;

    /** Permanently deletes every file and folder in the user's trash. */
    @DeleteMapping
    public PurgeResult emptyTrash(@AuthenticationPrincipal ServerUserDetails principal) {
        return trashService.emptyTrash(principal.getId());
    }
}
