package com.playersignal.issue;

import com.fasterxml.jackson.databind.*;
import com.playersignal.analysis.LocalModelEndpoint;
import com.playersignal.shared.ApiException;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class IssueEmbeddingService {
    private final String provider,model,key;
    private final double threshold;
    private final URI endpoint;
    private final ObjectMapper mapper;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build();
    public IssueEmbeddingService(ObjectMapper mapper,@Value("${EMBEDDING_PROVIDER:lexical}")String provider,
            @Value("${EMBEDDING_MODEL:}")String model,@Value("${EMBEDDING_REVISION:v1}")String revision,
            @Value("${EMBEDDING_THRESHOLD:0.78}")double threshold,@Value("${OLLAMA_URL:http://127.0.0.1:11434}")String url) {
        if(!Set.of("lexical","ollama").contains(provider)||!Double.isFinite(threshold)||threshold<=0||threshold>1
                ||!revision.matches("[a-zA-Z0-9._-]{1,80}")||model.length()>128
                ||(provider.equals("ollama")&&(model.isBlank()||!model.matches("[a-zA-Z0-9._:/-]+")||model.toLowerCase(Locale.ROOT).contains("cloud"))))
            throw new IllegalArgumentException("Invalid local embedding configuration");
        this.mapper=mapper;this.provider=provider;this.model=model;this.key=model+"@"+revision;this.threshold=threshold;
        this.endpoint=LocalModelEndpoint.origin(url).resolve("/api/embed");
    }
    public String algorithm(){return provider.equals("lexical")?IssueEngine.VERSION:"ollama-centroid-v1:"+key;}
    public double threshold(){return provider.equals("lexical")?IssueEngine.THRESHOLD:threshold;}
    public Map<UUID,double[]> vectors(JdbcTemplate db,List<IssueEngine.Source> sources){
        var result=new HashMap<UUID,double[]>();
        if(provider.equals("lexical")){sources.forEach(s->result.put(s.analysisId(),IssueEngine.vector(s.classification().normalizedIssue())));return result;}
        if(sources.isEmpty())return result;
        var hashes=new HashMap<UUID,String>();
        sources.forEach(s->hashes.put(s.analysisId(),hash(s.classification().normalizedIssue())));
        var args=new ArrayList<Object>();args.add(key);sources.forEach(s->args.add(s.analysisId()));
        db.query("SELECT analysis_id,input_hash,vector FROM playersignal.issue_embedding WHERE model_key=? AND analysis_id IN ("+String.join(",",Collections.nCopies(sources.size(),"?"))+")",rs->{
            UUID id=rs.getObject("analysis_id",UUID.class);
            if(hashes.get(id).equals(rs.getString("input_hash")))try{result.put(id,vector(mapper.readTree(rs.getString("vector"))));}catch(java.io.IOException error){throw new IllegalStateException("Invalid stored embedding",error);}
        },args.toArray());
        var missing=new LinkedHashMap<String,String>();
        for(var source:sources)if(!result.containsKey(source.analysisId()))missing.putIfAbsent(hashes.get(source.analysisId()),source.classification().normalizedIssue());
        if(!missing.isEmpty()) {
            var generated=generate(new ArrayList<>(missing.values()));
            var byHash=new HashMap<String,double[]>();int index=0;for(String hash:missing.keySet())byHash.put(hash,generated.get(index++));
            for(var source:sources)if(!result.containsKey(source.analysisId())){
                var vector=byHash.get(hashes.get(source.analysisId()));result.put(source.analysisId(),vector);
                try{db.update("INSERT INTO playersignal.issue_embedding(analysis_id,model_key,input_hash,vector) VALUES (?,?,?,?::jsonb) ON CONFLICT DO NOTHING",source.analysisId(),key,hashes.get(source.analysisId()),mapper.writeValueAsString(vector));}
                catch(com.fasterxml.jackson.core.JsonProcessingException error){throw new IllegalStateException("Embedding serialization failed",error);}
            }
        }
        int dimensions=result.values().iterator().next().length;
        if(result.values().stream().anyMatch(v->v.length!=dimensions))throw invalid();
        return result;
    }
    private List<double[]> generate(List<String> text){
        try {
            String body=mapper.writeValueAsString(Map.of("model",model,"input",text,"truncate",false));
            var request=HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(35)).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build();
            // Bound the response in memory and retain HttpClient's request timeout while consuming it.
            var response=http.send(request,limitedBody());
            if(response.statusCode()!=200)throw new ApiException(503,"LOCAL_EMBEDDING_UNAVAILABLE","Local embeddings unavailable. Install/configure the local embedding model; the previous grouping is preserved.");
            var data=mapper.readTree(response.body()).path("embeddings");
            if(!data.isArray()||data.size()!=text.size())throw invalid();
            var out=new ArrayList<double[]>();for(var item:data)out.add(vector(item));return out;
        }catch(InterruptedException error){Thread.currentThread().interrupt();throw new ApiException(503,"EMBEDDING_INTERRUPTED","Local embedding request interrupted; previous grouping preserved.");}
        catch(java.io.IOException error){throw new ApiException(503,"LOCAL_EMBEDDING_UNAVAILABLE","Local embedding request failed; previous grouping preserved.");}
    }
    private HttpResponse.BodyHandler<byte[]> limitedBody(){
        return info -> new HttpResponse.BodySubscriber<>() {
            private final java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();
            private final java.util.concurrent.CompletableFuture<byte[]> future=new java.util.concurrent.CompletableFuture<>();
            private java.util.concurrent.Flow.Subscription subscription;
            public java.util.concurrent.CompletionStage<byte[]> getBody(){return future;}
            public void onSubscribe(java.util.concurrent.Flow.Subscription value){subscription=value;value.request(1);}
            public void onNext(List<java.nio.ByteBuffer> buffers){for(var buffer:buffers){if((long)out.size()+buffer.remaining()>32*1024*1024){subscription.cancel();future.completeExceptionally(new java.io.IOException("Embedding response too large"));return;}byte[] bytes=new byte[buffer.remaining()];buffer.get(bytes);out.writeBytes(bytes);}subscription.request(1);}
            public void onError(Throwable error){future.completeExceptionally(error);}
            public void onComplete(){future.complete(out.toByteArray());}
        };
    }
    private double[] vector(JsonNode value){
        if(!value.isArray()||value.size()<2||value.size()>4096)throw invalid();
        double[] out=new double[value.size()];double norm=0;
        for(int i=0;i<out.length;i++){if(!value.get(i).isNumber())throw invalid();out[i]=value.get(i).asDouble();if(!Double.isFinite(out[i]))throw invalid();norm+=out[i]*out[i];}
        if(!Double.isFinite(norm)||norm<=0)throw invalid();norm=Math.sqrt(norm);for(int i=0;i<out.length;i++)out[i]/=norm;return out;
    }
    private ApiException invalid(){return new ApiException(503,"INVALID_EMBEDDING","Local embeddings are invalid or dimensions changed. Bump EMBEDDING_REVISION after replacing a model; previous grouping preserved.");}
    private String hash(String text){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException error){throw new IllegalStateException(error);}}
}
