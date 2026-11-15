package com.fluveny.fluveny_backend.content.web;

import com.fluveny.fluveny_backend.content.activity.Activity;
import com.fluveny.fluveny_backend.content.activity.ContentText;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.util.List;
import java.util.Map;

/**
 * O formato do pacote de publicação no OpenAPI. A rota lê o corpo como JSON cru e o valida pelo
 * JSON Schema de {@code content/schema/}, que é a fonte do formato; estes records só o descrevem.
 */
final class BundleDocs {

    private BundleDocs() {}

    @Schema(name = "UnitBundleContent")
    record UnitBundle(
            @Schema(requiredMode = RequiredMode.REQUIRED) UnitContent unit,
            @Schema(requiredMode = RequiredMode.REQUIRED) List<StageContent> stages,
            @Schema(requiredMode = RequiredMode.REQUIRED) Map<String, MediaContent> media) {}

    @Schema(name = "UnitContent")
    record UnitContent(
            @Schema(requiredMode = RequiredMode.REQUIRED) String unitKey,
            @Schema(requiredMode = RequiredMode.REQUIRED) Integer order,
            @Schema(requiredMode = RequiredMode.REQUIRED) Integer schemaVersion,
            @Schema(requiredMode = RequiredMode.REQUIRED) ContentText title,
            @Schema(requiredMode = RequiredMode.REQUIRED) List<String> stages,
            @Schema(requiredMode = RequiredMode.REQUIRED)
                    List<UsefulExpressionContent> usefulExpressions) {}

    @Schema(name = "UsefulExpressionContent")
    record UsefulExpressionContent(
            @Schema(requiredMode = RequiredMode.REQUIRED) String expressionKey,
            @Schema(requiredMode = RequiredMode.REQUIRED) ContentText term,
            @Schema(requiredMode = RequiredMode.REQUIRED) ContentText meaning,
            @Schema(requiredMode = RequiredMode.REQUIRED) List<ContentText> examples) {}

    @Schema(name = "StageContent")
    record StageContent(
            @Schema(requiredMode = RequiredMode.REQUIRED) String stageKey,
            @Schema(requiredMode = RequiredMode.REQUIRED) ContentText title,
            @Schema(requiredMode = RequiredMode.REQUIRED) List<Activity> activities) {}

    @Schema(name = "MediaContent")
    record MediaContent(
            @Schema(
                            requiredMode = RequiredMode.REQUIRED,
                            allowableValues = {"image", "video", "audio"})
                    String kind,
            @Schema(
                            requiredMode = RequiredMode.REQUIRED,
                            allowableValues = {"bundled", "remote"})
                    String source,
            String url) {}
}
