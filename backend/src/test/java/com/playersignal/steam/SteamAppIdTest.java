package com.playersignal.steam;
import com.playersignal.shared.ApiException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
class SteamAppIdTest {
    @ParameterizedTest @ValueSource(strings={"620", " 620 ", "https://store.steampowered.com/app/620/Portal_2/?l=english"})
    void acceptsIdsAndStoreUrls(String input) { assertThat(SteamAppId.parse(input)).isEqualTo(620); }
    @ParameterizedTest @ValueSource(strings={"0", "-1", "4294967296", "1e3", "", "https://evil.test/app/620", "http://localhost/app/620", "https://store.steampowered.com.evil.test/app/620", "https://user@store.steampowered.com/app/620", "https://store.steampowered.com:443/app/620", "https://store.steampowered.com/app/nope"})
    void rejectsUnsafeOrInvalidInput(String input) { assertThatThrownBy(() -> SteamAppId.parse(input)).isInstanceOf(ApiException.class); }
}
