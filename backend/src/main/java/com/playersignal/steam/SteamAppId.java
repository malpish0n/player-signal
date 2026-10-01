package com.playersignal.steam;

import java.net.URI;
import java.util.regex.Pattern;
import com.playersignal.shared.ApiException;

public final class SteamAppId {
    private static final Pattern PATH = Pattern.compile("^/app/([0-9]+)(?:/[^?#]*)?/?$");
    private SteamAppId() {}
    public static long parse(String input) {
        try {
            if (input == null || input.length() > 2048) throw new IllegalArgumentException();
            String value = input.strip();
            if (!value.matches("[0-9]{1,10}")) {
                URI uri = URI.create(value);
                if (!"https".equalsIgnoreCase(uri.getScheme()) ||
                        !"store.steampowered.com".equalsIgnoreCase(uri.getHost()) ||
                        uri.getUserInfo() != null || uri.getPort() != -1) throw new IllegalArgumentException();
                var match = PATH.matcher(uri.getPath());
                if (!match.matches()) throw new IllegalArgumentException();
                value = match.group(1);
            }
            long id = Long.parseLong(value);
            if (id < 1 || id > 4294967295L) throw new IllegalArgumentException();
            return id;
        } catch (IllegalArgumentException error) {
            throw new ApiException(400, "INVALID_STEAM_APP", "Enter a positive Steam App ID or an https://store.steampowered.com/app/… URL.");
        }
    }
}
