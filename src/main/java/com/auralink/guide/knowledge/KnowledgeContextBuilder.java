package com.auralink.guide.knowledge;

import com.auralink.config.properties.GuideProperties;
import com.auralink.guide.context.PaintingGuideContext;
import java.text.Normalizer;
import java.text.Normalizer.Form;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class KnowledgeContextBuilder {
   public static final String SOURCE_TYPE = "POETRY_GRAPH_NODE";
   private static final Pattern STRUCTURAL_PUNCTUATION = Pattern.compile("[\\s《》〈〉“”\\\"'·,.，。；;：:!?！？、()（）\\[\\]{}]+");
   private static final Set<String> STOP_TERMS = Set.of(
      "中国", "作品", "画作", "艺术", "绘画", "文化", "人物", "山水", "时代", "历史", "作者", "诗歌", "诗词", "生活", "表现", "情感", "自然", "一种", "重要", "相关", "创作", "主题", "场景"
   );
   private static final Map<String, String> DYNASTY_ALIASES = Map.ofEntries(
      Map.entry("唐", "唐"),
      Map.entry("唐朝", "唐"),
      Map.entry("唐代", "唐"),
      Map.entry("宋", "宋"),
      Map.entry("宋朝", "宋"),
      Map.entry("宋代", "宋"),
      Map.entry("元", "元"),
      Map.entry("元朝", "元"),
      Map.entry("元代", "元"),
      Map.entry("明", "明"),
      Map.entry("明朝", "明"),
      Map.entry("明代", "明"),
      Map.entry("清", "清"),
      Map.entry("清朝", "清"),
      Map.entry("清代", "清")
   );
   private final GuideProperties properties;
   private final StaticKnowledgeLoader loader;

   public KnowledgeContextBuilder(GuideProperties properties, StaticKnowledgeLoader loader) {
      this.properties = properties;
      this.loader = loader;
   }

   public KnowledgeSelection build(PaintingGuideContext context) {
      if (context == null) {
         throw new IllegalArgumentException("Painting guide context is required");
      }

      StaticKnowledgeLoader.LoadedKnowledge knowledge = this.loader.load();
      int itemLimit = Math.max(0, this.properties.getMaxKnowledgeItems());
      int characterLimit = Math.max(0, this.properties.getMaxKnowledgeChars());
      if (itemLimit != 0 && characterLimit != 0) {
         List<KnowledgeContextBuilder.Signal> signals = this.signals(context);
         List<KnowledgeContextBuilder.ScoredNode> candidates = knowledge.nodes()
            .stream()
            .map(nodex -> new KnowledgeContextBuilder.ScoredNode(nodex, this.score(nodex, signals)))
            .filter(candidatex -> candidatex.score() > 0)
            .sorted(
               Comparator.comparingInt(KnowledgeContextBuilder.ScoredNode::score)
                  .reversed()
                  .thenComparingInt(candidatex -> this.categoryPriority(candidatex.node().category()))
                  .thenComparing(candidatex -> candidatex.node().id())
            )
            .toList();
         List<KnowledgeItem> selected = new ArrayList<>();
         int usedCharacters = 0;

         for (KnowledgeContextBuilder.ScoredNode candidate : candidates) {
            if (selected.size() >= itemLimit || usedCharacters >= characterLimit) {
               break;
            }

            StaticKnowledgeLoader.KnowledgeNode node = candidate.node();
            int fixedCharacters = this.characterCount(node.id()) + this.characterCount("POETRY_GRAPH_NODE") + this.characterCount(node.name());
            int contentBudget = characterLimit - usedCharacters - fixedCharacters;
            if (contentBudget > 0) {
               String content = this.truncate(node.description(), contentBudget);
               if (!content.isBlank()) {
                  KnowledgeItem item = new KnowledgeItem(node.id(), "POETRY_GRAPH_NODE", node.name(), content);
                  selected.add(item);
                  usedCharacters += fixedCharacters + this.characterCount(content);
               }
            }
         }

         return new KnowledgeSelection(selected, knowledge.fingerprints());
      } else {
         return new KnowledgeSelection(List.of(), knowledge.fingerprints());
      }
   }

   private List<KnowledgeContextBuilder.Signal> signals(PaintingGuideContext context) {
      Map<String, KnowledgeContextBuilder.Signal> signals = new LinkedHashMap<>();
      this.add(signals, context.basic().title(), 120);
      this.add(signals, context.artist().name(), 110);
      this.add(signals, context.art().subject(), 95);
      this.add(signals, context.art().culturalSymbol(), 90);
      this.add(signals, context.art().artisticConception(), 85);
      this.add(signals, context.art().paintingSchool(), 75);
      this.add(signals, context.basic().creationDynastyRaw(), 65);
      this.add(signals, context.basic().creationDynastyNormalized(), 65);
      this.add(signals, context.officialAnnotations().generatedText(), 45);
      this.add(signals, context.officialAnnotations().musicSceneDescription(), 40);
      return List.copyOf(signals.values());
   }

   private void add(Map<String, KnowledgeContextBuilder.Signal> signals, String text, int weight) {
      String normalized = this.normalize(text);
      if (!normalized.isEmpty()) {
         signals.merge(
            normalized,
            new KnowledgeContextBuilder.Signal(normalized, weight),
            (left, right) -> (KnowledgeContextBuilder.Signal)(left.weight() >= right.weight() ? left : right)
         );
      }
   }

   private int score(StaticKnowledgeLoader.KnowledgeNode node, List<KnowledgeContextBuilder.Signal> signals) {
      String term = this.normalize(node.name());
      if (term.codePointCount(0, term.length()) >= 2 && !STOP_TERMS.contains(term)) {
         int score = 0;

         for (KnowledgeContextBuilder.Signal signal : signals) {
            if (signal.text().contains(term)) {
               score = Math.max(score, signal.weight() + Math.min(20, this.characterCount(term)));
            }

            String nodeDynasty = DYNASTY_ALIASES.get(term);
            String signalDynasty = DYNASTY_ALIASES.get(signal.text());
            if (nodeDynasty != null && nodeDynasty.equals(signalDynasty)) {
               score = Math.max(score, signal.weight() + 10);
            }
         }

         return score;
      } else {
         return 0;
      }
   }

   private String normalize(String value) {
      if (value == null) {
         return "";
      }

      String compatible = Normalizer.normalize(value, Form.NFKC).strip().toLowerCase(Locale.ROOT);
      return STRUCTURAL_PUNCTUATION.matcher(compatible).replaceAll("");
   }

   private int categoryPriority(String category) {
      return switch (category) {
         case "事件/诗词" -> 0;
         case "人物/诗人" -> 1;
         case "地理", "地点" -> 2;
         case "朝代/类别" -> 3;
         default -> 4;
      };
   }

   private int characterCount(String value) {
      return value.codePointCount(0, value.length());
   }

   private String truncate(String value, int maximumCharacters) {
      int count = this.characterCount(value);
      if (count <= maximumCharacters) {
         return value;
      }

      int end = value.offsetByCodePoints(0, maximumCharacters);
      return value.substring(0, end).stripTrailing();
   }

   private record ScoredNode(StaticKnowledgeLoader.KnowledgeNode node, int score) {
   }

   private record Signal(String text, int weight) {
   }
}
