package ua.kpi.grader.common.util;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;
import org.springframework.stereotype.Component;
import ua.kpi.grader.common.exception.InvalidPhoneNumberException;

/**
 * Parses raw phone-number strings entered by users (typically from a spreadsheet
 * cell or an editable form) into a single canonical E.164 representation.
 *
 * <p>All inputs are parsed with default region {@code UA}, so any of the following
 * yield {@code "+380508529087"}:
 * <ul>
 *     <li>{@code "+380508529087"}</li>
 *     <li>{@code "380508529087"}</li>
 *     <li>{@code "0508529087"}</li>
 *     <li>{@code "+38 (050) 852-9087"}</li>
 *     <li>{@code "050 852 9087"}</li>
 * </ul>
 */
@Component
public class PhoneNumberNormalizer {

    private static final String DEFAULT_REGION = "UA";

    private final PhoneNumberUtil phoneUtil = PhoneNumberUtil.getInstance();

    /**
     * Normalises a raw phone string to E.164 form for region UA.
     *
     * @param raw the raw string; may contain spaces, parentheses, dashes, or a leading '+'
     * @return the E.164-formatted number, or {@code null} if the input is blank
     * @throws InvalidPhoneNumberException if the input is non-blank but cannot be parsed
     *                                     as a valid Ukrainian number
     */
    public String normalizeUa(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        PhoneNumber parsed;
        try {
            parsed = phoneUtil.parse(trimmed, DEFAULT_REGION);
        } catch (NumberParseException ex) {
            throw new InvalidPhoneNumberException(raw, ex.getMessage(), ex);
        }
        if (!phoneUtil.isValidNumber(parsed)) {
            throw new InvalidPhoneNumberException(raw, "not a valid phone number for region " + DEFAULT_REGION);
        }
        return phoneUtil.format(parsed, PhoneNumberFormat.E164);
    }
}
