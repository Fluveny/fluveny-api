package com.fluveny.fluveny_backend.api.controller.interfaces;

import com.fluveny.fluveny_backend.api.ApiResponseFormat;
import com.fluveny.fluveny_backend.api.dto.exercise.ExerciseConstructorPhraseRequestDTO;
import com.fluveny.fluveny_backend.api.dto.exercise.ExerciseRequestDTO;
import com.fluveny.fluveny_backend.api.dto.exercise.ExerciseResponseDTO;
import com.fluveny.fluveny_backend.api.dto.exercise.ExerciseTranslateRequestDTO;
import com.fluveny.fluveny_backend.api.response.exercise.ExerciseCompletePhraseResponse;
import com.fluveny.fluveny_backend.api.response.exercise.ExerciseConstructionPhraseResponse;
import com.fluveny.fluveny_backend.api.response.exercise.ExerciseResponse;
import com.fluveny.fluveny_backend.api.response.exercise.ExerciseTranslatePhraseResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RequestMapping("/api/v1/modules/{id_module}/final-challenge")
public interface FinalChallengeExerciseControllerInterface {

    @Operation(summary = "Create a new Exercise in Final Challenge",
            description = "This endpoint is used to create a new exercise")
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "Exercise created successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(
                                    oneOf = {ExerciseConstructionPhraseResponse.class, ExerciseTranslatePhraseResponse.class, ExerciseCompletePhraseResponse.class}
                            ),
                            examples = {
                                    @ExampleObject(
                                            name = "Construction Phrase Response",
                                            value = """
                                                    {
                                                      "message": "string",
                                                      "data": {
                                                        "style": "CONSTRUCTION_PHRASE",
                                                        "originalSentence": "string",
                                                        "translation": "string",
                                                        "words": ["string"]
                                                      }
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "Translate Phrase Response",
                                            value = """
                                                    {
                                                      "message": "string",
                                                      "data": {
                                                        "style": "TRANSLATE",
                                                        "header": "string",
                                                        "phrase": "string",
                                                        "template": "string",
                                                        "justification": "string"
                                                      }
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "Complete Phrase Response",
                                            value = """
                                                    {
                                                      "message": "string",
                                                      "data": {
                                                        "style": "COMPLETE",
                                                        "header": "string",
                                                        "phrase": [
                                                                {
                                                                    "type": "TEXT",
                                                                    "content": "string"
                                                                },
                                                                {
                                                                    "type": "GAP",
                                                                    "words": ["string"],
                                                                    "justification": "string"
                                                                }
                                                        ]
                                                      }
                                                    }
                                                    """
                                    )
                            }
                    )
            ),
            @ApiResponse(responseCode = "400", description = "Bad request for application",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponseFormat.class)
                    )
            ),
            @ApiResponse(responseCode = "500", description = "Server error",
                    content  = @Content(
                            mediaType = "application/json",
                            schema =  @Schema(implementation = ApiResponseFormat.class)
                    )
            )
    })
    @PostMapping
    public ResponseEntity<ApiResponseFormat<ExerciseResponseDTO>> createExercise(
            @Valid
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Exercise request",
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(
                                    oneOf = {ExerciseTranslateRequestDTO.class, ExerciseConstructorPhraseRequestDTO.class, ExerciseCompletePhraseResponse.class}
                            ),
                            examples = {
                                    @ExampleObject(
                                            name = "Construction Phrase Request",
                                            value = """
                                                    {
                                                      "style": "CONSTRUCTION_PHRASE",
                                                      "originalSentence": "string",
                                                      "translation": "string",
                                                      "distractors": ["string"]
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "Translate Phrase Request",
                                            value = """
                                                    {
                                                      "style": "TRANSLATE",
                                                      "header": "string",
                                                      "phrase": "string",
                                                      "template": "string",
                                                      "justification": "string"
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "Complete Phrase Response",
                                            value = """
                                                    {
                                                        "style": "COMPLETE",
                                                        "header": "string",
                                                        "phrase": [
                                                                {
                                                                    "type": "TEXT",
                                                                    "content": "string"
                                                                },
                                                                {
                                                                    "type": "GAP",
                                                                    "words": ["string"],
                                                                    "justification": "string"
                                                                }
                                                        ]
                                                    }
                                                    """
                                    )
                            }
                    )
            )
            ExerciseRequestDTO exerciseRequestDTO, @PathVariable String id_module);


    @Operation(summary = "Return a exercise by id",
            description = "This endpoint is used to return a exercise by id")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Exercise found successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ExerciseResponse.class)
                    )
            ),
            @ApiResponse(responseCode = "400", description = "Bad request for application",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponseFormat.class)
                    )
            ),
            @ApiResponse(responseCode = "404", description = "Exercise not found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponseFormat.class)
                    )
            ),
            @ApiResponse(responseCode = "500", description = "Server error",
                    content  = @Content(
                            mediaType = "application/json",
                            schema =  @Schema(implementation = ApiResponseFormat.class)
                    )
            )
    })
    @GetMapping("/{id_exercise}")
    public ResponseEntity<ApiResponseFormat<ExerciseResponseDTO>> getExerciseByID(@PathVariable String id_module, @PathVariable String id_exercise);

    @Operation(summary = "Update a  Exercise",
            description = "This endpoint is used to update a exercise")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Exercise updated successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(
                                    oneOf = {ExerciseConstructionPhraseResponse.class, ExerciseTranslatePhraseResponse.class, ExerciseCompletePhraseResponse.class}
                            ),
                            examples = {
                                    @ExampleObject(
                                            name = "Construction Phrase Response",
                                            value = """
                                                    {
                                                      "message": "string",
                                                      "data": {
                                                        "style": "CONSTRUCTION_PHRASE",
                                                        "originalSentence": "string",
                                                        "translation": "string",
                                                        "words": ["string"]
                                                      }
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "Translate Phrase Response",
                                            value = """
                                                    {
                                                      "message": "string",
                                                      "data": {
                                                        "style": "TRANSLATE",
                                                        "header": "string",
                                                        "phrase": "string",
                                                        "template": "string",
                                                        "justification": "string"
                                                      }
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "Complete Phrase Response",
                                            value = """
                                                    {
                                                      "message": "string",
                                                      "data": {
                                                        "style": "COMPLETE",
                                                        "header": "string",
                                                        "phrase": [
                                                                {
                                                                    "type": "TEXT",
                                                                    "content": "string"
                                                                },
                                                                {
                                                                    "type": "GAP",
                                                                    "words": ["string"],
                                                                    "justification": "string"
                                                                }
                                                        ]
                                                      }
                                                    }
                                                    """
                                    )
                            }
                    )
            ),
            @ApiResponse(responseCode = "400", description = "Bad request for application",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponseFormat.class)
                    )
            ),
            @ApiResponse(responseCode = "500", description = "Server error",
                    content  = @Content(
                            mediaType = "application/json",
                            schema =  @Schema(implementation = ApiResponseFormat.class)
                    )
            )
    })
    @PutMapping("/{id_exercise}")
    public ResponseEntity<ApiResponseFormat<ExerciseResponseDTO>> updateExercise(
            @Valid
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Exercise request",
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(
                                    oneOf = {ExerciseTranslateRequestDTO.class, ExerciseConstructorPhraseRequestDTO.class, ExerciseCompletePhraseResponse.class}
                            ),
                            examples = {
                                    @ExampleObject(
                                            name = "Construction Phrase Request",
                                            value = """
                                                    {
                                                      "style": "CONSTRUCTION_PHRASE",
                                                      "originalSentence": "string",
                                                      "translation": "string",
                                                      "distractors": ["string"]
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "Translate Phrase Request",
                                            value = """
                                                    {
                                                      "style": "TRANSLATE",
                                                      "header": "string",
                                                      "phrase": "string",
                                                      "template": "string",
                                                      "justification": "string"
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "Complete Phrase Response",
                                            value = """
                                                    {
                                                        "style": "COMPLETE",
                                                        "header": "string",
                                                        "phrase": [
                                                                {
                                                                    "type": "TEXT",
                                                                    "content": "string"
                                                                },
                                                                {
                                                                    "type": "GAP",
                                                                    "words": ["string"],
                                                                    "justification": "string"
                                                                }
                                                        ]
                                                    }
                                                    """
                                    )
                            }
                    )
            )
            ExerciseRequestDTO exerciseRequestDTO,
            @PathVariable String id_exercise, @PathVariable String id_module);

}
