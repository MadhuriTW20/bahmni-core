package org.bahmni.module.bahmnicore.web.v1_0.controller.display.controls;

import org.apache.commons.collections.CollectionUtils;
import org.apache.log4j.Logger;
import org.bahmni.module.bahmnicore.service.BahmniObsService;
import org.bahmni.module.bahmnicore.web.v1_0.mapper.BahmniObservationsToTabularViewMapper;
import org.openmrs.Concept;
import org.openmrs.api.ConceptService;
import org.openmrs.module.bahmniemrapi.encountertransaction.contract.BahmniObservation;
import org.openmrs.module.bahmniemrapi.pivottable.contract.PivotTable;
import org.openmrs.module.emrapi.encounter.ConceptMapper;
import org.openmrs.module.emrapi.encounter.domain.EncounterTransaction;
import org.openmrs.module.webservices.rest.web.RestConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Controller
@RequestMapping(value = "/rest/" + RestConstants.VERSION_1 + "/bahmnicore/observations/flowSheet")
public class ObsToObsTabularFlowSheetController {

    public static final String CONCEPT_DETAILS = "Concept Details";
    private BahmniObsService bahmniObsService;
    private ConceptService conceptService;
    private BahmniObservationsToTabularViewMapper bahmniObservationsToTabularViewMapper;
    private ConceptMapper conceptMapper;

    private static Logger logger = Logger.getLogger(ObsToObsTabularFlowSheetController.class);

    @Autowired
    public ObsToObsTabularFlowSheetController(BahmniObsService bahmniObsService, ConceptService conceptService,
                                              BahmniObservationsToTabularViewMapper bahmniObservationsToTabularViewMapper) {
        this.bahmniObsService = bahmniObsService;
        this.conceptService = conceptService;
        this.bahmniObservationsToTabularViewMapper = bahmniObservationsToTabularViewMapper;
        this.conceptMapper = new ConceptMapper();
    }

    @RequestMapping(method = RequestMethod.GET)
    @ResponseBody
    public PivotTable constructPivotTableFor(
            @RequestParam(value = "patientUuid", required = true) String patientUuid,
            @RequestParam(value = "numberOfVisits", required = false) Integer numberOfVisits,
            @RequestParam(value = "conceptSet", required = true) String conceptSet,
            @RequestParam(value = "groupByConcept", required = true) String groupByConcept,
            @RequestParam(value = "conceptNames", required = false) List<String> conceptNames) {

        Concept rootConcept = conceptService.getConceptByName(conceptSet);
        Concept childConcept = conceptService.getConceptByName(groupByConcept);
        validate(conceptSet, groupByConcept, rootConcept, childConcept);

        Collection<BahmniObservation> bahmniObservations = bahmniObsService.observationsFor(patientUuid, rootConcept, childConcept, numberOfVisits);

        Set<EncounterTransaction.Concept> leafConcepts = new HashSet<>();
        if (CollectionUtils.isEmpty(conceptNames)) {
            getAllLeafConcepts(rootConcept, leafConcepts);
        } else {
            getSpecifiedLeafConcepts(rootConcept, conceptNames, leafConcepts);
        }
        if (conceptNames != null && !conceptNames.contains(groupByConcept)) {
            leafConcepts.add(conceptMapper.map(childConcept));
        }

        return bahmniObservationsToTabularViewMapper.constructTable(leafConcepts, bahmniObservations);
    }

    private void getSpecifiedLeafConcepts(Concept rootConcept, List<String> conceptNames, Set<EncounterTransaction.Concept> leafConcepts) {
        for (Concept concept : rootConcept.getSetMembers()) {
            if (conceptNames.contains(concept.getName().getName())) {
                getAllLeafConcepts(concept, leafConcepts);
            } else {
                getSpecifiedLeafConcepts(concept, conceptNames, leafConcepts);
            }
        }
    }

    private void getAllLeafConcepts(Concept rootConcept, Set<EncounterTransaction.Concept> leafConcepts) {
        if (!rootConcept.isSet() || rootConcept.getConceptClass().getName().equals(CONCEPT_DETAILS)) {
            leafConcepts.add(conceptMapper.map(rootConcept));
        } else {
            for (Concept concept : rootConcept.getSetMembers()) {
                getAllLeafConcepts(concept, leafConcepts);
            }
        }
    }

    private void validate(String conceptSet, String groupByConcept, Concept rootConcept, Concept childConcept) {
        if (rootConcept == null) {
            logger.error("Root concept not found for the name:  " + conceptSet);
            throw new RuntimeException("Root concept not found for the name:  " + conceptSet);
        }

        if (!rootConcept.getSetMembers().contains(childConcept)) {
            logger.error("GroupByConcept: " + groupByConcept + " doesn't belong to the Root concept:  " + conceptSet);
            throw new RuntimeException("GroupByConcept: " + groupByConcept + " doesn't belong to the Root concept:  " + conceptSet);
        }
    }
}