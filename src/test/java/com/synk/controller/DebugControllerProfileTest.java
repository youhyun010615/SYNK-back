package com.synk.controller;

import com.synk.repository.*;
import com.synk.service.FcmService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** 디버그용 컨트롤러는 운영(prod)에서 등록되지 않고, 그 밖의 프로필(로컬 개발 등)에서만 등록되어야 한다. */
class DebugControllerProfileTest {

    private ApplicationContextRunner runnerWith(String... activeProfiles) {
        return new ApplicationContextRunner()
                .withUserConfiguration(DebugController.class)
                .withBean(RoomRepository.class, () -> mock(RoomRepository.class))
                .withBean(MissionRepository.class, () -> mock(MissionRepository.class))
                .withBean(MissionTemplateRepository.class, () -> mock(MissionTemplateRepository.class))
                .withBean(MissionTimeSlotRepository.class, () -> mock(MissionTimeSlotRepository.class))
                .withBean(CollageRepository.class, () -> mock(CollageRepository.class))
                .withBean(RoomMemberRepository.class, () -> mock(RoomMemberRepository.class))
                .withBean(FcmService.class, () -> mock(FcmService.class))
                .withBean(UserRepository.class, () -> mock(UserRepository.class))
                .withBean(UserFcmTokenRepository.class, () -> mock(UserFcmTokenRepository.class))
                .withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles(activeProfiles));
    }

    @Test
    void 운영_프로필에서는_디버그_컨트롤러가_등록되지_않는다() {
        runnerWith("prod").run(ctx -> assertThat(ctx).doesNotHaveBean(DebugController.class));
    }

    @Test
    void 로컬_프로필에서는_디버그_컨트롤러가_등록된다() {
        runnerWith("local").run(ctx -> assertThat(ctx).hasSingleBean(DebugController.class));
    }
}
