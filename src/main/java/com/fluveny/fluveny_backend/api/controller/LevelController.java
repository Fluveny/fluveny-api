package com.fluveny.fluveny_backend.api.controller;

import com.fluveny.fluveny_backend.api.ApiResponseFormat;
import com.fluveny.fluveny_backend.business.service.LevelService;
import com.fluveny.fluveny_backend.infraestructure.entity.LevelEntity;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/v1/levels")
public class LevelController {

    private LevelService levelService;

    @Autowired
    public void setLevelService(LevelService levelService) {
        this.levelService = levelService;
    }

    @GetMapping
    public ResponseEntity<ApiResponseFormat<List<LevelEntity>>> getAllLevels() {
        List<LevelEntity> levelsResponse = levelService.findAll();
        if(levelsResponse.isEmpty()) {
            return ResponseEntity.status(HttpStatus.OK).body(new ApiResponseFormat<List<LevelEntity>>("No levels were retrieved", null));
        }
        return ResponseEntity.status(HttpStatus.OK).body(new ApiResponseFormat<List<LevelEntity>>("Levels were successfully retrieved", levelsResponse));
    }

}
