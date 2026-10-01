package com.playersignal.billing;

import com.fasterxml.jackson.databind.*;
import com.playersignal.shared.ApiException;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class StripeClient {
    public static final String API_VERSION="2024-12-18.acacia";
    private final String key;
    private final ObjectMapper mapper;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
    public StripeClient(ObjectMapper mapper,@Value("${STRIPE_SECRET_KEY:}")String key){this.mapper=mapper;this.key=key;}
    public boolean ready(){return key.startsWith("rk_test_")||key.startsWith("sk_test_");}
    public JsonNode get(String path){return call("GET",path,Map.of(),null);}
    public JsonNode post(String path,Map<String,String> fields,String idempotency){return call("POST",path,fields,idempotency);}
    private JsonNode call(String method,String path,Map<String,String> fields,String idempotency){
        if(!ready())throw new ApiException(503,"STRIPE_TEST_KEY_REQUIRED","Configure a Stripe sandbox key. Live payments are disabled.");
        try {
            var request=HttpRequest.newBuilder(URI.create("https://api.stripe.com/v1/"+path)).timeout(Duration.ofSeconds(12))
                .header("Authorization","Bearer "+key).header("Stripe-Version",API_VERSION);
            if(method.equals("POST")){
                String form=fields.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(e->encode(e.getKey())+"="+encode(e.getValue())).collect(java.util.stream.Collectors.joining("&"));
                request.header("Content-Type","application/x-www-form-urlencoded");
                if(idempotency!=null)request.header("Idempotency-Key",idempotency);
                request.POST(HttpRequest.BodyPublishers.ofString(form));
            } else request.GET();
            var response=http.send(request.build(),HttpResponse.BodyHandlers.ofString());
            if(response.statusCode()<200||response.statusCode()>=300){
                String code=response.statusCode()==403?"STRIPE_PERMISSION":response.statusCode()==429?"STRIPE_RATE_LIMIT":"STRIPE_REQUEST_FAILED";
                throw new ApiException(503,code,"Stripe sandbox request failed (HTTP "+response.statusCode()+"). Check the configured key permissions and price IDs. Retry safely; no plan is granted from a browser redirect.");
            }
            JsonNode data=mapper.readTree(response.body());
            if(data.path("livemode").asBoolean(false))throw new ApiException(503,"STRIPE_MODE_MISMATCH","Live Stripe data is not accepted by this sandbox integration.");
            return data;
        }catch(InterruptedException error){Thread.currentThread().interrupt();throw new ApiException(503,"STRIPE_INTERRUPTED","Stripe request interrupted. Retry to reconcile its result.");}
        catch(java.io.IOException error){throw new ApiException(503,"STRIPE_UNAVAILABLE","Stripe is unavailable. Retry; existing data is preserved.");}
    }
    public static String encode(String value){return URLEncoder.encode(value,StandardCharsets.UTF_8);}
    public static String id(JsonNode value){return value.isTextual()?value.asText():value.path("id").asText("");}
}
