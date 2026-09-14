package com.wnteam.mlanticheat.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public final class TextColors {

    private static final char SECTION = '\u00a7';
    private static final int SPREAD_LENGTH = 14;
    private static final Pattern COMPACT_HEX = Pattern.compile("(?i)[&\u00a7]#([0-9a-f]{6})");
    private static final Pattern SPREAD_HEX = Pattern.compile("(?i)[&\u00a7]x((?:[&\u00a7][0-9a-f]){6})");
    private static final Pattern SIMPLE = Pattern.compile("(?i)&([0-9a-fk-or])");
    private static final Map<Character, String> NAMES = new LinkedHashMap<>();

    private static final LegacyComponentSerializer SERIALIZER = LegacyComponentSerializer.builder()
            .character(SECTION)
            .hexCharacter('#')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    static {
        String[] colors = {"black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple", "gold",
                "gray", "dark_gray", "blue", "green", "aqua", "red", "light_purple", "yellow", "white"};
        for (int i = 0; i < colors.length; i++) {
            NAMES.put("0123456789abcdef".charAt(i), colors[i]);
        }
        NAMES.put('k', "obfuscated");
        NAMES.put('l', "bold");
        NAMES.put('m', "strikethrough");
        NAMES.put('n', "underlined");
        NAMES.put('o', "italic");
        NAMES.put('r', "reset");
    }

    private TextColors() {
    }

    public static Component legacy(String input) {
        return SERIALIZER.deserialize(section(input));
    }

    public static String section(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        String value = COMPACT_HEX.matcher(input).replaceAll(match -> spread(match.group(1)));
        value = SPREAD_HEX.matcher(value).replaceAll(match -> spread(strip(match.group(1))));
        return SIMPLE.matcher(value).replaceAll(match -> SECTION + match.group(1).toLowerCase(Locale.ROOT));
    }

    public static String miniMessage(String input) {
        String value = section(input);
        StringBuilder output = new StringBuilder(value.length() + 16);
        int cursor = 0;
        while (cursor < value.length()) {
            char symbol = value.charAt(cursor);
            if (symbol != SECTION || cursor + 1 >= value.length()) {
                output.append(symbol);
                cursor++;
                continue;
            }
            char code = Character.toLowerCase(value.charAt(cursor + 1));
            if (code == 'x') {
                String hex = readSpread(value, cursor);
                if (hex != null) {
                    output.append("<#").append(hex).append('>');
                    cursor += SPREAD_LENGTH;
                    continue;
                }
            }
            String name = NAMES.get(code);
            if (name == null) {
                output.append(symbol);
                cursor++;
                continue;
            }
            output.append('<').append(name).append('>');
            cursor += 2;
        }
        return output.toString();
    }

    private static String readSpread(String value, int start) {
        if (start + SPREAD_LENGTH > value.length()) {
            return null;
        }
        StringBuilder hex = new StringBuilder(6);
        for (int pair = 0; pair < 6; pair++) {
            int index = start + 2 + pair * 2;
            if (value.charAt(index) != SECTION || !isHex(value.charAt(index + 1))) {
                return null;
            }
            hex.append(Character.toLowerCase(value.charAt(index + 1)));
        }
        return hex.toString();
    }

    private static String spread(String rgb) {
        StringBuilder builder = new StringBuilder(SPREAD_LENGTH).append(SECTION).append('x');
        for (char symbol : rgb.toLowerCase(Locale.ROOT).toCharArray()) {
            builder.append(SECTION).append(symbol);
        }
        return builder.toString();
    }

    private static String strip(String value) {
        StringBuilder builder = new StringBuilder(6);
        for (int i = 0; i < value.length(); i++) {
            char symbol = value.charAt(i);
            if (isHex(symbol)) {
                builder.append(symbol);
            }
        }
        return builder.toString();
    }

    private static boolean isHex(char symbol) {
        return (symbol >= '0' && symbol <= '9')
                || (symbol >= 'a' && symbol <= 'f')
                || (symbol >= 'A' && symbol <= 'F');
    }
}
