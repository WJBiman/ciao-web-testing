package com.ciao.backend.security;

import java.util.List;
import java.util.Locale;

public final class AccountIdentifiers {
    private AccountIdentifiers() {}

    public static String normalize(String value) {
        String input = value == null ? "" : value.trim();
        if (input.contains("@")) return input.toLowerCase(Locale.ROOT);
        String phone = input.replaceAll("[\\s()-]", "");
        if (phone.matches("\\+947\\d{8}")) return "0" + phone.substring(3);
        if (phone.matches("947\\d{8}")) return "0" + phone.substring(2);
        if (phone.matches("7\\d{8}")) return "0" + phone;
        return phone;
    }

    public static List<String> phoneForms(String value) {
        String phone = normalize(value);
        if (!phone.matches("07\\d{8}")) return List.of(phone);
        return List.of(phone, "+94" + phone.substring(1), "94" + phone.substring(1), phone.substring(1));
    }
}
