package com.auralink.creation;

import com.auralink.api.v1.creation.CreationSourceRequest;
import com.auralink.api.v1.error.ApiErrorCode;
import com.auralink.api.v1.error.ApiV1Exception;
import com.auralink.config.properties.CreationProviderProperties;
import com.auralink.entity.MediaAsset;
import com.auralink.entity.Painting;
import com.auralink.entity.User;
import com.auralink.repository.MediaAssetRepository;
import com.auralink.repository.PaintingRepository;
import com.auralink.workflow.WorkflowModality;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class CreationSourceResolver {
   private static final String ACTIVE_PAINTING_STATUS = "ACTIVE";
   private final MediaAssetRepository mediaAssets;
   private final PaintingRepository paintings;
   private final CreationProviderProperties providerProperties;

   public CreationSourceResolver.ResolvedSource resolve(CreationSourceRequest request, WorkflowModality expectedModality, User owner) {
      if (request != null && request.unknownFields().isEmpty()) {
         WorkflowModality modality = this.parseModality(request.getModality());
         if (modality != expectedModality) {
            throw new ApiV1Exception(HttpStatus.UNPROCESSABLE_ENTITY, ApiErrorCode.CREATION_SOURCE_MISMATCH, "创作源类型与工作流 SOURCE 不匹配");
         }

         return switch (modality) {
            case TEXT_DESCRIPTION, POEM -> this.resolveText(request, modality);
            case IMAGE -> this.resolveImage(request, modality, owner);
            case PAINTING -> this.resolvePainting(request, modality, owner);
            default -> throw invalidSource();
         };
      } else {
         throw invalidSource();
      }
   }

   private CreationSourceResolver.ResolvedSource resolveText(CreationSourceRequest request, WorkflowModality modality) {
      if (request.hasTextField() && !request.hasAssetIdField() && !request.hasPaintingIdField()) {
         String text = request.getText();
         if (text != null && !text.isBlank() && text.length() <= this.providerProperties.getMaxTextChars() && !this.containsUnsupportedControlCharacter(text)) {
            return new CreationSourceResolver.ResolvedSource(modality, text, null, null);
         } else {
            throw invalidSource();
         }
      } else {
         throw invalidSource();
      }
   }

   private CreationSourceResolver.ResolvedSource resolveImage(CreationSourceRequest request, WorkflowModality modality, User owner) {
      if (request.hasAssetIdField() && !request.hasTextField() && !request.hasPaintingIdField()) {
         String assetId = this.canonicalUuidOrInvalid(request.getAssetId());
         MediaAsset asset = this.mediaAssets
            .findByPublicIdAndOwnerUser_IdAndStatusAndAssetType(assetId, owner.getId(), "ACTIVE", "IMAGE")
            .filter(candidate -> "PRIVATE".equals(candidate.getVisibility()) && "IMAGE".equals(candidate.getSemanticType()))
            .orElseThrow(CreationSourceResolver::invalidSource);
         return new CreationSourceResolver.ResolvedSource(modality, null, null, asset);
      } else {
         throw invalidSource();
      }
   }

   private CreationSourceResolver.ResolvedSource resolvePainting(CreationSourceRequest request, WorkflowModality modality, User owner) {
      if (request.hasTextField() || request.hasPaintingIdField() == request.hasAssetIdField()) {
         throw invalidSource();
      } else if (request.hasPaintingIdField()) {
         String paintingId = this.canonicalUuidOrInvalid(request.getPaintingId());
         Painting painting = this.paintings
            .findByPublicIdAndStatus(paintingId, "ACTIVE")
            .filter(this::hasUsableCatalogImage)
            .orElseThrow(CreationSourceResolver::invalidSource);
         return new CreationSourceResolver.ResolvedSource(modality, null, painting, null);
      } else {
         String assetId = this.canonicalUuidOrInvalid(request.getAssetId());
         MediaAsset asset = this.mediaAssets
            .findByPublicIdAndOwnerUser_IdAndStatusAndAssetType(assetId, owner.getId(), "ACTIVE", "IMAGE")
            .filter(candidate -> "PRIVATE".equals(candidate.getVisibility()) && "PAINTING".equals(candidate.getSemanticType()))
            .orElseThrow(CreationSourceResolver::invalidSource);
         return new CreationSourceResolver.ResolvedSource(modality, null, null, asset);
      }
   }

   private boolean hasUsableCatalogImage(Painting painting) {
      MediaAsset image = painting.getImageAsset();
      return painting.isImageAvailable()
         && image != null
         && "ACTIVE".equals(image.getStatus())
         && "IMAGE".equals(image.getAssetType())
         && "CATALOG_REFERENCE".equals(image.getSourceType())
         && "PUBLIC".equals(image.getVisibility());
   }

   private WorkflowModality parseModality(String value) {
      try {
         return WorkflowModality.valueOf(value);
      } catch (IllegalArgumentException | NullPointerException exception) {
         throw invalidSource();
      }
   }

   private String canonicalUuidOrInvalid(String value) {
      try {
         String canonical = UUID.fromString(value).toString();
         if (!canonical.equals(value)) {
            throw invalidSource();
         } else {
            return canonical;
         }
      } catch (IllegalArgumentException | NullPointerException exception) {
         throw invalidSource();
      }
   }

   private boolean containsUnsupportedControlCharacter(String text) {
      return text.codePoints().anyMatch(codePoint -> Character.isISOControl(codePoint) && codePoint != 10 && codePoint != 13 && codePoint != 9);
   }

   private static ApiV1Exception invalidSource() {
      return new ApiV1Exception(HttpStatus.UNPROCESSABLE_ENTITY, ApiErrorCode.CREATION_SOURCE_INVALID, "创作源无效或不可访问");
   }

   public CreationSourceResolver(
      final MediaAssetRepository mediaAssets, final PaintingRepository paintings, final CreationProviderProperties providerProperties
   ) {
      this.mediaAssets = mediaAssets;
      this.paintings = paintings;
      this.providerProperties = providerProperties;
   }

   public record ResolvedSource(WorkflowModality modality, String sourceText, Painting sourcePainting, MediaAsset sourceAsset) {
   }
}
