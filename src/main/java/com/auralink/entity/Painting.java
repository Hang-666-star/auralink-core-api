package com.auralink.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;

@Entity
@Table(
   name = "paintings",
   uniqueConstraints = {
         @UniqueConstraint(name = "uq_paintings_public_id", columnNames = "public_id"),
         @UniqueConstraint(name = "uq_paintings_source_key", columnNames = "source_key")
   },
   indexes = {
         @Index(name = "idx_paintings_image_storage_name", columnList = "image_storage_name"),
         @Index(name = "idx_paintings_gallery_status", columnList = "visible_in_gallery, status"),
         @Index(name = "idx_paintings_dynasty", columnList = "creation_dynasty_normalized"),
         @Index(name = "idx_paintings_category", columnList = "category"),
         @Index(name = "idx_paintings_author_name", columnList = "author_name"),
         @Index(name = "idx_paintings_image_asset", columnList = "image_asset_id")
   }
)
public class Painting extends BasePublicIdEntity {
   @Column(name = "source_key", nullable = false, length = 1024)
   private String sourceKey;
   @Column(name = "source_sequence", length = 64)
   private String sourceSequence;
   @Column(name = "image_storage_name", nullable = false, length = 512)
   private String imageStorageName;
   @Column(length = 512)
   private String title;
   @Column(name = "author_name", length = 255)
   private String authorName;
   @Column(name = "author_birth_year", length = 64)
   private String authorBirthYear;
   @Column(name = "author_birth_place", length = 512)
   private String authorBirthPlace;
   @Column(name = "author_school", length = 255)
   private String authorSchool;
   @Column(name = "creation_year", length = 255)
   private String creationYear;
   @Column(name = "creation_dynasty_raw", length = 255)
   private String creationDynastyRaw;
   @Column(name = "creation_dynasty_normalized", length = 255)
   private String creationDynastyNormalized;
   @Column(name = "actual_size", length = 255)
   private String actualSize;
   @Column(name = "collection_institution", length = 512)
   private String collectionInstitution;
   @Column(length = 255)
   private String category;
   @Column(length = 512)
   private String subject;
   @Column(name = "painting_school", length = 255)
   private String paintingSchool;
   @Column(columnDefinition = "TEXT")
   private String style;
   @Column(columnDefinition = "TEXT")
   private String color;
   @Column(columnDefinition = "TEXT")
   private String composition;
   @Column(name = "artistic_conception", columnDefinition = "TEXT")
   private String artisticConception;
   @Column(columnDefinition = "TEXT")
   private String brushwork;
   @Column(name = "ink_method", columnDefinition = "TEXT")
   private String inkMethod;
   @Column(name = "painting_material", columnDefinition = "TEXT")
   private String paintingMaterial;
   @Column(columnDefinition = "TEXT")
   private String pigment;
   @Column(columnDefinition = "TEXT")
   private String seal;
   @Column(name = "cultural_symbol", columnDefinition = "TEXT")
   private String culturalSymbol;
   @Column(name = "generated_text", columnDefinition = "TEXT")
   private String generatedText;
   @Column(name = "music_scene_description", columnDefinition = "TEXT")
   private String musicSceneDescription;
   @Column(name = "collection_platform", length = 512)
   private String collectionPlatform;
   @ManyToOne(fetch = FetchType.LAZY, optional = true)
   @JoinColumn(name = "image_asset_id", foreignKey = @ForeignKey(name = "fk_paintings_image_asset"))
   private MediaAsset imageAsset;
   @Column(name = "image_available", nullable = false)
   private boolean imageAvailable;
   @Column(name = "visible_in_gallery", nullable = false)
   private boolean visibleInGallery;
   @Column(nullable = false, length = 64)
   private String status;
   @Column(name = "created_at", nullable = false)
   private LocalDateTime createdAt;
   @Column(name = "updated_at", nullable = false)
   private LocalDateTime updatedAt;

   @PrePersist
   protected void initializeTimestamps() {
      LocalDateTime now = LocalDateTime.now();
      if (this.createdAt == null) {
         this.createdAt = now;
      }

      if (this.updatedAt == null) {
         this.updatedAt = now;
      }
   }

   @PreUpdate
   protected void updateTimestamp() {
      this.updatedAt = LocalDateTime.now();
   }

   private static boolean $default$imageAvailable() {
      return false;
   }

   private static boolean $default$visibleInGallery() {
      return true;
   }

   protected Painting(final Painting.PaintingBuilder<?, ?> b) {
      super(b);
      this.sourceKey = b.sourceKey;
      this.sourceSequence = b.sourceSequence;
      this.imageStorageName = b.imageStorageName;
      this.title = b.title;
      this.authorName = b.authorName;
      this.authorBirthYear = b.authorBirthYear;
      this.authorBirthPlace = b.authorBirthPlace;
      this.authorSchool = b.authorSchool;
      this.creationYear = b.creationYear;
      this.creationDynastyRaw = b.creationDynastyRaw;
      this.creationDynastyNormalized = b.creationDynastyNormalized;
      this.actualSize = b.actualSize;
      this.collectionInstitution = b.collectionInstitution;
      this.category = b.category;
      this.subject = b.subject;
      this.paintingSchool = b.paintingSchool;
      this.style = b.style;
      this.color = b.color;
      this.composition = b.composition;
      this.artisticConception = b.artisticConception;
      this.brushwork = b.brushwork;
      this.inkMethod = b.inkMethod;
      this.paintingMaterial = b.paintingMaterial;
      this.pigment = b.pigment;
      this.seal = b.seal;
      this.culturalSymbol = b.culturalSymbol;
      this.generatedText = b.generatedText;
      this.musicSceneDescription = b.musicSceneDescription;
      this.collectionPlatform = b.collectionPlatform;
      this.imageAsset = b.imageAsset;
      if (b.imageAvailable$set) {
         this.imageAvailable = b.imageAvailable$value;
      } else {
         this.imageAvailable = $default$imageAvailable();
      }

      if (b.visibleInGallery$set) {
         this.visibleInGallery = b.visibleInGallery$value;
      } else {
         this.visibleInGallery = $default$visibleInGallery();
      }

      this.status = b.status;
      this.createdAt = b.createdAt;
      this.updatedAt = b.updatedAt;
   }

   public static Painting.PaintingBuilder<?, ?> builder() {
      return new Painting.PaintingBuilderImpl();
   }

   public String getSourceKey() {
      return this.sourceKey;
   }

   public String getSourceSequence() {
      return this.sourceSequence;
   }

   public String getImageStorageName() {
      return this.imageStorageName;
   }

   public String getTitle() {
      return this.title;
   }

   public String getAuthorName() {
      return this.authorName;
   }

   public String getAuthorBirthYear() {
      return this.authorBirthYear;
   }

   public String getAuthorBirthPlace() {
      return this.authorBirthPlace;
   }

   public String getAuthorSchool() {
      return this.authorSchool;
   }

   public String getCreationYear() {
      return this.creationYear;
   }

   public String getCreationDynastyRaw() {
      return this.creationDynastyRaw;
   }

   public String getCreationDynastyNormalized() {
      return this.creationDynastyNormalized;
   }

   public String getActualSize() {
      return this.actualSize;
   }

   public String getCollectionInstitution() {
      return this.collectionInstitution;
   }

   public String getCategory() {
      return this.category;
   }

   public String getSubject() {
      return this.subject;
   }

   public String getPaintingSchool() {
      return this.paintingSchool;
   }

   public String getStyle() {
      return this.style;
   }

   public String getColor() {
      return this.color;
   }

   public String getComposition() {
      return this.composition;
   }

   public String getArtisticConception() {
      return this.artisticConception;
   }

   public String getBrushwork() {
      return this.brushwork;
   }

   public String getInkMethod() {
      return this.inkMethod;
   }

   public String getPaintingMaterial() {
      return this.paintingMaterial;
   }

   public String getPigment() {
      return this.pigment;
   }

   public String getSeal() {
      return this.seal;
   }

   public String getCulturalSymbol() {
      return this.culturalSymbol;
   }

   public String getGeneratedText() {
      return this.generatedText;
   }

   public String getMusicSceneDescription() {
      return this.musicSceneDescription;
   }

   public String getCollectionPlatform() {
      return this.collectionPlatform;
   }

   public MediaAsset getImageAsset() {
      return this.imageAsset;
   }

   public boolean isImageAvailable() {
      return this.imageAvailable;
   }

   public boolean isVisibleInGallery() {
      return this.visibleInGallery;
   }

   public String getStatus() {
      return this.status;
   }

   public LocalDateTime getCreatedAt() {
      return this.createdAt;
   }

   public LocalDateTime getUpdatedAt() {
      return this.updatedAt;
   }

   public void setSourceKey(final String sourceKey) {
      this.sourceKey = sourceKey;
   }

   public void setSourceSequence(final String sourceSequence) {
      this.sourceSequence = sourceSequence;
   }

   public void setImageStorageName(final String imageStorageName) {
      this.imageStorageName = imageStorageName;
   }

   public void setTitle(final String title) {
      this.title = title;
   }

   public void setAuthorName(final String authorName) {
      this.authorName = authorName;
   }

   public void setAuthorBirthYear(final String authorBirthYear) {
      this.authorBirthYear = authorBirthYear;
   }

   public void setAuthorBirthPlace(final String authorBirthPlace) {
      this.authorBirthPlace = authorBirthPlace;
   }

   public void setAuthorSchool(final String authorSchool) {
      this.authorSchool = authorSchool;
   }

   public void setCreationYear(final String creationYear) {
      this.creationYear = creationYear;
   }

   public void setCreationDynastyRaw(final String creationDynastyRaw) {
      this.creationDynastyRaw = creationDynastyRaw;
   }

   public void setCreationDynastyNormalized(final String creationDynastyNormalized) {
      this.creationDynastyNormalized = creationDynastyNormalized;
   }

   public void setActualSize(final String actualSize) {
      this.actualSize = actualSize;
   }

   public void setCollectionInstitution(final String collectionInstitution) {
      this.collectionInstitution = collectionInstitution;
   }

   public void setCategory(final String category) {
      this.category = category;
   }

   public void setSubject(final String subject) {
      this.subject = subject;
   }

   public void setPaintingSchool(final String paintingSchool) {
      this.paintingSchool = paintingSchool;
   }

   public void setStyle(final String style) {
      this.style = style;
   }

   public void setColor(final String color) {
      this.color = color;
   }

   public void setComposition(final String composition) {
      this.composition = composition;
   }

   public void setArtisticConception(final String artisticConception) {
      this.artisticConception = artisticConception;
   }

   public void setBrushwork(final String brushwork) {
      this.brushwork = brushwork;
   }

   public void setInkMethod(final String inkMethod) {
      this.inkMethod = inkMethod;
   }

   public void setPaintingMaterial(final String paintingMaterial) {
      this.paintingMaterial = paintingMaterial;
   }

   public void setPigment(final String pigment) {
      this.pigment = pigment;
   }

   public void setSeal(final String seal) {
      this.seal = seal;
   }

   public void setCulturalSymbol(final String culturalSymbol) {
      this.culturalSymbol = culturalSymbol;
   }

   public void setGeneratedText(final String generatedText) {
      this.generatedText = generatedText;
   }

   public void setMusicSceneDescription(final String musicSceneDescription) {
      this.musicSceneDescription = musicSceneDescription;
   }

   public void setCollectionPlatform(final String collectionPlatform) {
      this.collectionPlatform = collectionPlatform;
   }

   public void setImageAsset(final MediaAsset imageAsset) {
      this.imageAsset = imageAsset;
   }

   public void setImageAvailable(final boolean imageAvailable) {
      this.imageAvailable = imageAvailable;
   }

   public void setVisibleInGallery(final boolean visibleInGallery) {
      this.visibleInGallery = visibleInGallery;
   }

   public void setStatus(final String status) {
      this.status = status;
   }

   public void setCreatedAt(final LocalDateTime createdAt) {
      this.createdAt = createdAt;
   }

   public void setUpdatedAt(final LocalDateTime updatedAt) {
      this.updatedAt = updatedAt;
   }

   public Painting() {
      this.imageAvailable = $default$imageAvailable();
      this.visibleInGallery = $default$visibleInGallery();
   }

   public Painting(
      final String sourceKey,
      final String sourceSequence,
      final String imageStorageName,
      final String title,
      final String authorName,
      final String authorBirthYear,
      final String authorBirthPlace,
      final String authorSchool,
      final String creationYear,
      final String creationDynastyRaw,
      final String creationDynastyNormalized,
      final String actualSize,
      final String collectionInstitution,
      final String category,
      final String subject,
      final String paintingSchool,
      final String style,
      final String color,
      final String composition,
      final String artisticConception,
      final String brushwork,
      final String inkMethod,
      final String paintingMaterial,
      final String pigment,
      final String seal,
      final String culturalSymbol,
      final String generatedText,
      final String musicSceneDescription,
      final String collectionPlatform,
      final MediaAsset imageAsset,
      final boolean imageAvailable,
      final boolean visibleInGallery,
      final String status,
      final LocalDateTime createdAt,
      final LocalDateTime updatedAt
   ) {
      this.sourceKey = sourceKey;
      this.sourceSequence = sourceSequence;
      this.imageStorageName = imageStorageName;
      this.title = title;
      this.authorName = authorName;
      this.authorBirthYear = authorBirthYear;
      this.authorBirthPlace = authorBirthPlace;
      this.authorSchool = authorSchool;
      this.creationYear = creationYear;
      this.creationDynastyRaw = creationDynastyRaw;
      this.creationDynastyNormalized = creationDynastyNormalized;
      this.actualSize = actualSize;
      this.collectionInstitution = collectionInstitution;
      this.category = category;
      this.subject = subject;
      this.paintingSchool = paintingSchool;
      this.style = style;
      this.color = color;
      this.composition = composition;
      this.artisticConception = artisticConception;
      this.brushwork = brushwork;
      this.inkMethod = inkMethod;
      this.paintingMaterial = paintingMaterial;
      this.pigment = pigment;
      this.seal = seal;
      this.culturalSymbol = culturalSymbol;
      this.generatedText = generatedText;
      this.musicSceneDescription = musicSceneDescription;
      this.collectionPlatform = collectionPlatform;
      this.imageAsset = imageAsset;
      this.imageAvailable = imageAvailable;
      this.visibleInGallery = visibleInGallery;
      this.status = status;
      this.createdAt = createdAt;
      this.updatedAt = updatedAt;
   }

   public abstract static class PaintingBuilder<C extends Painting, B extends Painting.PaintingBuilder<C, B>>
      extends BasePublicIdEntity.BasePublicIdEntityBuilder<C, B> {
      private String sourceKey;
      private String sourceSequence;
      private String imageStorageName;
      private String title;
      private String authorName;
      private String authorBirthYear;
      private String authorBirthPlace;
      private String authorSchool;
      private String creationYear;
      private String creationDynastyRaw;
      private String creationDynastyNormalized;
      private String actualSize;
      private String collectionInstitution;
      private String category;
      private String subject;
      private String paintingSchool;
      private String style;
      private String color;
      private String composition;
      private String artisticConception;
      private String brushwork;
      private String inkMethod;
      private String paintingMaterial;
      private String pigment;
      private String seal;
      private String culturalSymbol;
      private String generatedText;
      private String musicSceneDescription;
      private String collectionPlatform;
      private MediaAsset imageAsset;
      private boolean imageAvailable$set;
      private boolean imageAvailable$value;
      private boolean visibleInGallery$set;
      private boolean visibleInGallery$value;
      private String status;
      private LocalDateTime createdAt;
      private LocalDateTime updatedAt;

      public B sourceKey(final String sourceKey) {
         this.sourceKey = sourceKey;
         return this.self();
      }

      public B sourceSequence(final String sourceSequence) {
         this.sourceSequence = sourceSequence;
         return this.self();
      }

      public B imageStorageName(final String imageStorageName) {
         this.imageStorageName = imageStorageName;
         return this.self();
      }

      public B title(final String title) {
         this.title = title;
         return this.self();
      }

      public B authorName(final String authorName) {
         this.authorName = authorName;
         return this.self();
      }

      public B authorBirthYear(final String authorBirthYear) {
         this.authorBirthYear = authorBirthYear;
         return this.self();
      }

      public B authorBirthPlace(final String authorBirthPlace) {
         this.authorBirthPlace = authorBirthPlace;
         return this.self();
      }

      public B authorSchool(final String authorSchool) {
         this.authorSchool = authorSchool;
         return this.self();
      }

      public B creationYear(final String creationYear) {
         this.creationYear = creationYear;
         return this.self();
      }

      public B creationDynastyRaw(final String creationDynastyRaw) {
         this.creationDynastyRaw = creationDynastyRaw;
         return this.self();
      }

      public B creationDynastyNormalized(final String creationDynastyNormalized) {
         this.creationDynastyNormalized = creationDynastyNormalized;
         return this.self();
      }

      public B actualSize(final String actualSize) {
         this.actualSize = actualSize;
         return this.self();
      }

      public B collectionInstitution(final String collectionInstitution) {
         this.collectionInstitution = collectionInstitution;
         return this.self();
      }

      public B category(final String category) {
         this.category = category;
         return this.self();
      }

      public B subject(final String subject) {
         this.subject = subject;
         return this.self();
      }

      public B paintingSchool(final String paintingSchool) {
         this.paintingSchool = paintingSchool;
         return this.self();
      }

      public B style(final String style) {
         this.style = style;
         return this.self();
      }

      public B color(final String color) {
         this.color = color;
         return this.self();
      }

      public B composition(final String composition) {
         this.composition = composition;
         return this.self();
      }

      public B artisticConception(final String artisticConception) {
         this.artisticConception = artisticConception;
         return this.self();
      }

      public B brushwork(final String brushwork) {
         this.brushwork = brushwork;
         return this.self();
      }

      public B inkMethod(final String inkMethod) {
         this.inkMethod = inkMethod;
         return this.self();
      }

      public B paintingMaterial(final String paintingMaterial) {
         this.paintingMaterial = paintingMaterial;
         return this.self();
      }

      public B pigment(final String pigment) {
         this.pigment = pigment;
         return this.self();
      }

      public B seal(final String seal) {
         this.seal = seal;
         return this.self();
      }

      public B culturalSymbol(final String culturalSymbol) {
         this.culturalSymbol = culturalSymbol;
         return this.self();
      }

      public B generatedText(final String generatedText) {
         this.generatedText = generatedText;
         return this.self();
      }

      public B musicSceneDescription(final String musicSceneDescription) {
         this.musicSceneDescription = musicSceneDescription;
         return this.self();
      }

      public B collectionPlatform(final String collectionPlatform) {
         this.collectionPlatform = collectionPlatform;
         return this.self();
      }

      public B imageAsset(final MediaAsset imageAsset) {
         this.imageAsset = imageAsset;
         return this.self();
      }

      public B imageAvailable(final boolean imageAvailable) {
         this.imageAvailable$value = imageAvailable;
         this.imageAvailable$set = true;
         return this.self();
      }

      public B visibleInGallery(final boolean visibleInGallery) {
         this.visibleInGallery$value = visibleInGallery;
         this.visibleInGallery$set = true;
         return this.self();
      }

      public B status(final String status) {
         this.status = status;
         return this.self();
      }

      public B createdAt(final LocalDateTime createdAt) {
         this.createdAt = createdAt;
         return this.self();
      }

      public B updatedAt(final LocalDateTime updatedAt) {
         this.updatedAt = updatedAt;
         return this.self();
      }

      protected abstract B self();

      public abstract C build();

      @Override
      public String toString() {
         return "Painting.PaintingBuilder(super="
            + super.toString()
            + ", sourceKey="
            + this.sourceKey
            + ", sourceSequence="
            + this.sourceSequence
            + ", imageStorageName="
            + this.imageStorageName
            + ", title="
            + this.title
            + ", authorName="
            + this.authorName
            + ", authorBirthYear="
            + this.authorBirthYear
            + ", authorBirthPlace="
            + this.authorBirthPlace
            + ", authorSchool="
            + this.authorSchool
            + ", creationYear="
            + this.creationYear
            + ", creationDynastyRaw="
            + this.creationDynastyRaw
            + ", creationDynastyNormalized="
            + this.creationDynastyNormalized
            + ", actualSize="
            + this.actualSize
            + ", collectionInstitution="
            + this.collectionInstitution
            + ", category="
            + this.category
            + ", subject="
            + this.subject
            + ", paintingSchool="
            + this.paintingSchool
            + ", style="
            + this.style
            + ", color="
            + this.color
            + ", composition="
            + this.composition
            + ", artisticConception="
            + this.artisticConception
            + ", brushwork="
            + this.brushwork
            + ", inkMethod="
            + this.inkMethod
            + ", paintingMaterial="
            + this.paintingMaterial
            + ", pigment="
            + this.pigment
            + ", seal="
            + this.seal
            + ", culturalSymbol="
            + this.culturalSymbol
            + ", generatedText="
            + this.generatedText
            + ", musicSceneDescription="
            + this.musicSceneDescription
            + ", collectionPlatform="
            + this.collectionPlatform
            + ", imageAsset="
            + this.imageAsset
            + ", imageAvailable$value="
            + this.imageAvailable$value
            + ", visibleInGallery$value="
            + this.visibleInGallery$value
            + ", status="
            + this.status
            + ", createdAt="
            + this.createdAt
            + ", updatedAt="
            + this.updatedAt
            + ")";
      }
   }

   private static final class PaintingBuilderImpl extends Painting.PaintingBuilder<Painting, Painting.PaintingBuilderImpl> {
      protected Painting.PaintingBuilderImpl self() {
         return this;
      }

      @Override
      public Painting build() {
         return new Painting(this);
      }
   }
}
