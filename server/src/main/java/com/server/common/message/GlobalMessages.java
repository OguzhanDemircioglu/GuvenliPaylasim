package com.server.common.message;

/**
 * Bütün modüllerin ortak kullandığı mesaj anahtarları. Değerler snake_case anahtardır; çeviriyi frontend yapar.
 */
public final class GlobalMessages {

    public static final String SUCCESS = "success";
    public static final String RECORD_CREATED = "record_created";
    public static final String RECORD_UPDATED = "record_updated";
    public static final String RECORD_DELETED = "record_deleted";

    public static final String INVALID_REQUEST = "invalid_request";
    public static final String INVALID_PARAMETER = "invalid_parameter";
    public static final String VERSION_REQUIRED = "version_required";
    public static final String UNAUTHORIZED = "unauthorized";
    public static final String ACCESS_DENIED = "access_denied";
    public static final String RECORD_NOT_FOUND = "record_not_found";
    public static final String URL_NOT_FOUND = "url_not_found";
    public static final String METHOD_NOT_ALLOWED = "method_not_allowed";
    public static final String RECORD_MODIFIED = "record_modified";
    public static final String RECORD_CONFLICT = "record_conflict";
    public static final String UNSUPPORTED_MEDIA_TYPE = "unsupported_media_type";
    public static final String UNEXPECTED_ERROR = "unexpected_error";

    private GlobalMessages() {
    }
}
