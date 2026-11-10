// 종료 시각 전용 LocalTime 역직렬화기.
// 프론트 시간 슬라이더가 하루의 끝을 "24:00" 으로 보내는데, LocalTime 은 00:00~23:59 만 표현할 수 있어
// 그대로 두면 파싱 실패(500)가 난다. "24:00" 을 하루의 끝(23:59:59)으로 해석하고, 그 외에는 기존과 동일하게 파싱한다.
// 시작 시각에는 쓰지 않는다 (24:00 이 시작일 수는 없으므로 기존처럼 거부).

package com.synk.global.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;

public class EndOfDayTimeDeserializer extends JsonDeserializer<LocalTime> {

    private static final LocalTime END_OF_DAY = LocalTime.of(23, 59, 59);

    @Override
    public LocalTime deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        String text = p.getValueAsString();
        if (text == null || text.isBlank()) return null;
        text = text.trim();

        if (text.equals("24:00") || text.equals("24:00:00")) return END_OF_DAY;

        try {
            return LocalTime.parse(text);
        } catch (DateTimeParseException e) {
            return (LocalTime) ctxt.handleWeirdStringValue(LocalTime.class, text, "시각 형식이 올바르지 않습니다 (HH:mm)");
        }
    }
}
