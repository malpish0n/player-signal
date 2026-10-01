package com.playersignal.analysis;

import java.util.*;
import java.util.regex.Pattern;
import static com.playersignal.analysis.Classification.*;

/** Conservative English phrase rules applied to actual input, never demonstration fixtures. */
public final class LocalReviewAnalyzer implements ReviewAnalyzer {
 private record Rule(Category category,String title,Pattern pattern) {}
 private static Rule rule(Category c,String title,String regex){return new Rule(c,title,Pattern.compile(regex,Pattern.CASE_INSENSITIVE));}
 private static final List<Rule> RULES=List.of(
  rule(Category.BUG,"Reported crashes","\\b(game crashes|keeps crashing|crashes on startup|crashes every time)\\b"),
  rule(Category.BUG,"Reported save loss","\\b(lost my save|save (file )?(is )?corrupt(ed)?|progress was lost)\\b"),
  rule(Category.PERFORMANCE,"Reported frame-rate problems","\\b(low fps|frame rate drops|framerate drops|frequent stuttering)\\b"),
  rule(Category.MULTIPLAYER,"Reported connection failures","\\b(keeps disconnecting|cannot join|can't join|connection timed out)\\b"),
  rule(Category.CONTROLS,"Reported controller failures","\\b(controller (does not|doesn't) work|unresponsive controls)\\b"),
  rule(Category.AUDIO,"Reported missing audio","\\b(no sound|audio (does not|doesn't) work)\\b")
 );
 private static final Pattern NEGATION=Pattern.compile("\\b(no longer|never|not|doesn't|does not|fixed|resolved|without)\\b",Pattern.CASE_INSENSITIVE);
 public boolean available(){return true;}
 public Output analyze(Input input){
  for(String sentence:input.text().split("(?<=[.!?])\\s+|\\r?\\n")) {
   if(sentence.length()>500||NEGATION.matcher(sentence).find())continue;
   for(var rule:RULES)if(rule.pattern().matcher(sentence).find())return new Output(new Classification(Sentiment.NEGATIVE,rule.category(),Severity.MEDIUM,.35,rule.title(),true,rule.category()==Category.BUG,List.of(sentence),List.of("local-rule","human-review-required")).validate(input.text()),"local-rules-v1",0,0);
  }
  return new Output(new Classification(Sentiment.MIXED,Category.OTHER,Severity.LOW,.1,"",false,false,List.of(),List.of("local-rule","unclassified")).validate(input.text()),"local-rules-v1",0,0);
 }
}
