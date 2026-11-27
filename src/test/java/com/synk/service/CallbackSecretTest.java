package com.synk.service;

import com.synk.dto.request.CollageCallbackRequest;
import com.synk.dto.request.SynklogCallbackRequest;
import com.synk.global.exception.CustomException;
import com.synk.global.exception.ErrorCode;
import com.synk.repository.CollageRepository;
import com.synk.repository.MissionRepository;
import com.synk.repository.SynklogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Lambda 콜백(콜라주·SYNKLOG)이 비밀값 검증을 가장 먼저 하는지 확인한다.
 * 비밀값이 틀리면 DB 를 조회하거나 바꾸기 전에 거절해야 한다. (COLLAGE_CALLBACK_SECRET 교체 시에도 이 동작이 유지돼야 한다)
 */
class CallbackSecretTest {

    private static final String SECRET = "test-callback-secret";

    private MissionRepository missionRepository;
    private CollageRepository collageRepository;
    private SynklogRepository synklogRepository;
    private CollageService collageService;
    private AlbumService albumService;

    @BeforeEach
    void setUp() {
        missionRepository = mock(MissionRepository.class);
        collageRepository = mock(CollageRepository.class);
        synklogRepository = mock(SynklogRepository.class);

        collageService = Mockito.mock(CollageService.class, Mockito.CALLS_REAL_METHODS);
        ReflectionTestUtils.setField(collageService, "missionRepository", missionRepository);
        ReflectionTestUtils.setField(collageService, "collageRepository", collageRepository);
        ReflectionTestUtils.setField(collageService, "callbackSecret", SECRET);

        albumService = Mockito.mock(AlbumService.class, Mockito.CALLS_REAL_METHODS);
        ReflectionTestUtils.setField(albumService, "synklogRepository", synklogRepository);
        ReflectionTestUtils.setField(albumService, "callbackSecret", SECRET);
    }

    private static void assertRejected(Runnable call) {
        CustomException e = assertThrows(CustomException.class, call::run);
        assertEquals(ErrorCode.INVALID_REQUEST, e.getErrorCode());
    }

    @Test
    void 콜라주_콜백은_비밀값이_틀리면_DB_조회_전에_거절한다() {
        assertRejected(() -> collageService.handleCallback("wrong-secret", new CollageCallbackRequest()));

        verifyNoInteractions(missionRepository, collageRepository);
    }

    @Test
    void 콜라주_콜백은_비밀값이_비어_있어도_거절한다() {
        assertRejected(() -> collageService.handleCallback("", new CollageCallbackRequest()));

        verifyNoInteractions(missionRepository, collageRepository);
    }

    @Test
    void SYNKLOG_콜백은_비밀값이_틀리면_DB_조회_전에_거절한다() {
        assertRejected(() -> albumService.handleSynklogCallback("wrong-secret", new SynklogCallbackRequest()));

        verifyNoInteractions(synklogRepository);
    }

    @Test
    void SYNKLOG_콜백은_비밀값이_비어_있어도_거절한다() {
        assertRejected(() -> albumService.handleSynklogCallback("", new SynklogCallbackRequest()));

        verifyNoInteractions(synklogRepository);
    }
}
