package com.server.modules.document;

public final class DocumentMessages {

    public static final String KIND_REQUIRED = "document_kind_required";
    public static final String PAYLOAD_REQUIRED = "document_payload_required";
    public static final String PAYLOAD_TOO_LARGE = "document_payload_too_large";
    public static final String PAYLOAD_INVALID = "document_payload_invalid";
    public static final String SIGNATURE_REQUIRED = "document_signature_required";
    public static final String SIGNATURE_TOO_LONG = "document_signature_too_long";
    public static final String RECIPIENTS_REQUIRED = "document_recipients_required";
    public static final String RECIPIENTS_TOO_MANY = "document_recipients_too_many";
    public static final String RECIPIENT_REQUIRED = "document_recipient_required";
    public static final String RECIPIENT_INVALID = "document_recipient_invalid";
    public static final String RECIPIENT_DUPLICATE = "document_recipient_duplicate";
    public static final String WRAPPED_KEY_REQUIRED = "document_wrapped_key_required";
    public static final String WRAPPED_KEY_TOO_LONG = "document_wrapped_key_too_long";
    public static final String SENDER_HAS_NO_KEYS = "document_sender_has_no_keys";

    private DocumentMessages() {
    }
}
