package com.ispbilling.inbound;

/** Bir giriş satırının işlenememe nedeni. Kod ve mesaj satıra geri yazılır. */
class ImportException extends RuntimeException {

    enum Code { VALIDATION, NOT_FOUND, BUSINESS_RULE, INTERNAL }

    final Code code;

    ImportException(Code code, String message) {
        super(message);
        this.code = code;
    }

    static ImportException validation(String message) {
        return new ImportException(Code.VALIDATION, message);
    }

    static ImportException notFound(String message) {
        return new ImportException(Code.NOT_FOUND, message);
    }
}
