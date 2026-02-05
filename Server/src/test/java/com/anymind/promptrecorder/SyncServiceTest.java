package com.anymind.promptrecorder;

import static org.assertj.core.api.Assertions.assertThat;

import com.anymind.promptrecorder.model.SyncModels.ChangeRequest;
import com.anymind.promptrecorder.model.SyncModels.PullRequest;
import com.anymind.promptrecorder.model.SyncModels.PullResponse;
import com.anymind.promptrecorder.model.SyncModels.PushRequest;
import com.anymind.promptrecorder.model.SyncModels.PushResponse;
import com.anymind.promptrecorder.storage.SpaceDatabase;
import com.anymind.promptrecorder.storage.StoragePaths;
import com.anymind.promptrecorder.storage.SyncService;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SyncServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void pushAndPullRoundTrip() {
        StoragePaths paths = new StoragePaths(tempDir.toString());
        SpaceDatabase spaceDatabase = new SpaceDatabase(paths);
        SyncService syncService = new SyncService(spaceDatabase);

        String spaceId = "spc_test";
        String now = Instant.now().toString();
        ChangeRequest change = new ChangeRequest();
        change.setId("rec-1");
        change.setContent("Hello #tag");
        change.setSystemTags(Collections.singletonList("#p1"));
        change.setUserTags(Collections.singletonList("#tag"));
        change.setCreatedAt(now);
        change.setUpdatedAt(now);
        change.setDeleted(false);
        change.setBaseRev(null);

        PushRequest pushRequest = new PushRequest();
        pushRequest.setSpaceId(spaceId);
        pushRequest.setSpaceSecret("sec");
        pushRequest.setDeviceId("device");
        pushRequest.setChanges(Collections.singletonList(change));
        PushResponse pushResponse = syncService.push(pushRequest);

        assertThat(pushResponse.getResults()).hasSize(1);
        assertThat(pushResponse.getResults().get(0).getServerRev()).isGreaterThan(0);

        PullRequest pullRequest = new PullRequest();
        pullRequest.setSpaceId(spaceId);
        pullRequest.setSpaceSecret("sec");
        pullRequest.setSinceRev(0L);
        pullRequest.setLimit(10);
        PullResponse pullResponse = syncService.pull(pullRequest);

        assertThat(pullResponse.getChanges()).hasSize(1);
        assertThat(pullResponse.getChanges().get(0).getId()).isEqualTo("rec-1");
        assertThat(pullResponse.getServerRevMax()).isGreaterThan(0);
    }
}
