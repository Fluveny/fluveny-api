package com.fluveny.fluveny_backend.business.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.fluveny.fluveny_backend.api.dto.finalchallenge.FinalChallengeRequestDTO;
import com.fluveny.fluveny_backend.api.dto.module.LinkStudentToModuleRequestDTO;
import com.fluveny.fluveny_backend.api.dto.module.ModuleOverviewDTO;
import com.fluveny.fluveny_backend.api.dto.module.ModuleResponseStudentDTO;
import com.fluveny.fluveny_backend.api.dto.module.SearchModuleStudentDTO;
import com.fluveny.fluveny_backend.api.mapper.module.ModuleOverviewMapper;
import com.fluveny.fluveny_backend.api.mapper.module.ModuleSearchStudentMapper;
import com.fluveny.fluveny_backend.exception.BusinessException.BusinessException;
import com.fluveny.fluveny_backend.infraestructure.entity.TextBlockEntity;
import com.fluveny.fluveny_backend.infraestructure.entity.auth.UserEntity;
import com.fluveny.fluveny_backend.infraestructure.entity.exercise.ExerciseEntity;
import com.fluveny.fluveny_backend.infraestructure.entity.grammarrule.GrammarRuleEntity;
import com.fluveny.fluveny_backend.infraestructure.entity.grammarrule.GrammarRuleModuleEntity;
import com.fluveny.fluveny_backend.infraestructure.entity.module.ModuleEntity;
import com.fluveny.fluveny_backend.infraestructure.entity.module.ModuleStudent;
import com.fluveny.fluveny_backend.infraestructure.enums.ContentType;
import com.fluveny.fluveny_backend.infraestructure.enums.ModuleStatus;
import com.fluveny.fluveny_backend.infraestructure.enums.ParentOfTheContent;
import com.fluveny.fluveny_backend.infraestructure.repository.ModuleRepository;
import com.fluveny.fluveny_backend.infraestructure.repository.ModuleStudentRepository;
import com.fluveny.fluveny_backend.infraestructure.repository.TextBlockRepository;
import com.fluveny.fluveny_backend.infraestructure.repository.UserRepository;
import org.springframework.data.domain.*;

import java.time.LocalDateTime;

@Service
public class ModuleService implements IntroductionService {

    @Autowired
    private ModuleRepository moduleRepository;

    @Autowired
    private GrammarRuleModuleService grammarRuleModuleService;

    @Autowired
    private TextBlockRepository textBlockRepository;

    @Autowired
    private GrammarRuleService grammarRuleService;

    @Autowired
    private ModuleStudentRepository moduleStudentRepository;

    @Autowired
    private ModuleOverviewMapper moduleOverviewMapper;

    @Autowired
    private ContentManagerService contentManagerService;

    @Autowired
    private ModuleSearchStudentMapper moduleSearchStudentMapper;

    public List<ModuleEntity> getAllDraftModule (String userName, Integer quantity, Boolean sortedByDate) {

        List<ModuleEntity> returnedModulesByAuthor = moduleRepository.findByAuthorUsername(userName);

        if (returnedModulesByAuthor.isEmpty()){
            throw new BusinessException("Doesn't exits modules for this username", HttpStatus.OK);
        }

        List<ModuleEntity> modulesByAuthor = new ArrayList<>();

        for (ModuleEntity moduleEntity : returnedModulesByAuthor){
            if (moduleEntity.getStatus() == ModuleStatus.DRAFT){
                modulesByAuthor.add(moduleEntity);
            }
        }

        if (sortedByDate != null && sortedByDate){
            modulesByAuthor.sort(Comparator.comparing(ModuleEntity::getLastModified).reversed());
        }
        if (quantity != null){
            modulesByAuthor = modulesByAuthor.subList(0, Math.min(modulesByAuthor.size(), quantity));
        }
        return modulesByAuthor;
    }

    @Autowired
    private UserRepository userRepository;

    public Page<ModuleResponseStudentDTO> getAllModuleByStudent (UserEntity userEntity, Integer pageSize, Integer pageNumber) {

        Page<ModuleEntity> modulesPage = moduleRepository.findAll(
                PageRequest.of(pageNumber, pageSize, Sort.by(Sort.Direction.ASC, "level.title"))
        );

        List<ModuleStudent> moduleStudents = moduleStudentRepository.findByStudentId(userEntity.getId());

        Map<String, ModuleStudent> moduleStudentMap = moduleStudents.stream()
                .collect(Collectors.toMap(ModuleStudent::getModuleId, Function.identity()));

        return modulesPage.map(module ->
        {
            ModuleResponseStudentDTO dto = moduleSearchStudentMapper.toDTO(module);

            ModuleStudent moduleStudent = moduleStudentMap.get(module.getId());
            if (moduleStudent != null) {
                dto.setProgress(moduleStudent.getProgress());
                dto.setIsFavorite(moduleStudent.getIsFavorite());
            }
            return dto;
        }
        );
    }

    public ModuleEntity publishModule(String id, String username) {
        Optional<ModuleEntity> moduleFind = moduleRepository.findById(id);

        if(moduleFind.isEmpty()){
            throw new BusinessException("A module with that id was not found", HttpStatus.NOT_FOUND);
        }

        ModuleEntity module = moduleFind.get();

        if (module.getAuthorUsername() == null || !module.getAuthorUsername().equals(username)) {
            throw new BusinessException("You don't have permission to publish this module", HttpStatus.FORBIDDEN);
        }

        if (module.getIntroduction() == null) {
            throw new BusinessException("O módulo precisa ter uma introdução para ser publicado.", HttpStatus.BAD_REQUEST);
        }

        if (module.getGrammarRules() == null || module.getGrammarRules().isEmpty()) {
            throw new BusinessException("O módulo precisa ter pelo menos uma regra de gramática para ser publicado.", HttpStatus.BAD_REQUEST);
        }

        if (module.getFinalChallenge() == null || module.getFinalChallenge().isEmpty()) {
            throw new BusinessException("O módulo precisa ter pelo menos um exercício no desafio final para ser publicado.", HttpStatus.BAD_REQUEST);
        }

        module.setStatus(ModuleStatus.PUBLISHED);
        module.setLastModified(LocalDateTime.now());
        return moduleRepository.save(module);
    }

    public Page<ModuleResponseStudentDTO> searchModulesByAuthorAndStatus(String authorUsername, ModuleStatus status, SearchModuleStudentDTO searchModuleStudentDTO, Integer pageSize, Integer pageNumber) {
        Pageable pageable = PageRequest.of(pageNumber, pageSize, Sort.by(Sort.Direction.DESC, "LastModified"));

        Page<ModuleEntity> moduleEntities = moduleRepository.searchByAuthorAndStatusWithFilters(
                authorUsername,
                status,
                searchModuleStudentDTO.getModuleName(),
                searchModuleStudentDTO.getLevelId(),
                searchModuleStudentDTO.getGrammarRulesId(),
                pageable
        );

        return moduleEntities.map(moduleSearchStudentMapper::toDTO);
    }

    /**
     * Checks if a GrammarRuleModule exists within a Module by their IDs.
     * <p>
     * Throws BusinessException with NOT_FOUND status if the module or rule module is not found.
     *
     * @param idModule the ID of the module
     * @param idGrammarRuleModule the ID of the grammar rule module
     * @throws BusinessException if module or grammar rule module does not exist
     */
    public void grammarRuleModuleExistsInModule(String idModule, String idGrammarRuleModule){

        Optional<ModuleEntity> optionalModule = moduleRepository.findById(idModule);

        if (optionalModule.isEmpty()) {
            throw new BusinessException("A module with that id was not found", HttpStatus.NOT_FOUND);
        }

        List<GrammarRuleModuleEntity> grammarRuleModules = optionalModule.get().getGrammarRuleModules();
        if (grammarRuleModules.isEmpty()) {
            throw new BusinessException("This module has no grammar rule modules", HttpStatus.NOT_FOUND);
        }

        boolean found = false;
        for (GrammarRuleModuleEntity grm : grammarRuleModules) {
            if (grm != null && idGrammarRuleModule.equals(grm.getId())) {
                found = true;
                break;
            }
        }

        if (!found) {
            throw new BusinessException("This module has no grammar rule module with this id", HttpStatus.NOT_FOUND);
        }
    }

    public List<GrammarRuleModuleEntity> getAllGrammarRulesModulesByIdModule (String id) {

        Optional<ModuleEntity> moduleFind = moduleRepository.findById(id);

        if(moduleFind.isEmpty()){
            throw new BusinessException("A module with that id was not found", HttpStatus.NOT_FOUND);
        }

        return moduleFind.get().getGrammarRuleModules();
    }

    public ModuleEntity createModule(ModuleEntity moduleEntity) {

        Optional<ModuleEntity> titleConflict = moduleRepository.findByTitle(moduleEntity.getTitle());

        if (titleConflict.isPresent()) {
            throw new BusinessException("Another module with this title already exists", HttpStatus.BAD_REQUEST);
        }

        validateGrammarRules(moduleEntity);

        ModuleEntity savedModuleEntity = moduleRepository.save(moduleEntity);
        savedModuleEntity.setGrammarRuleModules(new ArrayList<>());

        List<GrammarRuleEntity> grammarRules = moduleEntity.getGrammarRules();

        for(GrammarRuleEntity grammarRule : grammarRules) {
            savedModuleEntity.getGrammarRuleModules().add(setGrammarRuleModule(savedModuleEntity, grammarRule));
        }

        return moduleRepository.save(savedModuleEntity);
    }

    public ModuleEntity deleteModule (String id) {

        Optional<ModuleEntity> module = moduleRepository.findById(id);

        if (module.isEmpty()) {
            throw new BusinessException("This module doesn't exist", HttpStatus.BAD_REQUEST);
        }

        if(module.get().getIntroduction() != null){
            this.deleteIntroductionById(module.get().getId());
        }

        if(module.get().getGrammarRuleModules() != null) {
            for (GrammarRuleModuleEntity grammarRuleModuleEntity : module.get().getGrammarRuleModules()) {
                grammarRuleModuleService.deleteGrammarRuleModule(grammarRuleModuleEntity.getId());
            }
        }

        moduleRepository.deleteById(module.get().getId());

        return module.get();
    }

    private GrammarRuleModuleEntity setGrammarRuleModule(ModuleEntity moduleEntity, GrammarRuleEntity grammarRuleEntity) {
        GrammarRuleModuleEntity grammarRuleModuleEntity = new GrammarRuleModuleEntity();
        grammarRuleModuleEntity.setModuleId(moduleEntity.getId());
        grammarRuleModuleEntity.setGrammarRule(grammarRuleService.getGrammarRuleById(grammarRuleEntity.getId()));
        return grammarRuleModuleService.createGrammarRuleModule(grammarRuleModuleEntity);
    }

    public ModuleEntity updateModule(ModuleEntity moduleEntity, String id) {

        moduleEntity.setId(id);
        Optional<ModuleEntity> existing = moduleRepository.findById(id);

        if (existing.isEmpty()) {
            throw new BusinessException("No module with this ID was found.", HttpStatus.NOT_FOUND);
        }

        moduleEntity.setIntroduction(existing.get().getIntroduction());
        this.updateLastModified(moduleEntity.getId());

        Optional<ModuleEntity> titleConflict = moduleRepository.findByTitle(moduleEntity.getTitle());
        if (titleConflict.isPresent() && !titleConflict.get().getId().equals(id)) {
            throw new BusinessException("Another module with this title already exists", HttpStatus.BAD_REQUEST);
        }

        validateGrammarRules(moduleEntity);

        List<GrammarRuleModuleEntity> updatedGrammarRuleModules = new ArrayList<>(existing.get().getGrammarRuleModules());
        moduleEntity.setGrammarRuleModules(updatedGrammarRuleModules);

        syncGrammarRuleModules(moduleEntity, existing.get());
        reorderGrammarRuleModules(moduleEntity);

        return moduleRepository.save(moduleEntity);

    }

    /**
     * Synchronizes the associations between grammar rules and the module.
     * <p>
     * Adds new associations from the module to rules not yet linked,
     * removes associations that were removed in the new version,
     * and updates the internal lists accordingly.
     *
     * @param newModule the module with the new list of grammar rules
     * @param existingModule the current module to be updated
     */
    public void syncGrammarRuleModules(ModuleEntity newModule, ModuleEntity existingModule) {

        List<GrammarRuleModuleEntity> toAdd = new ArrayList<>();
        List<GrammarRuleModuleEntity> toRemove = new ArrayList<>();
        List<GrammarRuleEntity> grammarRulesToRemove = new ArrayList<>();

        for (GrammarRuleEntity rule : newModule.getGrammarRules()) {
            if (!existingModule.getGrammarRules().contains(rule)) {
                toAdd.add(setGrammarRuleModule(newModule, rule));
            } else {
                GrammarRuleModuleEntity gmr = grammarRuleModuleService.getGrammarRuleModuleByGrammarRuleId(existingModule.getId(), rule.getId());
                toRemove.add(gmr);
                grammarRulesToRemove.add(rule);
            }
        }

        existingModule.getGrammarRuleModules().removeAll(toRemove);
        existingModule.getGrammarRules().removeAll(grammarRulesToRemove);
        newModule.getGrammarRuleModules().addAll(toAdd);

        List<GrammarRuleModuleEntity> grammarRuleModuleEntities = existingModule.getGrammarRuleModules();

        if (!grammarRuleModuleEntities.isEmpty()) {
            List<GrammarRuleModuleEntity> GrammarRuleModulesToRemove = new ArrayList<>(grammarRuleModuleEntities);
            for (GrammarRuleModuleEntity grammarRuleModuleEntity : GrammarRuleModulesToRemove) {
                newModule.getGrammarRuleModules().remove(grammarRuleModuleEntity);
                grammarRuleModuleService.deleteGrammarRuleModule(grammarRuleModuleEntity.getId());
            }
        }
    }

    /**
     * Reorders the list of associations between grammar rules and the module
     * to reflect the current order of grammar rules in the module.
     *
     * @param moduleEntity the module whose associations will be reordered
     */
    public void reorderGrammarRuleModules(ModuleEntity moduleEntity) {

        Map<String, Integer> newPositions = new HashMap<>();

        for (int i = 0; i < moduleEntity.getGrammarRules().size(); i++) {
            newPositions.put(moduleEntity.getGrammarRules().get(i).getId(), i);
        }

        moduleEntity.getGrammarRuleModules().sort(
                Comparator.comparingInt(grm -> newPositions.getOrDefault(grm.getGrammarRule().getId(), Integer.MAX_VALUE))
        );

    }

    public List<ModuleEntity> getAllModules() {
        return moduleRepository.findAll();
    }

    public ModuleEntity getModuleById(String id) {

        Optional<ModuleEntity> moduleFind = moduleRepository.findById(id);

        if(moduleFind.isEmpty()){
            throw new BusinessException("A module with that id was not found", HttpStatus.NOT_FOUND);
        }

        return moduleFind.get();

    }

    public TextBlockEntity getIntroductionByEntityId(String id){
        Optional<ModuleEntity> existing = moduleRepository.findById(id);

        if (existing.isEmpty()) {
            throw new BusinessException("No module with this ID was found.", HttpStatus.NOT_FOUND);
        }

        return existing.get().getIntroduction();
    }

    public TextBlockEntity createIntroduction(String id, TextBlockEntity textblockEntity) {

        Optional<ModuleEntity> existing = moduleRepository.findById(id);

        if (existing.isEmpty()) {
            throw new BusinessException("No module with this ID was found.", HttpStatus.NOT_FOUND);
        }

        if(existing.get().getIntroduction() != null){
            throw new BusinessException("This module already has an introduction.", HttpStatus.BAD_REQUEST);
        }

        existing.get().setIntroduction(textblockEntity);
        this.updateLastModified(existing.get().getId());
        moduleRepository.save(existing.get());

        return textblockEntity;
    }

    public TextBlockEntity updateIntroduction(String id, TextBlockEntity textblockEntity) {

        Optional<ModuleEntity> existing = moduleRepository.findById(id);

        if (existing.isEmpty()) {
            throw new BusinessException("No module with this ID was found.", HttpStatus.NOT_FOUND);
        }

        if(existing.get().getIntroduction() == null){
            throw new BusinessException("This module doesn't have an introduction.", HttpStatus.NOT_FOUND);
        }

        textblockEntity.setId(existing.get().getIntroduction().getId());
        existing.get().setIntroduction(textblockEntity);
        this.updateLastModified(existing.get().getId());
        moduleRepository.save(existing.get());

        return textblockEntity;
    }

    public void deleteIntroductionById(String id) {

        Optional<ModuleEntity> existing = moduleRepository.findById(id);

        if (existing.isEmpty()) {
            throw new BusinessException("No module with this ID was found.", HttpStatus.NOT_FOUND);
        }

        if (existing.get().getIntroduction() == null) {
            throw new BusinessException("This module doesn't have an introduction.", HttpStatus.NOT_FOUND);
        }

        textBlockRepository.deleteById(existing.get().getIntroduction().getId());
        this.updateLastModified(existing.get().getId());
        existing.get().setIntroduction(null);
        moduleRepository.save(existing.get());
    }

    public void validateGrammarRules(ModuleEntity moduleEntity){
        if(moduleEntity.getGrammarRules().size() > 5){
            throw new BusinessException("A module cannot have more than 5 grammar rules", HttpStatus.BAD_REQUEST);
        }
    }

public ModuleOverviewDTO getModuleOverview(String moduleId, String studentUsername) {
        Optional<ModuleEntity> moduleFind = moduleRepository.findById(moduleId);

        if(moduleFind.isEmpty()){
            throw new BusinessException("A module with that id was not found", HttpStatus.NOT_FOUND);
        }

        Optional<ModuleStudent> moduleStudent = moduleStudentRepository.findLinkModuleStudent(studentUsername, moduleId);

        return moduleOverviewMapper.toDTO(moduleFind.get(), moduleStudent.orElse(null));
    }

    public List<String> updateFinalChallenge (FinalChallengeRequestDTO finalChallengeRequestDTO, String moduleId) {

        Optional<ModuleEntity> moduleFind = moduleRepository.findById(moduleId);

        if(moduleFind.isEmpty()){
            throw new BusinessException("A module with that id was not found", HttpStatus.NOT_FOUND);
        }

        Set<String> oldSet = new HashSet<>(moduleFind.get().getFinalChallenge());
        Set<String> newSet = new HashSet<>(finalChallengeRequestDTO.getExerciseList());

        for (String exercise : newSet) {
            contentManagerService.verifyContentOwnership(ContentType.EXERCISE, exercise, moduleFind.get().getId(), ParentOfTheContent.FINAL_CHALLENGE);
        }

        if (!oldSet.equals(newSet) || moduleFind.get().getFinalChallenge().size() != newSet.size()) {
            throw new BusinessException("There are different content types or content IDs in the grammar rule module.", HttpStatus.BAD_REQUEST);
        }

        moduleFind.get().setFinalChallenge(finalChallengeRequestDTO.getExerciseList());
        this.updateLastModified(moduleFind.get().getId());
        this.updateModule(moduleFind.get(), moduleId);

        return finalChallengeRequestDTO.getExerciseList();

    }

    public void exerciseExistInFinalChallenge(String idExercise, String moduleId){

        Optional<ModuleEntity> moduleFind = moduleRepository.findById(moduleId);

        if(moduleFind.isEmpty()){
            throw new BusinessException("A module with that id was not found", HttpStatus.NOT_FOUND);
        }

        boolean exists = false;

        for (String exercise : moduleFind.get().getFinalChallenge()){
            if (exercise.equals(idExercise)){
                exists = true;
                break;
            }
        }

        if(!exists){
            throw new BusinessException("No exercise with this ID in this Final Challenge was found.", HttpStatus.NOT_FOUND);
        }

    }

    public void addExerciseToFinalChallenge (String id, ExerciseEntity exerciseEntity) {

        Optional<ModuleEntity> moduleFind = moduleRepository.findById(id);

        if(moduleFind.isEmpty()){
            throw new BusinessException("A module with that id was not found", HttpStatus.NOT_FOUND);
        }
        this.updateLastModified(moduleFind.get().getId());
        moduleFind.get().getFinalChallenge().add(exerciseEntity.getId());
        this.updateModule(moduleFind.get(), id);

    }

    public ModuleEntity linkModuleToStudent(LinkStudentToModuleRequestDTO linkStudentToModuleRequestDTO) {

        Optional<ModuleEntity> startedModule = moduleRepository.findById(linkStudentToModuleRequestDTO.getModuleId());

        if(startedModule.isEmpty()){
            throw new BusinessException("A module with that id was not found", HttpStatus.NOT_FOUND);
        }

        ModuleEntity moduleEntity = startedModule.get();

        Optional<UserEntity> student = userRepository.findById(linkStudentToModuleRequestDTO.getStudentId());

        if(student.isEmpty()){
            throw new BusinessException("A student with that id was not found", HttpStatus.NOT_FOUND);
        }

        UserEntity studentEntity = student.get();

        Optional<ModuleStudent> existingLink = moduleStudentRepository.findLinkModuleStudent(studentEntity.getId(), moduleEntity.getId());

        if (existingLink.isPresent()) {
            throw new BusinessException("This student is already linked to this module", HttpStatus.BAD_REQUEST);
        }

        ModuleStudent moduleStudent = new ModuleStudent();
        moduleStudent.setStudentId(studentEntity.getId());
        moduleStudent.setModuleId(moduleEntity.getId());
        moduleStudent.setIsVisible(true);
        moduleStudent.setIsFavorite(false);
        moduleStudent.setProgress(0f);

        moduleStudentRepository.save(moduleStudent);

        return moduleEntity;

    }

    public void updateLastModified(String moduleId){

        Optional<ModuleEntity> moduleFind = moduleRepository.findById(moduleId);

        if(moduleFind.isEmpty()){
            throw new BusinessException("A module with that id was not found", HttpStatus.NOT_FOUND);
        }

        moduleFind.get().setLastModified(LocalDateTime.now());
        moduleRepository.save(moduleFind.get());

    }

}
