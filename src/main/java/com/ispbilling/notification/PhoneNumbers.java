package com.ispbilling.notification;

/** Türkiye cep telefonu numaralarını SMS sağlayıcılarının beklediği 905XXXXXXXXX biçimine çevirir. */
public final class PhoneNumbers {

    private PhoneNumbers() {
    }

    /** @return 905XXXXXXXXX veya numara cep telefonu değilse null */
    public static String normalizeTrMobile(String raw) {
        if (raw == null) return null;
        String d = raw.replaceAll("\\D", "");
        if (d.length() == 10 && d.startsWith("5")) d = "90" + d;
        else if (d.length() == 11 && d.startsWith("05")) d = "9" + d;
        else if (d.length() == 14 && d.startsWith("0090")) d = d.substring(2);
        return d.length() == 12 && d.startsWith("905") ? d : null;
    }
}
