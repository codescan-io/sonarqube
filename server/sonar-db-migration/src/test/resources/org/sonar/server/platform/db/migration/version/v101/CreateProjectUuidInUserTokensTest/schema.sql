CREATE TABLE "USER_TOKENS"(
    "UUID" CHARACTER VARYING(40) NOT NULL,
    "USER_UUID" CHARACTER VARYING(255) NOT NULL,
    "NAME" CHARACTER VARYING(100) NOT NULL,
    "TOKEN_HASH" CHARACTER VARYING(255) NOT NULL,
    "LAST_CONNECTION_DATE" BIGINT,
    "CREATED_AT" BIGINT NOT NULL,
    "PROJECT_KEY" CHARACTER VARYING(255),
    "TYPE" CHARACTER VARYING(100) NOT NULL,
    "EXPIRATION_DATE" BIGINT
);
ALTER TABLE "USER_TOKENS" ADD CONSTRAINT "PK_USER_TOKENS" PRIMARY KEY("UUID");
CREATE UNIQUE INDEX "USER_TOKENS_USER_UUID_NAME" ON "USER_TOKENS"("USER_UUID" NULLS FIRST, "NAME" NULLS FIRST);
CREATE UNIQUE INDEX "USER_TOKENS_TOKEN_HASH" ON "USER_TOKENS"("TOKEN_HASH" NULLS FIRST);
