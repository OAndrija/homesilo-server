package com.andrija.homesiloserver.dashboard.layout;

import com.andrija.homesiloserver.common.exception.BadRequestException;
import com.andrija.homesiloserver.dashboard.layout.dto.DashboardLayoutRequest;
import com.andrija.homesiloserver.dashboard.layout.dto.DashboardLayoutResponse;
import com.andrija.homesiloserver.dashboard.layout.dto.TileDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class DashboardLayoutService {

    private final DashboardLayoutRepository repository;

    @Transactional(readOnly = true)
    public DashboardLayoutResponse get(UUID userId) {
        return repository.findById(userId).map(DashboardLayoutService::toResponse)
                .orElseGet(DashboardLayoutResponse::notCustomized);
    }

    public DashboardLayoutResponse save(UUID userId, DashboardLayoutRequest request) {
        Set<String> seen = new HashSet<>();
        for (TileDto tile : request.tiles()) {
            if (!seen.add(tile.id())) {
                throw new BadRequestException("Each tile can only be on the dashboard once");
            }
        }
        DashboardLayout layout = repository.findById(userId).orElseGet(() -> new DashboardLayout(userId));
        layout.getTiles().clear();
        request.tiles().forEach(t -> layout.getTiles().add(new DashboardTile(t.id(), t.size(), t.variant())));
        layout.setUpdatedAt(LocalDateTime.now());
        return toResponse(repository.save(layout));
    }

    /** Back to the app's default layout. */
    public void reset(UUID userId) {
        repository.deleteById(userId);
    }

    private static DashboardLayoutResponse toResponse(DashboardLayout layout) {
        List<TileDto> tiles = layout.getTiles().stream()
                .map(t -> new TileDto(t.getTileId(), t.getSize(), t.getVariant()))
                .toList();
        return new DashboardLayoutResponse(true, tiles, layout.getUpdatedAt());
    }
}
