package ua.kpi.grader.common.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import ua.kpi.grader.common.exception.InvalidPhoneNumberException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PhoneNumberNormalizerTest {

    private final PhoneNumberNormalizer normalizer = new PhoneNumberNormalizer();

    @ParameterizedTest
    @CsvSource({
            "'+380508529087',   '+380508529087'",
            "'380508529087',    '+380508529087'",
            "'0508529087',      '+380508529087'",
            "'+38(050) 852 9087', '+380508529087'",
            "'+38 (050) 852-9087', '+380508529087'",
            "'050 852 9087',    '+380508529087'",
            "'050-852-9087',    '+380508529087'",
            "'  +380508529087 ', '+380508529087'"
    })
    void normalizeUa_returnsE164_forEveryAcceptedFormat(String raw, String expected) {
        assertThat(normalizer.normalizeUa(raw)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   ", "\t"})
    void normalizeUa_returnsNull_forBlankInput(String raw) {
        assertThat(normalizer.normalizeUa(raw)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "abc",
            "1",
            "12",
            "050 852 90",
            "05085290871234567",
            "not-a-phone"
    })
    void normalizeUa_throwsInvalidPhoneNumberException_forInvalidInput(String raw) {
        assertThatThrownBy(() -> normalizer.normalizeUa(raw))
                .isInstanceOf(InvalidPhoneNumberException.class);
    }

    @Test
    void normalizeUa_preservesRawValueInException() {
        String raw = "definitely-not-a-phone";
        assertThatThrownBy(() -> normalizer.normalizeUa(raw))
                .isInstanceOfSatisfying(InvalidPhoneNumberException.class, ex ->
                        assertThat(ex.getRawValue()).isEqualTo(raw));
    }
}
