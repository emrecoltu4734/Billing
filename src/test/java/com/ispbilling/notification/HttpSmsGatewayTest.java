package com.ispbilling.notification;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpSmsGatewayTest {

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

    private HttpSmsGateway gateway(String url, String method, String body, String contentType, String successPattern) {
        return new HttpSmsGateway(builder, new NotificationProperties.Http(url, method, body, contentType, successPattern,
                "kullanici", "gizli&şifre", "ORNEKISP"));
    }

    @Test
    void getRequestEncodesPlaceholders() {
        server.expect(requestTo("https://sms.example/send?u=kullanici&p=gizli%26%C5%9Fifre&to=905321234567&msg=Faturan%C4%B1z+396%2C00+TL%3F&h=ORNEKISP"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("00 123456", MediaType.TEXT_PLAIN));
        var gw = gateway("https://sms.example/send?u={username}&p={password}&to={phone}&msg={message}&h={sender}",
                "GET", null, null, "^00");
        assertThatCode(() -> gw.send("905321234567", "Faturanız 396,00 TL?")).doesNotThrowAnyException();
        server.verify();
    }

    @Test
    void postJsonEscapesValues() {
        server.expect(requestTo("https://api.example/sms"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType("application/json"))
                .andExpect(content().string("{\"to\":\"905321234567\",\"text\":\"a \\\"tırnak\\\"\\nyeni\"}"))
                .andRespond(withSuccess("{\"status\":\"ok\"}", MediaType.APPLICATION_JSON));
        gateway("https://api.example/sms", "POST", "{\"to\":\"{phone}\",\"text\":\"{message}\"}", "application/json", "\"ok\"")
                .send("905321234567", "a \"tırnak\"\nyeni");
        server.verify();
    }

    @Test
    void providerRejectionBecomesDeliveryErrorWithoutLeakingUrl() {
        server.expect(requestTo("https://sms.example/send?p=gizli%26%C5%9Fifre")).andRespond(withSuccess("30 GECERSIZ KULLANICI", MediaType.TEXT_PLAIN));
        var gw = gateway("https://sms.example/send?p={password}", "GET", null, null, "^00");
        assertThatThrownBy(() -> gw.send("905321234567", "x"))
                .isInstanceOf(DeliveryException.class)
                .hasMessage("SMS sağlayıcısı başarısız yanıt verdi: 30 GECERSIZ KULLANICI")
                .message().doesNotContain("gizli");
    }

    @Test
    void httpErrorBecomesDeliveryError() {
        server.expect(requestTo("https://sms.example/send")).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE).body("bakımda"));
        assertThatThrownBy(() -> gateway("https://sms.example/send", "GET", null, null, null).send("905321234567", "x"))
                .isInstanceOf(DeliveryException.class)
                .hasMessage("SMS sağlayıcısı hata döndü: HTTP 503 bakımda");
    }
}
