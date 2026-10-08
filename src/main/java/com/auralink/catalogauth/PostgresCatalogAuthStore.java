package com.auralink.catalogauth;

import com.auralink.api.v1.error.ApiErrorCode;
import com.auralink.api.v1.error.ApiV1Exception;
import com.auralink.entity.User;
import com.auralink.exception.UserAlreadyExistsException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

public class PostgresCatalogAuthStore implements CatalogAuthStore {
   private static final String USER_SELECT = "SELECT id, username, password_hash, full_name, email, enabled,\n       account_non_expired, account_non_locked, credentials_non_expired,\n       role, created_at, updated_at\nFROM catalog_users\n";
   private final NamedParameterJdbcTemplate jdbc;

   PostgresCatalogAuthStore(NamedParameterJdbcTemplate jdbc) {
      this.jdbc = jdbc;
   }

   @Override
   public Optional<User> findByUsername(String username) {
      try {
         return this.jdbc
            .query(
               "SELECT id, username, password_hash, full_name, email, enabled,\n       account_non_expired, account_non_locked, credentials_non_expired,\n       role, created_at, updated_at\nFROM catalog_users\n WHERE username = :username",
               new MapSqlParameterSource("username", username),
               userRow()
            )
            .stream()
            .findFirst();
      } catch (DataAccessException exception) {
         throw unavailable(exception);
      }
   }

   @Override
   public boolean existsByUsername(String username) {
      return this.exists("username", username);
   }

   @Override
   public boolean existsByEmail(String email) {
      return this.exists("email", email);
   }

   @Transactional(transactionManager = "catalogAuthTransactionManager")
   @Override
   public User register(String username, String encodedPassword, String fullName, String email) {
      if (this.existsByUsername(username)) {
         throw new UserAlreadyExistsException("用户名已存在");
      }

      if (this.existsByEmail(email)) {
         throw new UserAlreadyExistsException("邮箱已被注册");
      }

      LocalDateTime now = LocalDateTime.now();

      try {
         return (User)this.jdbc
            .queryForObject(
               "INSERT INTO catalog_users(\n    username, password_hash, full_name, email, enabled,\n    account_non_expired, account_non_locked, credentials_non_expired,\n    role, created_at, updated_at)\nVALUES (:username, :password, :fullName, :email, TRUE, TRUE, TRUE, TRUE,\n        'ROLE_USER', :createdAt, :updatedAt)\nRETURNING id, username, password_hash, full_name, email, enabled,\n          account_non_expired, account_non_locked, credentials_non_expired,\n          role, created_at, updated_at\n",
               new MapSqlParameterSource()
                  .addValue("username", username)
                  .addValue("password", encodedPassword)
                  .addValue("fullName", fullName)
                  .addValue("email", email)
                  .addValue("createdAt", now)
                  .addValue("updatedAt", now),
               userRow()
            );
      } catch (DuplicateKeyException exception) {
         throw new UserAlreadyExistsException("用户名或邮箱已被注册");
      } catch (DataAccessException exception) {
         throw unavailable(exception);
      }
   }

   @Override
   public Set<String> favoritedPaintingIds(long userId, Collection<String> paintingIds) {
      if (paintingIds != null && !paintingIds.isEmpty()) {
         try {
            return new LinkedHashSet<>(
               this.jdbc
                  .query(
                     "SELECT painting_id::text\nFROM catalog_painting_favorites\nWHERE user_id = :userId AND painting_id::text IN (:paintingIds)\n",
                     new MapSqlParameterSource().addValue("userId", userId).addValue("paintingIds", paintingIds),
                     (rs, row) -> rs.getString(1)
                  )
            );
         } catch (DataAccessException exception) {
            throw unavailable(exception);
         }
      } else {
         return Set.of();
      }
   }

   @Override
   public boolean isFavorited(long userId, String paintingId) {
      String canonicalId = canonicalPaintingId(paintingId);

      try {
         Long count = (Long)this.jdbc
            .queryForObject(
               "SELECT count(*) FROM catalog_painting_favorites\nWHERE user_id = :userId AND painting_id = CAST(:paintingId AS uuid)\n",
               new MapSqlParameterSource().addValue("userId", userId).addValue("paintingId", canonicalId),
               Long.class
            );
         return count != null && count > 0L;
      } catch (DataAccessException exception) {
         throw unavailable(exception);
      }
   }

   @Transactional(transactionManager = "catalogAuthTransactionManager")
   @Override
   public void favorite(long userId, String paintingId) {
      String canonicalId = this.requireActivePainting(paintingId);

      try {
         this.jdbc
            .update(
               "INSERT INTO catalog_painting_favorites(public_id, user_id, painting_id, created_at)\nVALUES (CAST(:favoriteId AS uuid), :userId, CAST(:paintingId AS uuid), :createdAt)\nON CONFLICT (user_id, painting_id) DO NOTHING\n",
               new MapSqlParameterSource()
                  .addValue("favoriteId", UUID.randomUUID().toString())
                  .addValue("userId", userId)
                  .addValue("paintingId", canonicalId)
                  .addValue("createdAt", LocalDateTime.now())
            );
      } catch (DataAccessException exception) {
         throw unavailable(exception);
      }
   }

   @Transactional(transactionManager = "catalogAuthTransactionManager")
   @Override
   public void unfavorite(long userId, String paintingId) {
      String canonicalId = this.requireActivePainting(paintingId);

      try {
         this.jdbc
            .update(
               "DELETE FROM catalog_painting_favorites\nWHERE user_id = :userId AND painting_id = CAST(:paintingId AS uuid)\n",
               new MapSqlParameterSource().addValue("userId", userId).addValue("paintingId", canonicalId)
            );
      } catch (DataAccessException exception) {
         throw unavailable(exception);
      }
   }

   @Override
   public CatalogAuthStore.FavoritePage favoritePaintingIds(long userId, int page, int size) {
      try {
         MapSqlParameterSource parameters = new MapSqlParameterSource("userId", userId);
         Long total = (Long)this.jdbc
            .queryForObject(
               "SELECT count(*)\nFROM catalog_painting_favorites favorite\nJOIN catalog_paintings painting ON painting.public_id = favorite.painting_id\nWHERE favorite.user_id = :userId AND painting.catalog_status = 'ACTIVE'\n",
               parameters,
               Long.class
            );
         List<String> paintingIds = this.jdbc
            .query(
               "SELECT favorite.painting_id::text\nFROM catalog_painting_favorites favorite\nJOIN catalog_paintings painting ON painting.public_id = favorite.painting_id\nWHERE favorite.user_id = :userId AND painting.catalog_status = 'ACTIVE'\nORDER BY favorite.created_at DESC, favorite.public_id ASC\nLIMIT :limit OFFSET :offset\n",
               parameters.addValue("limit", size).addValue("offset", Math.multiplyExact(page, size)),
               (rs, row) -> rs.getString(1)
            );
         return new CatalogAuthStore.FavoritePage(paintingIds, page, size, total == null ? 0L : total);
      } catch (DataAccessException exception) {
         throw unavailable(exception);
      }
   }

   private boolean exists(String column, String value) {
      try {
         Long count = (Long)this.jdbc
            .queryForObject("SELECT count(*) FROM catalog_users WHERE " + column + " = :value", new MapSqlParameterSource("value", value), Long.class);
         return count != null && count > 0L;
      } catch (DataAccessException exception) {
         throw unavailable(exception);
      }
   }

   private String requireActivePainting(String paintingId) {
      String canonicalId = canonicalPaintingId(paintingId);

      try {
         Long count = (Long)this.jdbc
            .queryForObject(
               "SELECT count(*) FROM catalog_paintings\nWHERE public_id = CAST(:paintingId AS uuid) AND catalog_status = 'ACTIVE'\n",
               new MapSqlParameterSource("paintingId", canonicalId),
               Long.class
            );
         if (count != null && count != 0L) {
            return canonicalId;
         } else {
            throw paintingNotFound();
         }
      } catch (ApiV1Exception exception) {
         throw exception;
      } catch (DataAccessException exception) {
         throw unavailable(exception);
      }
   }

   private static String canonicalPaintingId(String value) {
      try {
         String canonical = UUID.fromString(value).toString();
         if (!canonical.equals(value.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("non-canonical UUID");
         } else {
            return canonical;
         }
      } catch (RuntimeException exception) {
         throw new ApiV1Exception(HttpStatus.BAD_REQUEST, ApiErrorCode.INVALID_PAINTING_ID, "画作标识格式无效");
      }
   }

   private static RowMapper<User> userRow() {
      return PostgresCatalogAuthStore::mapUser;
   }

   private static User mapUser(ResultSet rs, int rowNumber) throws SQLException {
      return User.builder()
         .id(rs.getLong("id"))
         .username(rs.getString("username"))
         .password(rs.getString("password_hash"))
         .fullName(rs.getString("full_name"))
         .email(rs.getString("email"))
         .enabled(rs.getBoolean("enabled"))
         .accountNonExpired(rs.getBoolean("account_non_expired"))
         .accountNonLocked(rs.getBoolean("account_non_locked"))
         .credentialsNonExpired(rs.getBoolean("credentials_non_expired"))
         .role(rs.getString("role"))
         .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
         .updatedAt(rs.getTimestamp("updated_at").toLocalDateTime())
         .build();
   }

   private static ApiV1Exception paintingNotFound() {
      return new ApiV1Exception(HttpStatus.NOT_FOUND, ApiErrorCode.PAINTING_NOT_FOUND, "画作不存在或当前不可用");
   }

   private static ApiV1Exception unavailable(DataAccessException exception) {
      return new ApiV1Exception(HttpStatus.SERVICE_UNAVAILABLE, ApiErrorCode.CATALOG_READ_UNAVAILABLE, "PostgreSQL 目录认证数据库暂不可用");
   }
}
