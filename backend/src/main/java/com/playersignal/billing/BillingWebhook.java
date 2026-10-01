package com.playersignal.billing;

import com.fasterxml.jackson.databind.*;
import com.playersignal.shared.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class BillingWebhook {
    private final String secret;private final ObjectMapper mapper;private final JdbcTemplate jdbc;private final BillingService billing;private final PlanService plans;
    private static final Set<String> EVENTS=Set.of("checkout.session.completed","checkout.session.async_payment_succeeded","checkout.session.async_payment_failed","customer.subscription.created","customer.subscription.updated","customer.subscription.deleted","invoice.paid","invoice.payment_failed");
    public BillingWebhook(@Value("${STRIPE_WEBHOOK_SECRET:}")String secret,ObjectMapper mapper,JdbcTemplate jdbc,BillingService billing,PlanService plans){this.secret=secret;this.mapper=mapper;this.jdbc=jdbc;this.billing=billing;this.plans=plans;}
    public void receive(byte[] body,String signature){
        if(!plans.enabled()||!secret.startsWith("whsec_"))throw new ApiException(503,"WEBHOOK_DISABLED","Billing webhook is not configured.");
        if(!valid(body,signature))throw new ApiException(400,"INVALID_SIGNATURE","Invalid Stripe signature.");
        JsonNode event;try{event=mapper.readTree(body);}catch(Exception error){throw new ApiException(400,"INVALID_EVENT","Invalid event JSON.");}
        String id=event.path("id").asText(),type=event.path("type").asText();if(!id.matches("evt_[A-Za-z0-9]+")||!event.has("livemode")||event.path("livemode").asBoolean(true))throw new ApiException(400,"INVALID_EVENT","Only sandbox Stripe events are accepted.");
        if(!EVENTS.contains(type))return;
        String customer=StripeClient.id(event.path("data").path("object").path("customer"));
        if(!customer.matches("cus_[A-Za-z0-9]+"))return;
        jdbc.update("INSERT INTO playersignal.billing_event(id,type,customer_id) VALUES (?,?,?) ON CONFLICT DO NOTHING",id,type,customer);
        process(id,customer);
    }
    private boolean valid(byte[] body,String signature){
        if(signature==null||signature.length()>4096)return false;
        try {
            Long time=null;var signatures=new ArrayList<String>();
            for(String part:signature.split(",")){String[] pair=part.strip().split("=",2);if(pair.length!=2)continue;if(pair[0].equals("t")){if(time!=null)return false;time=Long.parseLong(pair[1]);}else if(pair[0].equals("v1"))signatures.add(pair[1]);}
            long now=Instant.now().getEpochSecond();if(time==null||time<now-300||time>now+300||signatures.isEmpty())return false;
            Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));mac.update((time+".").getBytes(StandardCharsets.UTF_8));byte[] expected=mac.doFinal(body);
            boolean match=false;for(String value:signatures)if(value.matches("[a-fA-F0-9]{64}"))match|=MessageDigest.isEqual(expected,HexFormat.of().parseHex(value));return match;
        }catch(Exception error){return false;}
    }
    private void process(String id,String customer){
        var rows=jdbc.queryForList("SELECT status FROM playersignal.billing_event WHERE id=?",id);if(rows.isEmpty()||rows.getFirst().get("status").equals("PROCESSED"))return;
        try{billing.reconcileCustomer(customer);jdbc.update("UPDATE playersignal.billing_event SET status='PROCESSED',attempts=attempts+1,error=NULL,processed_at=now() WHERE id=?",id);}
        catch(Exception error){jdbc.update("UPDATE playersignal.billing_event SET status='FAILED',attempts=attempts+1,error='Could not reconcile current subscription. Check Stripe permissions and connection.' WHERE id=?",id);throw new ApiException(503,"BILLING_RETRY","Billing state could not be reconciled. Retry this event.");}
    }
    @Scheduled(fixedDelayString="${BILLING_RETRY_MS:60000}")public void retry(){if(!plans.enabled()||!secret.startsWith("whsec_"))return;
        for(var row:jdbc.queryForList("SELECT id,customer_id FROM playersignal.billing_event WHERE status<>'PROCESSED' AND attempts<12 ORDER BY attempts,created_at LIMIT 20"))try{process((String)row.get("id"),(String)row.get("customer_id"));}catch(ApiException ignored){}
    }
}
