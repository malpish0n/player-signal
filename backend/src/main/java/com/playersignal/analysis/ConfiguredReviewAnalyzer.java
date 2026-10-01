package com.playersignal.analysis;

import com.fasterxml.jackson.databind.*;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class ConfiguredReviewAnalyzer implements ReviewAnalyzer {
 private final AnalysisSettings settings;private final OpenAiReviewAnalyzer openai;private final LocalReviewAnalyzer local=new LocalReviewAnalyzer();
 private final ObjectMapper mapper;private final URI endpoint;private final String prompt;private final JsonNode schema;
 private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
 public ConfiguredReviewAnalyzer(AnalysisSettings settings,ObjectMapper mapper,@Value("${OPENAI_API_KEY:}")String key,@Value("${ALLOW_PAID_AI:false}")boolean allowPaid,@Value("${OLLAMA_URL:http://127.0.0.1:11434}")String url){
  this.settings=settings;this.mapper=mapper;this.openai=new OpenAiReviewAnalyzer(settings,allowPaid?key:"",mapper);
  URI root=LocalModelEndpoint.origin(url);
  endpoint=root.resolve("/api/chat");
  try(var p=new ClassPathResource("analysis/prompt-v1.txt").getInputStream();var s=new ClassPathResource("analysis/classification-schema.json").getInputStream()){prompt=new String(p.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);schema=mapper.readTree(s);}catch(java.io.IOException e){throw new IllegalStateException("Missing classification contract",e);}
 }
 public boolean available(){return switch(settings.mode()){case "local" -> true;case "ollama" -> !settings.model().isBlank();case "openai" -> openai.available();default -> false;};}
 public Output analyze(Input input){
  if(settings.mode().equals("local"))return local.analyze(input);
  if(settings.mode().equals("openai"))return openai.analyze(input);
  if(!settings.mode().equals("ollama"))throw new AnalysisFailure("NOT_CONFIGURED","Choose a local analysis provider.",false,true);
  try {
   var body=Map.of("model",settings.model(),"stream",false,"format",schema,"options",Map.of("temperature",0,"num_predict",1200),"messages",List.of(Map.of("role","system","content",prompt),Map.of("role","user","content",mapper.writeValueAsString(Map.of("language",input.language(),"reviewText",input.text())))));
   var request=HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(120)).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build();
   var response=http.send(request,HttpResponse.BodyHandlers.ofString());
   if(response.statusCode()!=200)throw new AnalysisFailure("LOCAL_MODEL_UNAVAILABLE","Start local Ollama and install the configured model. No cloud fallback is used.",false,true);
   JsonNode d=mapper.readTree(response.body());if(!d.path("done").asBoolean()||!d.path("message").path("content").isTextual())throw AnalysisFailure.invalid();
   // Reuse the existing strict classification/evidence validator without any OpenAI request.
   var envelope=mapper.createObjectNode();envelope.put("status","completed");envelope.put("model",d.path("model").asText(settings.model()));
   var usage=envelope.putObject("usage");usage.put("input_tokens",d.path("prompt_eval_count").asLong());usage.put("output_tokens",d.path("eval_count").asLong());
   var output=envelope.putArray("output").addObject();output.put("type","message");var content=output.putArray("content").addObject();content.put("type","output_text");content.put("text",d.path("message").path("content").asText());
   return openai.parse(envelope,input.text());
  }catch(InterruptedException e){Thread.currentThread().interrupt();throw new AnalysisFailure("INTERRUPTED","Local analysis interrupted.",false,true);}
  catch(java.io.IOException e){throw new AnalysisFailure("LOCAL_MODEL_UNAVAILABLE","Cannot reach the local model. Previously imported reviews remain available.",false,true);}
 }
}
