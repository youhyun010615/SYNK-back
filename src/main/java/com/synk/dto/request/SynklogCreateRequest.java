// SYNKLOG 생성 요청 body (선택) — missionIds가 있으면 해당 미션의 콜라주만 합침
package com.synk.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class SynklogCreateRequest {
    private List<Long> missionIds;
}
