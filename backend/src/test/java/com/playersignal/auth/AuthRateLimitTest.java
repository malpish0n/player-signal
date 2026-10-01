package com.playersignal.auth;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class AuthRateLimitTest {
 @Test void limitsRepeatedAttemptsEvenAcrossDifferentAddressStrings(){var rate=new AuthRateLimit();for(int i=0;i<8;i++)rate.check("127.0.0.1","user@example.test");assertThatThrownBy(()->rate.check("127.0.0.1"," USER@EXAMPLE.TEST ")).isInstanceOf(com.playersignal.shared.ApiException.class);}
}
