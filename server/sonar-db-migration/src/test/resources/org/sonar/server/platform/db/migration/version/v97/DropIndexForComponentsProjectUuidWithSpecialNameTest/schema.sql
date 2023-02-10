CREATE TABLE "COMPONENTS"(
                             "UUID" CHARACTER VARYING(50) NOT NULL,
                             "KEE" CHARACTER VARYING(1000),
                             "DEPRECATED_KEE" CHARACTER VARYING(400),
                             "NAME" CHARACTER VARYING(2000),
                             "LONG_NAME" CHARACTER VARYING(2000),
                             "DESCRIPTION" CHARACTER VARYING(2000),
                             "ENABLED" BOOLEAN DEFAULT TRUE NOT NULL,
                             "SCOPE" CHARACTER VARYING(3),
                             "QUALIFIER" CHARACTER VARYING(10),
                             "PRIVATE" BOOLEAN NOT NULL,
                             "ROOT_UUID" CHARACTER VARYING(50) NOT NULL,
                             "LANGUAGE" CHARACTER VARYING(20),
                             "COPY_COMPONENT_UUID" CHARACTER VARYING(50),
                             "PATH" CHARACTER VARYING(2000),
                             "UUID_PATH" CHARACTER VARYING(1500) NOT NULL,
                             "PROJECT_UUID" CHARACTER VARYING(50) NOT NULL,
                             "MODULE_UUID" CHARACTER VARYING(50),
                             "MODULE_UUID_PATH" CHARACTER VARYING(1500),
                             "MAIN_BRANCH_PROJECT_UUID" CHARACTER VARYING(50),
                             "B_CHANGED" BOOLEAN,
                             "B_NAME" CHARACTER VARYING(500),
                             "B_LONG_NAME" CHARACTER VARYING(500),
                             "B_DESCRIPTION" CHARACTER VARYING(2000),
                             "B_ENABLED" BOOLEAN,
                             "B_QUALIFIER" CHARACTER VARYING(10),
                             "B_LANGUAGE" CHARACTER VARYING(20),
                             "B_COPY_COMPONENT_UUID" CHARACTER VARYING(50),
                             "B_PATH" CHARACTER VARYING(2000),
                             "B_UUID_PATH" CHARACTER VARYING(1500),
                             "B_MODULE_UUID" CHARACTER VARYING(50),
                             "B_MODULE_UUID_PATH" CHARACTER VARYING(1500),
                             "CREATED_AT" TIMESTAMP
);
CREATE UNIQUE INDEX "PROJECTS_KEE" ON "COMPONENTS"("KEE" NULLS FIRST);
CREATE INDEX "PROJECTS_MODULE_UUID" ON "COMPONENTS"("MODULE_UUID" NULLS FIRST);
CREATE INDEX "IDX_1234_PROJECTS_PROJECT_UUID" ON "COMPONENTS"("PROJECT_UUID" NULLS FIRST);
CREATE INDEX "PROJECTS_QUALIFIER" ON "COMPONENTS"("QUALIFIER" NULLS FIRST);
CREATE INDEX "PROJECTS_ROOT_UUID" ON "COMPONENTS"("ROOT_UUID" NULLS FIRST);
CREATE INDEX "PROJECTS_UUID" ON "COMPONENTS"("UUID" NULLS FIRST);
CREATE INDEX "IDX_MAIN_BRANCH_PRJ_UUID" ON "COMPONENTS"("MAIN_BRANCH_PROJECT_UUID" NULLS FIRST);
