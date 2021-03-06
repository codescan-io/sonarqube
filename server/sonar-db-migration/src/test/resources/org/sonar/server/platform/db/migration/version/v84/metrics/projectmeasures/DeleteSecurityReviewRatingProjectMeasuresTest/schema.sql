CREATE TABLE "METRICS"(
    "ID" INTEGER NOT NULL AUTO_INCREMENT (1,1),
    "UUID" VARCHAR(40) NOT NULL,
    "NAME" VARCHAR(64) NOT NULL,
    "DESCRIPTION" VARCHAR(255),
    "DIRECTION" INTEGER DEFAULT 0 NOT NULL,
    "DOMAIN" VARCHAR(64),
    "SHORT_NAME" VARCHAR(64),
    "QUALITATIVE" BOOLEAN DEFAULT FALSE NOT NULL,
    "VAL_TYPE" VARCHAR(8),
    "USER_MANAGED" BOOLEAN DEFAULT FALSE,
    "ENABLED" BOOLEAN DEFAULT TRUE,
    "WORST_VALUE" DOUBLE,
    "BEST_VALUE" DOUBLE,
    "OPTIMIZED_BEST_VALUE" BOOLEAN,
    "HIDDEN" BOOLEAN,
    "DELETE_HISTORICAL_DATA" BOOLEAN,
    "DECIMAL_SCALE" INTEGER
);
ALTER TABLE "METRICS" ADD CONSTRAINT "PK_METRICS" PRIMARY KEY("ID");
CREATE UNIQUE INDEX "METRICS_UNIQUE_NAME" ON "METRICS"("NAME");

CREATE TABLE "PROJECT_MEASURES"(
    "VALUE" DOUBLE,
    "METRIC_UUID" VARCHAR(40) NOT NULL,
    "ANALYSIS_UUID" VARCHAR(50) NOT NULL,
    "COMPONENT_UUID" VARCHAR(50) NOT NULL,
    "TEXT_VALUE" VARCHAR(4000),
    "ALERT_STATUS" VARCHAR(5),
    "ALERT_TEXT" VARCHAR(4000),
    "DESCRIPTION" VARCHAR(4000),
    "PERSON_ID" INTEGER,
    "VARIATION_VALUE_1" DOUBLE,
    "VARIATION_VALUE_2" DOUBLE,
    "VARIATION_VALUE_3" DOUBLE,
    "VARIATION_VALUE_4" DOUBLE,
    "VARIATION_VALUE_5" DOUBLE,
    "MEASURE_DATA" BLOB,
    "UUID" VARCHAR(40) NOT NULL
);
ALTER TABLE "PROJECT_MEASURES" ADD CONSTRAINT "PK_PROJECT_MEASURES" PRIMARY KEY("UUID");
CREATE INDEX "MEASURES_COMPONENT_UUID" ON "PROJECT_MEASURES"("COMPONENT_UUID");
