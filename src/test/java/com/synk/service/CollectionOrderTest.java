package com.synk.service;

import com.synk.entity.MissionTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CollectionOrderTest {

    private MissionTemplate t(long id, String category) {
        MissionTemplate m = MissionTemplate.builder().title("미션" + id).description(category).build();
        ReflectionTestUtils.setField(m, "id", id);
        return m;
    }

    private List<Long> ids(List<MissionTemplate> list) {
        return list.stream().map(MissionTemplate::getId).toList();
    }

    @Test
    void 같은_카테고리_안에서_완료한_미션이_위로_오고_최근_완료가_맨_위다() {
        // 카테고리 A: 1,2,3,4 / 완료: 3(11/12), 1(11/10), 4(11/13)
        List<MissionTemplate> templates = List.of(t(1, "A"), t(2, "A"), t(3, "A"), t(4, "A"));
        Map<Long, LocalDate> done = Map.of(
                3L, LocalDate.of(2026, 11, 12),
                1L, LocalDate.of(2026, 11, 10),
                4L, LocalDate.of(2026, 11, 13));

        // 완료: 4(11/13) → 3(11/12) → 1(11/10), 미완료 2
        assertEquals(List.of(4L, 3L, 1L, 2L), ids(CollectionService.orderForDisplay(templates, done)));
    }

    @Test
    void 미완료_미션은_기존_id_순서를_유지한다() {
        List<MissionTemplate> templates = List.of(t(1, "A"), t(2, "A"), t(3, "A"));
        assertEquals(List.of(1L, 2L, 3L), ids(CollectionService.orderForDisplay(templates, Map.of())));
    }

    @Test
    void 카테고리_순서는_완료_여부와_상관없이_첫_등장_순서를_유지한다() {
        // A(1,2) B(3,4) C(5,6) — 마지막 카테고리 C 만 완료해도 C 가 앞으로 올라오면 안 된다
        List<MissionTemplate> templates = List.of(t(1, "A"), t(2, "A"), t(3, "B"), t(4, "B"), t(5, "C"), t(6, "C"));
        Map<Long, LocalDate> done = Map.of(6L, LocalDate.of(2026, 11, 13), 4L, LocalDate.of(2026, 11, 12));

        // A: 1,2 / B: 4(완료),3 / C: 6(완료),5
        assertEquals(List.of(1L, 2L, 4L, 3L, 6L, 5L), ids(CollectionService.orderForDisplay(templates, done)));
    }

    @Test
    void 입력_순서가_섞여_있어도_카테고리는_id_기준_첫_등장_순서다() {
        List<MissionTemplate> templates = List.of(t(5, "C"), t(1, "A"), t(3, "B"), t(2, "A"));
        assertEquals(List.of(1L, 2L, 3L, 5L), ids(CollectionService.orderForDisplay(templates, Map.of())));
    }

    @Test
    void 튜토리얼처럼_id가_큰_카테고리도_id_순서에_따른다() {
        // 튜토리얼(id 161,162)은 일반 미션 뒤 id 라서 서버 응답에서는 뒤에 오고, 프론트가 맨 앞으로 옮긴다
        List<MissionTemplate> templates = List.of(t(10, "A"), t(11, "A"), t(161, "튜토리얼"), t(162, "튜토리얼"));
        Map<Long, LocalDate> done = Map.of(162L, LocalDate.of(2026, 11, 10));
        assertEquals(List.of(10L, 11L, 162L, 161L), ids(CollectionService.orderForDisplay(templates, done)));
    }
}
