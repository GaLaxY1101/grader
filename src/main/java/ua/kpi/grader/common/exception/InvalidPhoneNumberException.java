package ua.kpi.grader.common.exception;

/**
 * Thrown when a raw phone-number string cannot be parsed as a valid
 * Ukrainian phone number by {@link ua.kpi.grader.common.util.PhoneNumberNormalizer}.
 */
public class InvalidPhoneNumberException extends RuntimeException {

    private final String rawValue;

    public InvalidPhoneNumberException(String rawValue, String reason) {
        super("Invalid phone number '" + rawValue + "': " + reason);
        this.rawValue = rawValue;
    }

    public InvalidPhoneNumberException(String rawValue, String reason, Throwable cause) {
        super("Invalid phone number '" + rawValue + "': " + reason, cause);
        this.rawValue = rawValue;
    }

    public String getRawValue() {
        return rawValue;
    }
}
