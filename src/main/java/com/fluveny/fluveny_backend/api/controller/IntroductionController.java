package com.fluveny.fluveny_backend.api.controller;

import com.fluveny.fluveny_backend.api.mapper.IntroductionMapper;
import com.fluveny.fluveny_backend.business.service.IntroductionService;
import com.fluveny.fluveny_backend.api.ApiResponseFormat;
import com.fluveny.fluveny_backend.api.dto.IntroductionDTO;
import com.fluveny.fluveny_backend.infraestructure.entity.IntroductionEntity;
import lombok.RequiredArgsConstructor;
import org.apache.coyote.Response;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/{modulo_id}/{id}")
@RequiredArgsConstructor
public class IntroductionController {

    private final IntroductionService introductionService;

    @GetMapping
    public ResponseEntity<ApiResponseFormat<List<IntroductionEntity>>> getAllIntroduction(){
        List <IntroductionEntity> introductions = introductionService.getAllIntroduction();
        return ResponseEntity.status(HttpStatus.OK)
                .body(new ApiResponseFormat<>("Introductions were found", introductions));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponseFormat<IntroductionEntity>> getIntroductionById(@PathVariable String id){
        IntroductionEntity introduction = introductionService.getIntroductionById(id);
        return ResponseEntity.status(HttpStatus.OK)
                .body(new ApiResponseFormat<>("Introduction was found", introduction));
    }

    @PostMapping
    public ResponseEntity<ApiResponseFormat<IntroductionEntity>> creatingIntroduction(@RequestBody IntroductionEntity introductionEntity){
        IntroductionEntity introduction = introductionService.creatingIntroduction(introductionEntity);
        return ResponseEntity.status(HttpStatus.OK)
                .body(new ApiResponseFormat<>("Introduction was created", introduction));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponseFormat<IntroductionEntity>> deleteIntroduction(@PathVariable String id){
        introductionService.deleteIntroductionById(id);
        return ResponseEntity.status(HttpStatus.OK)
                .body(new ApiResponseFormat<>("Introduction was deleted", null));
    }
}
