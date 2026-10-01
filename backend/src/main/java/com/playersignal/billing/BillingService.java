package com.playersignal.billing;

import com.fasterxml.jackson.databind.JsonNode;
import com.playersignal.auth.WorkspaceContext;
import com.playersignal.shared.ApiException;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class BillingService {
    public record Price(String plan,String priceId,long amount,String currency,String interval,PlanService.Limits limits) {}
    public record Status(boolean enabled,boolean configured,boolean webhookConfigured,String mode,PlanService.Limits limits,String subscriptionStatus,Instant periodEnd,Instant graceUntil,List<Price> prices) {}
    public record Link(String url) {}
    private final JdbcTemplate jdbc;private final StripeClient stripe;private final WorkspaceContext context;private final PlanService plans;
    private final TransactionTemplate tx;private final String indie,studio,origin;private final boolean webhookReady;
    private volatile List<Price> cachedPrices=List.of();private volatile long pricesAt;
    public BillingService(JdbcTemplate jdbc,StripeClient stripe,WorkspaceContext context,PlanService plans,PlatformTransactionManager manager,
            @Value("${STRIPE_PRICE_INDIE_MONTHLY:}")String indie,@Value("${STRIPE_PRICE_STUDIO_MONTHLY:}")String studio,
            @Value("${APP_PUBLIC_URL:http://localhost:3000}")String origin,@Value("${STRIPE_WEBHOOK_SECRET:}")String webhook){
        this.jdbc=jdbc;this.stripe=stripe;this.context=context;this.plans=plans;this.tx=new TransactionTemplate(manager);this.indie=indie;this.studio=studio;this.origin=origin.replaceAll("/$","");this.webhookReady=webhook.startsWith("whsec_");
        URI uri=URI.create(this.origin);if(uri.getHost()==null||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null||!uri.getPath().isEmpty()||!(uri.getScheme().equals("https")||(uri.getScheme().equals("http")&&Set.of("localhost","127.0.0.1").contains(uri.getHost()))))throw new IllegalArgumentException("APP_PUBLIC_URL must be an HTTPS origin or local HTTP origin");
    }
    private void require(){context.requireOwner();if(!plans.enabled()||!stripe.ready()||!webhookReady)throw new ApiException(503,"BILLING_NOT_READY","Configure sandbox billing and its webhook signing secret first.");}
    private String priceId(String plan){return switch(plan){case "INDIE"->indie;case "STUDIO"->studio;default->throw new ApiException(400,"INVALID_PLAN","Choose INDIE or STUDIO.");};}
    private void ensure(UUID workspace){jdbc.update("INSERT INTO playersignal.workspace_billing(workspace_id) VALUES (?) ON CONFLICT DO NOTHING",workspace);}
    private Map<String,Object> locked(UUID workspace){return jdbc.queryForMap("SELECT * FROM playersignal.workspace_billing WHERE workspace_id=? FOR UPDATE",workspace);}
    private Instant instant(Object value){return value instanceof java.sql.Timestamp t?t.toInstant():null;}
    private List<Price> prices(){
        if(System.currentTimeMillis()-pricesAt<60000&&!cachedPrices.isEmpty())return cachedPrices;
        var result=new ArrayList<Price>();
        for(String plan:List.of("INDIE","STUDIO")){
            String id=priceId(plan);if(!id.matches("price_[A-Za-z0-9]+"))throw new ApiException(503,"PRICE_NOT_CONFIGURED","Set the sandbox price IDs.");
            JsonNode p=stripe.get("prices/"+id);
            if(!p.path("active").asBoolean()||p.path("livemode").asBoolean()||!p.path("recurring").path("interval").asText().equals("month")||p.path("recurring").path("interval_count").asInt()!=1||!p.path("unit_amount").canConvertToLong())throw new ApiException(503,"INVALID_PRICE","Configure active fixed monthly sandbox prices.");
            result.add(new Price(plan,id,p.path("unit_amount").asLong(),p.path("currency").asText(),"month",plans.forPlan(plan)));
        }
        cachedPrices=List.copyOf(result);pricesAt=System.currentTimeMillis();return cachedPrices;
    }
    public Status status(){context.requireOwner();UUID workspace=context.current();ensure(workspace);var row=jdbc.queryForMap("SELECT * FROM playersignal.workspace_billing WHERE workspace_id=?",workspace);
        return new Status(plans.enabled(),stripe.ready(),webhookReady,"test",plans.limits(workspace),(String)row.get("status"),instant(row.get("period_end")),instant(row.get("grace_until")),plans.enabled()&&stripe.ready()?prices():List.of());
    }
    public Link checkout(String plan){require();String price=priceId(plan);prices();UUID workspace=context.current();ensure(workspace);
        // Reserve the attempt before HTTP; an uncertain response must reuse its exact idempotency key.
        tx.executeWithoutResult(status->{var row=locked(workspace);if(row.get("customer_id")!=null)reconcileLocked(workspace,(String)row.get("customer_id"));row=locked(workspace);
            if(!Set.of("none","canceled","incomplete_expired").contains((String)row.get("status")))throw new ApiException(409,"SUBSCRIPTION_EXISTS","Manage your existing subscription in the customer portal.");
            Instant expiry=instant(row.get("checkout_expires_at"));
            if(row.get("checkout_key")!=null&&expiry!=null&&expiry.plusSeconds(300).isAfter(Instant.now())){
                if(!plan.equals(row.get("checkout_plan")))throw new ApiException(409,"CHECKOUT_PENDING","Resume the pending checkout or wait for it to expire before choosing another plan.");
            }else jdbc.update("UPDATE playersignal.workspace_billing SET checkout_key=?,checkout_plan=?,checkout_expires_at=?,checkout_id=NULL,checkout_url=NULL WHERE workspace_id=?",UUID.randomUUID(),plan,java.sql.Timestamp.from(Instant.now().plusSeconds(3600)),workspace);
        });
        tx.executeWithoutResult(status->{var row=locked(workspace);if(row.get("customer_id")==null){
            var created=stripe.post("customers",Map.of("metadata[playersignal_workspace]",workspace.toString()),"playersignal-customer-"+workspace);String customer=created.path("id").asText();
            if(!customer.startsWith("cus_"))throw new ApiException(503,"INVALID_CUSTOMER","Stripe did not return a customer.");
            jdbc.update("UPDATE playersignal.workspace_billing SET customer_id=? WHERE workspace_id=?",customer,workspace);
        }});
        return tx.execute(status->{var row=locked(workspace);
            if(!Set.of("none","canceled","incomplete_expired").contains((String)row.get("status")))throw new ApiException(409,"SUBSCRIPTION_EXISTS","Manage your existing subscription in the customer portal.");
            if(row.get("checkout_url")!=null)return new Link((String)row.get("checkout_url"));
            String customer=(String)row.get("customer_id");
            Map<String,String> form=new LinkedHashMap<>();form.put("mode","subscription");form.put("customer",customer);form.put("line_items[0][price]",price);form.put("line_items[0][quantity]","1");form.put("client_reference_id",workspace.toString());form.put("subscription_data[metadata][playersignal_workspace]",workspace.toString());form.put("success_url",origin+"/settings?billing=returned");form.put("cancel_url",origin+"/settings?billing=canceled");form.put("expires_at",Long.toString(instant(row.get("checkout_expires_at")).getEpochSecond()));
            var session=stripe.post("checkout/sessions",form,"playersignal-checkout-"+row.get("checkout_key"));String url=safeUrl(session.path("url").asText(),"checkout.stripe.com");
            jdbc.update("UPDATE playersignal.workspace_billing SET checkout_id=?,checkout_url=?,updated_at=now() WHERE workspace_id=?",session.path("id").asText(),url,workspace);return new Link(url);
        });
    }
    public Link portal(){require();UUID workspace=context.current();ensure(workspace);String customer=jdbc.queryForObject("SELECT customer_id FROM playersignal.workspace_billing WHERE workspace_id=?",String.class,workspace);if(customer==null)throw new ApiException(409,"NO_CUSTOMER","Start a checkout before opening the portal.");return new Link(safeUrl(stripe.post("billing_portal/sessions",Map.of("customer",customer,"return_url",origin+"/settings"),null).path("url").asText(),"billing.stripe.com"));}
    private String safeUrl(String value,String host){try{var uri=URI.create(value);if(!"https".equals(uri.getScheme())||!host.equals(uri.getHost())||uri.getUserInfo()!=null)throw new IllegalArgumentException();return value;}catch(Exception error){throw new ApiException(503,"INVALID_STRIPE_URL","Stripe returned an unexpected redirect.");}}
    public Status refresh(){require();UUID workspace=context.current();ensure(workspace);tx.executeWithoutResult(status->{var row=locked(workspace);if(row.get("customer_id")!=null)reconcileLocked(workspace,(String)row.get("customer_id"));});return status();}
    public void reconcileCustomer(String customer){var ids=jdbc.query("SELECT workspace_id FROM playersignal.workspace_billing WHERE customer_id=?",(rs,n)->rs.getObject(1,UUID.class),customer);if(ids.isEmpty())return;UUID workspace=ids.getFirst();tx.executeWithoutResult(status->{locked(workspace);reconcileLocked(workspace,customer);});}
    private void reconcileLocked(UUID workspace,String customer){
        var response=stripe.get("subscriptions?customer="+StripeClient.encode(customer)+"&status=all&limit=100");
        if(response.path("has_more").asBoolean())throw new ApiException(503,"SUBSCRIPTION_REVIEW","Subscription history requires operator review.");
        var current=new ArrayList<JsonNode>();for(var s:response.path("data"))if(!Set.of("canceled","incomplete_expired").contains(s.path("status").asText()))current.add(s);
        if(current.size()>1)throw new ApiException(409,"MULTIPLE_SUBSCRIPTIONS","Multiple subscriptions require review in Stripe before changing access.");
        if(current.isEmpty()){
            var previous=locked(workspace);if(previous.get("subscription_id")!=null)jdbc.update("UPDATE playersignal.workspace_billing SET checkout_key=NULL,checkout_plan=NULL,checkout_expires_at=NULL,checkout_id=NULL,checkout_url=NULL WHERE workspace_id=?",workspace);
            jdbc.update("UPDATE playersignal.workspace_billing SET subscription_id=NULL,plan='FREE',status='none',period_end=NULL,grace_until=NULL,verified_at=now(),updated_at=now() WHERE workspace_id=?",workspace);return;}
        var sub=current.getFirst();var items=sub.path("items").path("data");String state=sub.path("status").asText();String plan="FREE";
        if(items.size()==1&&items.get(0).path("quantity").asInt()==1){String price=StripeClient.id(items.get(0).path("price"));plan=price.equals(indie)?"INDIE":price.equals(studio)?"STUDIO":"FREE";}
        var row=locked(workspace);long seconds=sub.path("current_period_end").asLong(0);if(seconds==0&&items.size()>0)seconds=items.get(0).path("current_period_end").asLong(0);Instant end=seconds>0?Instant.ofEpochSecond(seconds):null;
        Instant grace=null;if(state.equals("past_due")&&!row.get("plan").equals("FREE")){
            grace=instant(row.get("grace_until"));if(grace==null&&Set.of("active","trialing").contains((String)row.get("status"))){Instant previous=instant(row.get("period_end"));grace=(previous==null?Instant.now():previous).plusSeconds(3*86400);if(grace.isAfter(Instant.now().plusSeconds(3*86400)))grace=Instant.now().plusSeconds(3*86400);}
        }
        jdbc.update("UPDATE playersignal.workspace_billing SET subscription_id=?,plan=?,status=?,period_end=?,grace_until=?,verified_at=now(),updated_at=now() WHERE workspace_id=?",sub.path("id").asText(),plan,state,end==null?null:java.sql.Timestamp.from(end),grace==null?null:java.sql.Timestamp.from(grace),workspace);
    }
    @Scheduled(fixedDelayString="${BILLING_RECONCILE_MS:300000}")public void reconcile(){if(!plans.enabled()||!stripe.ready())return;
        for(String customer:jdbc.query("SELECT customer_id FROM playersignal.workspace_billing WHERE customer_id IS NOT NULL ORDER BY verified_at NULLS FIRST LIMIT 50",(rs,n)->rs.getString(1)))try{reconcileCustomer(customer);}catch(Exception error){org.slf4j.LoggerFactory.getLogger(getClass()).warn("Billing reconciliation unavailable type={}",error.getClass().getSimpleName());}
    }
}
