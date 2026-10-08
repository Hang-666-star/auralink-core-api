package com.auralink.security.access;

import com.auralink.api.v1.error.ApiErrorCode;
import com.auralink.api.v1.error.ApiV1Exception;
import com.auralink.entity.MediaAsset;
import com.auralink.entity.User;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class MediaAssetAccessPolicy {
   public void requireReadable(MediaAsset asset, User currentUser) {
      if (!this.canRead(asset, currentUser)) {
         throw assetNotFound();
      }
   }

   public boolean canRead(MediaAsset asset, User currentUser) {
      if (asset == null || !"ACTIVE".equals(asset.getStatus())) {
         return false;
      } else if ("PUBLIC".equals(asset.getVisibility())) {
         return true;
      } else if ("PRIVATE".equals(asset.getVisibility()) && currentUser != null) {
         User owner = asset.getOwnerUser();
         return owner != null && owner.getId() != null && Objects.equals(owner.getId(), currentUser.getId());
      } else {
         return false;
      }
   }

   public static ApiV1Exception assetNotFound() {
      return new ApiV1Exception(HttpStatus.NOT_FOUND, ApiErrorCode.ASSET_NOT_FOUND, "资源不存在");
   }
}
