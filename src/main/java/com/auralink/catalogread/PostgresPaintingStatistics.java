package com.auralink.catalogread;

import com.auralink.api.v1.knowledge.PaintingStatisticsResponse;
import com.auralink.api.v1.knowledge.PaintingStatisticsResponse.AuthorNames;
import com.auralink.api.v1.knowledge.PaintingStatisticsResponse.Item;
import com.auralink.api.v1.knowledge.PaintingStatisticsResponse.Metric;
import com.auralink.api.v1.knowledge.PaintingStatisticsResponse.Population;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;

/** Aggregation runs inside PostgreSQL, bounded to 302 items and a 15-second statement timeout. */
final class PostgresPaintingStatistics {
   private static final String SQL = loadSql();
   private static final Map<String, Definition> DEFINITIONS = definitions();
   private final JdbcTemplate jdbc;

   PostgresPaintingStatistics(JdbcTemplate jdbc) { this.jdbc = jdbc; }

   PaintingStatisticsResponse read() {
      List<Row> rows = jdbc.query(connection -> {
         PreparedStatement statement = connection.prepareStatement(SQL);
         statement.setQueryTimeout(15);
         statement.setMaxRows(400);
         return statement;
      }, (rs, index) -> new Row(rs.getLong("population_count"), rs.getLong("author_count"),
         rs.getLong("author_eligible"), rs.getString("metric"), rs.getLong("eligible"),
         rs.getLong("unmatched"), rs.getString("label"), rs.getDouble("value"), rs.getLong("total_groups")));
      Row first = rows.get(0); // metric_keys guarantees seven rows, including an empty catalog.
      Map<String, Metric> metrics = new LinkedHashMap<>();
      for (Map.Entry<String, Definition> entry : DEFINITIONS.entrySet()) {
         String key = entry.getKey();
         Definition definition = entry.getValue();
         List<Row> metricRows = rows.stream().filter(row -> key.equals(row.metric())).toList();
         Row coverage = metricRows.get(0);
         List<Item> items = new ArrayList<>();
         for (Row row : metricRows) {
            if (row.label() != null) items.add(new Item(row.label(), row.value()));
         }
         boolean available = coverage.eligible() > 0;
         String reason = available ? null : first.population() == 0 ? "当前没有 Gallery 可展示的画作"
            : "没有可用的公开" + definition.requiredFields() + "；缺失、待核实及受限字段不参与统计";
         metrics.put(key, new Metric(available ? "available" : "unavailable", reason, coverage.eligible(),
            first.population() - coverage.eligible(), coverage.unmatched(), items,
            coverage.totalGroups() > items.size(), definition.unit(), definition.method()));
      }
      return new PaintingStatisticsResponse(1,
         new Population("gallery-displayable", "Gallery 可展示画作", first.population()),
         new AuthorNames(first.authors(), first.authorEligible()), metrics);
   }

   private static String loadSql() {
      try (var input = new ClassPathResource("knowledge/painting-statistics.sql").getInputStream()) {
         return new String(input.readAllBytes(), StandardCharsets.UTF_8)
            .replace("/* GALLERY_SOURCE */", PostgresCatalogReadStore.CURRENT_SOURCE)
            .replace("/* GALLERY_WHERE */", PostgresCatalogReadStore.GALLERY_WHERE);
      } catch (IOException exception) {
         throw new UncheckedIOException("Missing painting statistics SQL", exception);
      }
   }

   private static Map<String, Definition> definitions() {
      Map<String, Definition> result = new LinkedHashMap<>();
      result.put("dynasty", new Definition("创作朝代", "幅",
         "公开创作朝代按现有朝代别名表精确归一；其他值保留，不由年份推断。最多显示 50 项。"));
      result.put("category", new Definition("分类", "幅",
         "每幅画作按公开分类原文计一次；复合分类不拆分。最多显示 50 项。"));
      result.put("geography", new Definition("作者姓名和出生地", "姓名与出生地组合",
         "按规范姓名与出生地文本组合去重，并非已核实人数。优先匹配省级名称，否则匹配旧版城市词表；多省冲突与未匹配地点单列为未匹配。"));
      result.put("style", new Definition("风格", "幅次",
         "公开风格按逗号、顿号、分号、空白、斜线、竖线及间隔号拆分；同画同词只计一次。显示前 12 项，总和可超过样本画作数。"));
      result.put("imagery", new Definition("题材、流派、风格、色彩、构图、意境、笔法或墨法", "幅次",
         "公开描述按分隔符拆词，仅保留 1–6 字词；同画同词只计一次，显示前 40 项。词云反映描述词覆盖，不是语义识别。"));
      result.put("sentiment", new Definition("配乐情境描述", "幅",
         "规则估计：沿用旧版 10 类关键词，命中词数最多者获选，同分按固定规则顺序；无命中列为未匹配，不调用模型。"));
      result.put("sentimentByDynasty", new Definition("意境和创作朝代", "规则估计分",
         "规则估计：沿用旧版意境关键词权重，50 分基值加命中权重×10及最多 15 分文本长度项，限于 20–100 后按朝代求平均；不是客观情绪分。"));
      return result;
   }

   private record Definition(String requiredFields, String unit, String method) {}
   private record Row(long population, long authors, long authorEligible, String metric, long eligible,
                      long unmatched, String label, double value, long totalGroups) {}
}
