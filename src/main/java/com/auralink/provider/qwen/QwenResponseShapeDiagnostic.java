package com.auralink.provider.qwen;

public record QwenResponseShapeDiagnostic(
   Boolean providerEnvelopePresent,
   Boolean choicesPresent,
   Integer choiceCount,
   Boolean messagePresent,
   Boolean reasoningContentPresent,
   QwenSafeValueType reasoningContentType,
   Boolean reasoningContentNonblank,
   Boolean contentPresent,
   QwenSafeValueType contentType,
   Integer contentLength,
   Boolean jsonParsed,
   QwenSafeValueType topLevelType,
   Boolean schemaVersionPresent,
   QwenSafeValueType schemaVersionType,
   Boolean titlePresent,
   QwenSafeValueType titleType,
   Integer titleLength,
   Boolean linesPresent,
   QwenSafeValueType linesType,
   Integer lineCount,
   Integer stringLineCount,
   Integer nonblankLineCount,
   Integer chineseDominantLineCount,
   Integer duplicateLineCount,
   Integer minimumLineLength,
   Integer maximumLineLength,
   Boolean textPresent,
   QwenSafeValueType textType,
   Integer textLength,
   Boolean textMatchesLines,
   Integer unknownFieldCount,
   Integer duplicateFieldCount,
   Boolean hasLeadingOrTrailingContent,
   Boolean hasMarkdownFence,
   Boolean hasHtml,
   Boolean hasReasoningMarker,
   Boolean hasAiSelfReference
) {
   public static final int MAX_SAFE_COUNT_OR_LENGTH = 1048576;

   public QwenResponseShapeDiagnostic {
      requireBounded("choiceCount", choiceCount);
      requireBounded("contentLength", contentLength);
      requireBounded("titleLength", titleLength);
      requireBounded("lineCount", lineCount);
      requireBounded("stringLineCount", stringLineCount);
      requireBounded("nonblankLineCount", nonblankLineCount);
      requireBounded("chineseDominantLineCount", chineseDominantLineCount);
      requireBounded("duplicateLineCount", duplicateLineCount);
      requireBounded("minimumLineLength", minimumLineLength);
      requireBounded("maximumLineLength", maximumLineLength);
      requireBounded("textLength", textLength);
      requireBounded("unknownFieldCount", unknownFieldCount);
      requireBounded("duplicateFieldCount", duplicateFieldCount);
      if (minimumLineLength != null && maximumLineLength != null && minimumLineLength > maximumLineLength) {
         throw new IllegalArgumentException("Minimum line length exceeds maximum line length");
      }
   }

   public static QwenResponseShapeDiagnostic.Builder builder() {
      return new QwenResponseShapeDiagnostic.Builder();
   }

   public QwenResponseShapeDiagnostic.Builder toBuilder() {
      return new QwenResponseShapeDiagnostic.Builder(this);
   }

   private static void requireBounded(String field, Integer value) {
      if (value != null && (value < 0 || value > 1048576)) {
         throw new IllegalArgumentException(field + " is outside the safe diagnostic bound");
      }
   }

   private static int bounded(long value) {
      if (value < 0L) {
         throw new IllegalArgumentException("Diagnostic counts and lengths must be nonnegative");
      } else {
         return (int)Math.min(value, 1048576L);
      }
   }

   public static final class Builder {
      private Boolean providerEnvelopePresent;
      private Boolean choicesPresent;
      private Integer choiceCount;
      private Boolean messagePresent;
      private Boolean reasoningContentPresent;
      private QwenSafeValueType reasoningContentType;
      private Boolean reasoningContentNonblank;
      private Boolean contentPresent;
      private QwenSafeValueType contentType;
      private Integer contentLength;
      private Boolean jsonParsed;
      private QwenSafeValueType topLevelType;
      private Boolean schemaVersionPresent;
      private QwenSafeValueType schemaVersionType;
      private Boolean titlePresent;
      private QwenSafeValueType titleType;
      private Integer titleLength;
      private Boolean linesPresent;
      private QwenSafeValueType linesType;
      private Integer lineCount;
      private Integer stringLineCount;
      private Integer nonblankLineCount;
      private Integer chineseDominantLineCount;
      private Integer duplicateLineCount;
      private Integer minimumLineLength;
      private Integer maximumLineLength;
      private Boolean textPresent;
      private QwenSafeValueType textType;
      private Integer textLength;
      private Boolean textMatchesLines;
      private Integer unknownFieldCount;
      private Integer duplicateFieldCount;
      private Boolean hasLeadingOrTrailingContent;
      private Boolean hasMarkdownFence;
      private Boolean hasHtml;
      private Boolean hasReasoningMarker;
      private Boolean hasAiSelfReference;

      private Builder() {
      }

      private Builder(QwenResponseShapeDiagnostic source) {
         this.providerEnvelopePresent = source.providerEnvelopePresent();
         this.choicesPresent = source.choicesPresent();
         this.choiceCount = source.choiceCount();
         this.messagePresent = source.messagePresent();
         this.reasoningContentPresent = source.reasoningContentPresent();
         this.reasoningContentType = source.reasoningContentType();
         this.reasoningContentNonblank = source.reasoningContentNonblank();
         this.contentPresent = source.contentPresent();
         this.contentType = source.contentType();
         this.contentLength = source.contentLength();
         this.jsonParsed = source.jsonParsed();
         this.topLevelType = source.topLevelType();
         this.schemaVersionPresent = source.schemaVersionPresent();
         this.schemaVersionType = source.schemaVersionType();
         this.titlePresent = source.titlePresent();
         this.titleType = source.titleType();
         this.titleLength = source.titleLength();
         this.linesPresent = source.linesPresent();
         this.linesType = source.linesType();
         this.lineCount = source.lineCount();
         this.stringLineCount = source.stringLineCount();
         this.nonblankLineCount = source.nonblankLineCount();
         this.chineseDominantLineCount = source.chineseDominantLineCount();
         this.duplicateLineCount = source.duplicateLineCount();
         this.minimumLineLength = source.minimumLineLength();
         this.maximumLineLength = source.maximumLineLength();
         this.textPresent = source.textPresent();
         this.textType = source.textType();
         this.textLength = source.textLength();
         this.textMatchesLines = source.textMatchesLines();
         this.unknownFieldCount = source.unknownFieldCount();
         this.duplicateFieldCount = source.duplicateFieldCount();
         this.hasLeadingOrTrailingContent = source.hasLeadingOrTrailingContent();
         this.hasMarkdownFence = source.hasMarkdownFence();
         this.hasHtml = source.hasHtml();
         this.hasReasoningMarker = source.hasReasoningMarker();
         this.hasAiSelfReference = source.hasAiSelfReference();
      }

      public QwenResponseShapeDiagnostic.Builder providerEnvelopePresent(boolean value) {
         this.providerEnvelopePresent = value;
         return this;
      }

      public QwenResponseShapeDiagnostic.Builder choicesPresent(boolean value) {
         this.choicesPresent = value;
         return this;
      }

      public QwenResponseShapeDiagnostic.Builder choiceCount(long value) {
         this.choiceCount = QwenResponseShapeDiagnostic.bounded(value);
         return this;
      }

      public QwenResponseShapeDiagnostic.Builder messagePresent(boolean value) {
         this.messagePresent = value;
         return this;
      }

      public QwenResponseShapeDiagnostic.Builder reasoningContentPresent(boolean value) {
         this.reasoningContentPresent = value;
         return this;
      }

      public QwenResponseShapeDiagnostic.Builder reasoningContentType(QwenSafeValueType value) {
         this.reasoningContentType = value;
         return this;
      }

      public QwenResponseShapeDiagnostic.Builder reasoningContentNonblank(boolean value) {
         this.reasoningContentNonblank = value;
         return this;
      }

      public QwenResponseShapeDiagnostic.Builder contentPresent(boolean value) {
         this.contentPresent = value;
         return this;
      }

      public QwenResponseShapeDiagnostic.Builder contentType(QwenSafeValueType value) {
         this.contentType = value;
         return this;
      }

      public QwenResponseShapeDiagnostic.Builder contentLength(long value) {
         this.contentLength = QwenResponseShapeDiagnostic.bounded(value);
         return this;
      }

      public QwenResponseShapeDiagnostic.Builder jsonParsed(boolean value) {
         this.jsonParsed = value;
         return this;
      }

      public QwenResponseShapeDiagnostic.Builder topLevelType(QwenSafeValueType value) {
         this.topLevelType = value;
         return this;
      }

      public QwenResponseShapeDiagnostic.Builder schemaVersionPresent(boolean value) {
         this.schemaVersionPresent = value;
         return this;
      }

      public QwenResponseShapeDiagnostic.Builder schemaVersionType(QwenSafeValueType value) {
         this.schemaVersionType = value;
         return this;
      }

      public QwenResponseShapeDiagnostic.Builder titlePresent(boolean value) {
         this.titlePresent = value;
         return this;
      }

      public QwenResponseShapeDiagnostic.Builder titleType(QwenSafeValueType value) {
         this.titleType = value;
         return this;
      }

      public QwenResponseShapeDiagnostic.Builder titleLength(long value) {
         this.titleLength = QwenResponseShapeDiagnostic.bounded(value);
         return this;
      }

      public QwenResponseShapeDiagnostic.Builder linesPresent(boolean value) {
         this.linesPresent = value;
         return this;
      }

      public QwenResponseShapeDiagnostic.Builder linesType(QwenSafeValueType value) {
         this.linesType = value;
         return this;
      }

      public QwenResponseShapeDiagnostic.Builder lineCount(long value) {
         this.lineCount = QwenResponseShapeDiagnostic.bounded(value);
         return this;
      }

      public QwenResponseShapeDiagnostic.Builder stringLineCount(long value) {
         this.stringLineCount = QwenResponseShapeDiagnostic.bounded(value);
         return this;
      }

      public QwenResponseShapeDiagnostic.Builder nonblankLineCount(long value) {
         this.nonblankLineCount = QwenResponseShapeDiagnostic.bounded(value);
         return this;
      }

      public QwenResponseShapeDiagnostic.Builder chineseDominantLineCount(long value) {
         this.chineseDominantLineCount = QwenResponseShapeDiagnostic.bounded(value);
         return this;
      }

      public QwenResponseShapeDiagnostic.Builder duplicateLineCount(long value) {
         this.duplicateLineCount = QwenResponseShapeDiagnostic.bounded(value);
         return this;
      }

      public QwenResponseShapeDiagnostic.Builder minimumLineLength(long value) {
         this.minimumLineLength = QwenResponseShapeDiagnostic.bounded(value);
         return this;
      }

      public QwenResponseShapeDiagnostic.Builder maximumLineLength(long value) {
         this.maximumLineLength = QwenResponseShapeDiagnostic.bounded(value);
         return this;
      }

      public QwenResponseShapeDiagnostic.Builder textPresent(boolean value) {
         this.textPresent = value;
         return this;
      }

      public QwenResponseShapeDiagnostic.Builder textType(QwenSafeValueType value) {
         this.textType = value;
         return this;
      }

      public QwenResponseShapeDiagnostic.Builder textLength(long value) {
         this.textLength = QwenResponseShapeDiagnostic.bounded(value);
         return this;
      }

      public QwenResponseShapeDiagnostic.Builder textMatchesLines(boolean value) {
         this.textMatchesLines = value;
         return this;
      }

      public QwenResponseShapeDiagnostic.Builder unknownFieldCount(long value) {
         this.unknownFieldCount = QwenResponseShapeDiagnostic.bounded(value);
         return this;
      }

      public QwenResponseShapeDiagnostic.Builder duplicateFieldCount(long value) {
         this.duplicateFieldCount = QwenResponseShapeDiagnostic.bounded(value);
         return this;
      }

      public QwenResponseShapeDiagnostic.Builder hasLeadingOrTrailingContent(boolean value) {
         this.hasLeadingOrTrailingContent = value;
         return this;
      }

      public QwenResponseShapeDiagnostic.Builder hasMarkdownFence(boolean value) {
         this.hasMarkdownFence = value;
         return this;
      }

      public QwenResponseShapeDiagnostic.Builder hasHtml(boolean value) {
         this.hasHtml = value;
         return this;
      }

      public QwenResponseShapeDiagnostic.Builder hasReasoningMarker(boolean value) {
         this.hasReasoningMarker = value;
         return this;
      }

      public QwenResponseShapeDiagnostic.Builder hasAiSelfReference(boolean value) {
         this.hasAiSelfReference = value;
         return this;
      }

      public QwenResponseShapeDiagnostic build() {
         return new QwenResponseShapeDiagnostic(
            this.providerEnvelopePresent,
            this.choicesPresent,
            this.choiceCount,
            this.messagePresent,
            this.reasoningContentPresent,
            this.reasoningContentType,
            this.reasoningContentNonblank,
            this.contentPresent,
            this.contentType,
            this.contentLength,
            this.jsonParsed,
            this.topLevelType,
            this.schemaVersionPresent,
            this.schemaVersionType,
            this.titlePresent,
            this.titleType,
            this.titleLength,
            this.linesPresent,
            this.linesType,
            this.lineCount,
            this.stringLineCount,
            this.nonblankLineCount,
            this.chineseDominantLineCount,
            this.duplicateLineCount,
            this.minimumLineLength,
            this.maximumLineLength,
            this.textPresent,
            this.textType,
            this.textLength,
            this.textMatchesLines,
            this.unknownFieldCount,
            this.duplicateFieldCount,
            this.hasLeadingOrTrailingContent,
            this.hasMarkdownFence,
            this.hasHtml,
            this.hasReasoningMarker,
            this.hasAiSelfReference
         );
      }
   }
}
