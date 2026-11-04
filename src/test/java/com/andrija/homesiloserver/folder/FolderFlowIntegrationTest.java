package com.andrija.homesiloserver.folder;

import com.andrija.homesiloserver.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FolderFlowIntegrationTest extends IntegrationTest {

    @Test
    void contentsIncludeBreadcrumbSubfoldersAndFiles() throws Exception {
        Session session = register();
        String root = createFolder(session, "Projects", null);
        String child = createFolder(session, "HomeSilo", root);
        createFolder(session, "Specs", child);
        upload(session, "readme.md", "# hi", child);

        mvc.perform(get("/api/v1/folders/{id}/contents", child).cookie(session.cookie()))
                .andExpect(jsonPath("$.folder.name").value("HomeSilo"))
                .andExpect(jsonPath("$.breadcrumb[0].name").value("Projects"))
                .andExpect(jsonPath("$.breadcrumb[1].name").value("HomeSilo"))
                .andExpect(jsonPath("$.subfolders[0].name").value("Specs"))
                .andExpect(jsonPath("$.files.content[0].originalFileName").value("readme.md"));
    }

    @Test
    void renameWorksAndRejectsSiblingNameClashes() throws Exception {
        Session session = register();
        String parent = createFolder(session, "Parent", null);
        String a = createFolder(session, "A", parent);
        createFolder(session, "B", parent);

        mvc.perform(patch("/api/v1/folders/{id}/rename", a).cookie(session.cookie()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json("name", "Renamed")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed"));

        mvc.perform(patch("/api/v1/folders/{id}/rename", a).cookie(session.cookie()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json("name", "b")))
                .andExpect(status().isConflict());
    }

    @Test
    void duplicateFolderNamesInTheSameLocationAreConflicts() throws Exception {
        Session session = register();
        createFolder(session, "Music", null);
        mvc.perform(post("/api/v1/folders").cookie(session.cookie()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json("name", "Music")))
                .andExpect(status().isConflict());
    }

    @Test
    void folderCannotBeMovedIntoItsOwnSubtree() throws Exception {
        Session session = register();
        String top = createFolder(session, "Top", null);
        String mid = createFolder(session, "Mid", top);
        String leaf = createFolder(session, "Leaf", mid);

        mvc.perform(patch("/api/v1/folders/{id}/move", top).cookie(session.cookie()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json("targetParentId", leaf)))
                .andExpect(status().isConflict());

        mvc.perform(patch("/api/v1/folders/{id}/move", leaf).cookie(session.cookie()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json("targetParentId", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parentId").doesNotExist());
    }

    @Test
    void trashCascadesAndTrashViewShowsOnlyTheTopLevelItem() throws Exception {
        Session session = register();
        String top = createFolder(session, "Archive", null);
        String nested = createFolder(session, "2024", top);
        upload(session, "report.pdf", "pdf-ish", nested);

        trashFolder(session, top);

        mvc.perform(get("/api/v1/folders/trash").cookie(session.cookie()))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(top));
        mvc.perform(get("/api/v1/files/trash").cookie(session.cookie()))
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/v1/folders/root").cookie(session.cookie()))
                .andExpect(jsonPath("$.subfolders.length()").value(0));
    }

    @Test
    void restoringIntoAnOccupiedNameAddsASuffix() throws Exception {
        Session session = register();
        String original = createFolder(session, "Taxes", null);
        trashFolder(session, original);
        createFolder(session, "Taxes", null);

        mvc.perform(patch("/api/v1/folders/{id}/restore", original).cookie(session.cookie()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Taxes (2)"))
                .andExpect(jsonPath("$.trashed").value(false));
    }

    @Test
    void deletingADeepTreeRemovesEverything() throws Exception {
        Session session = register();
        String parent = null;
        String top = null;
        for (int depth = 0; depth < 6; depth++) {
            parent = createFolder(session, "level-" + depth, parent);
            if (top == null) top = parent;
            upload(session, "file-" + depth + ".txt", "content-" + depth, parent);
        }
        trashFolder(session, top);

        mvc.perform(delete("/api/v1/folders/{id}", top).cookie(session.cookie()).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/folders/{id}", parent).cookie(session.cookie()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/dashboard/stats").cookie(session.cookie()))
                .andExpect(jsonPath("$.storageUsedBytes").value(0));
    }

    @Test
    void emptyTrashRemovesFilesAndFolders() throws Exception {
        Session session = register();
        String folder = createFolder(session, "Junk", null);
        upload(session, "inside.txt", "1", folder);
        String loose = upload(session, "loose.txt", "2", null);
        trashFolder(session, folder);
        trashFile(session, loose);

        mvc.perform(delete("/api/v1/trash").cookie(session.cookie()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.files").value(2))
                .andExpect(jsonPath("$.folders").value(1));
        mvc.perform(get("/api/v1/folders/trash").cookie(session.cookie()))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void dashboardCountsTrashTowardsUsage() throws Exception {
        Session session = register();
        upload(session, "a.txt", "12345", null);
        String b = upload(session, "b.txt", "123", null);
        trashFile(session, b);

        mvc.perform(get("/api/v1/dashboard/stats").cookie(session.cookie()))
                .andExpect(jsonPath("$.storageUsedBytes").value(8))
                .andExpect(jsonPath("$.trashedBytes").value(3))
                .andExpect(jsonPath("$.totalFiles").value(1))
                .andExpect(jsonPath("$.recentlyTrashed[0].id").value(b));
    }
}
