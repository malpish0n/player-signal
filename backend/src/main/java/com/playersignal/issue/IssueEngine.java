package com.playersignal.issue;

import com.playersignal.analysis.Classification;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Local lexical embeddings: deterministic and cost-free, not a semantic language model. */
public final class IssueEngine {
    public static final String VERSION = "lexical-centroid-v1";
    public static final double THRESHOLD = .72;
    public record Source(UUID analysisId, UUID reviewId, String steamId, String text, String language,
                         Instant seenAt, Classification classification) {}
    public record Metrics(int mentionCount, Instant firstSeenAt, Instant lastSeenAt, double negativeRatio,
                          int recentMentions, int previousMentions, Double velocityPercent, double severityScore, double confidence) {}
    public record Member(Source source, double similarity) {}
    public record Cluster(UUID id, String title, String category, double[] centroid, Metrics metrics, List<Member> mentions) {}
    private static final Set<String> STOP = Set.of("a","an","the","in","on","at","to","of","for","with","when","while","after","is","are","my","it","and","game");
    public static String normalize(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").strip();
    }
    public static double[] vector(String text) {
        double[] v = new double[512];
        for (String token : normalize(text).split(" +")) {
            if (token.isBlank() || STOP.contains(token)) continue;
            token = switch(token) {case "crashes", "crashed", "crashing" -> "crash"; case "joining", "joins", "joined" -> "join"; case "drops", "dropping" -> "drop"; case "saves" -> "save"; default -> token;};
            int hash = token.hashCode(); v[Math.floorMod(hash, v.length)] += 1;
        }
        return unit(v);
    }
    private static double[] unit(double[] v) { double norm=0; for(double d:v) norm+=d*d; if(norm>0) {norm=Math.sqrt(norm); for(int i=0;i<v.length;i++) v[i]/=norm;} return v; }
    public static double cosine(double[] a, double[] b) { double sum=0;for(int i=0;i<a.length;i++) sum+=a[i]*b[i];return Math.max(-1,Math.min(1,sum)); }
    public List<Cluster> cluster(UUID game, List<Source> input, Instant now) {
        var groups=new ArrayList<List<Source>>(); var sums=new ArrayList<double[]>(); var centroids=new ArrayList<double[]>(); var anchors=new ArrayList<double[]>();
        for(var source: input.stream().sorted(Comparator.comparing(Source::seenAt).thenComparing(s->s.analysisId().toString())).toList()) {
            var c=source.classification();
            if(!c.isActionable() || c.normalizedIssue().isBlank() || c.primaryCategory()==Classification.Category.POSITIVE) continue;
            double[] vector=vector(c.normalizedIssue()); int best=-1; double score=THRESHOLD;
            for(int i=0;i<groups.size();i++) {
                if(groups.get(i).getFirst().classification().primaryCategory()!=c.primaryCategory()) continue;
                double similarity=cosine(vector, centroids.get(i));
                // Also match the first member to prevent a chain of weakly related complaints.
                if(similarity>=score && cosine(vector, anchors.get(i))>=THRESHOLD) {best=i;score=similarity;}
            }
            if(best<0) {groups.add(new ArrayList<>(List.of(source)));sums.add(vector.clone());centroids.add(vector.clone());anchors.add(vector.clone());}
            else {groups.get(best).add(source);for(int j=0;j<vector.length;j++) sums.get(best)[j]+=vector[j];centroids.set(best,unit(sums.get(best).clone()));}
        }
        var result=new ArrayList<Cluster>();
        for(int i=0;i<groups.size();i++) {
            var members=groups.get(i);var first=members.getFirst(); var c=first.classification();double[] centroid=unit(sums.get(i));
            UUID id=UUID.nameUUIDFromBytes((game+":"+VERSION+":"+first.analysisId()).getBytes(StandardCharsets.UTF_8));
            result.add(new Cluster(id,c.normalizedIssue(),c.primaryCategory().name(),centroid,metrics(members,now),members.stream().map(s->new Member(s,cosine(vector(s.classification().normalizedIssue()),centroid))).toList()));
        }
        return result.stream().sorted(Comparator.comparingDouble((Cluster c)->c.metrics().severityScore()).reversed().thenComparing(c->c.id().toString())).toList();
    }
    public static Metrics metrics(List<Source> sources, Instant now) {
        int recent=0,previous=0,negative=0;double severity=0,confidence=0;
        var week=now.minus(7,ChronoUnit.DAYS);var fortnight=now.minus(14,ChronoUnit.DAYS);
        for(var s:sources) {
            if(!s.seenAt().isAfter(now)) {if(!s.seenAt().isBefore(week)) recent++;else if(!s.seenAt().isBefore(fortnight)) previous++;}
            var c=s.classification(); if(c.sentiment()==Classification.Sentiment.NEGATIVE || c.sentiment()==Classification.Sentiment.VERY_NEGATIVE) negative++;
            severity+=switch(c.severity()) {case LOW->25;case MEDIUM->50;case HIGH->75;case CRITICAL->100;}; confidence+=c.confidence();
        }
        return new Metrics(sources.size(),sources.stream().map(Source::seenAt).min(Instant::compareTo).orElseThrow(),sources.stream().map(Source::seenAt).max(Instant::compareTo).orElseThrow(),(double)negative/sources.size(),recent,previous,previous==0?null:100.0*(recent-previous)/previous,severity/sources.size(),confidence/sources.size());
    }
}
