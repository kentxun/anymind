package com.anymind.promptrecorder.api;

import com.anymind.promptrecorder.model.SyncModels.PullRequest;
import com.anymind.promptrecorder.model.SyncModels.PullResponse;
import com.anymind.promptrecorder.model.SyncModels.PushRequest;
import com.anymind.promptrecorder.model.SyncModels.PushResponse;
import com.anymind.promptrecorder.storage.SpaceRegistry;
import com.anymind.promptrecorder.storage.SyncService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class SyncController {
    private final SpaceRegistry spaceRegistry;
    private final SyncService syncService;

    public SyncController(SpaceRegistry spaceRegistry, SyncService syncService) {
        this.spaceRegistry = spaceRegistry;
        this.syncService = syncService;
    }

    @PostMapping("/sync/push")
    public PushResponse push(@RequestBody PushRequest request) {
        validate(request.getSpaceId(), request.getSpaceSecret());
        return syncService.push(request);
    }

    @PostMapping("/sync/pull")
    public PullResponse pull(@RequestBody PullRequest request) {
        validate(request.getSpaceId(), request.getSpaceSecret());
        return syncService.pull(request);
    }

    private void validate(String spaceId, String spaceSecret) {
        if (spaceId == null || spaceId.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "space_id required");
        }
        if (spaceSecret == null || spaceSecret.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "space_secret required");
        }
        if (!spaceRegistry.findSpace(spaceId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "space not found");
        }
        if (!spaceRegistry.validateSecret(spaceId, spaceSecret)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid space secret");
        }
    }
}
