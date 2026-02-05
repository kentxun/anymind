package com.anymind.promptrecorder.api;

import com.anymind.promptrecorder.model.SpaceModels.SpaceCreateRequest;
import com.anymind.promptrecorder.model.SpaceModels.SpaceCreateResponse;
import com.anymind.promptrecorder.storage.SpaceRegistry;
import com.anymind.promptrecorder.storage.SpaceRegistry.SpaceInfo;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SpacesController {
    private final SpaceRegistry spaceRegistry;

    public SpacesController(SpaceRegistry spaceRegistry) {
        this.spaceRegistry = spaceRegistry;
    }

    @PostMapping("/spaces")
    public SpaceCreateResponse create(@RequestBody(required = false) SpaceCreateRequest request) {
        String name = request == null ? null : request.getName();
        SpaceInfo info = spaceRegistry.createSpace(name);
        return new SpaceCreateResponse(info.getSpaceId(), info.getSpaceSecret(), info.getCreatedAt());
    }
}
