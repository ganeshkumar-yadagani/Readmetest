package com.tmobile.deep.service.v4.impl;

import com.tmobile.deep.service.DeepCustomConfigService;
import com.tmobile.deep.service.DeepDefaultConfigService;
import com.tmobile.deep.service.dto.DeepDefaultConfigDTO;
import com.tmobile.deep.service.dto.PublisherConfigDTO;
import com.tmobile.deep.service.entity.PublisherConfig;
import com.tmobile.deep.service.enums.EntityType;
import com.tmobile.deep.service.v4.LargeFileConfigServiceV4;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.*;

@Service
public class LargeFileConfigServiceV4Impl implements LargeFileConfigServiceV4 {

    @Autowired
    private DeepDefaultConfigService defaultConfigService;

    @Autowired
    private DeepCustomConfigService customConfigService;

    @PersistenceContext
    private EntityManager entityManager;

    private static final String MAX_EVENT_SIZE_IN_KB_PROP = "deep.event.largefile.maxEventSizeInKb";

    @Override
    public LargeFilePublisherConfigDTO buildLargeFilePublisherConfigDTO(String env, String producer) {
        // 1) Load all defaults
        List<EntityType> entityTypes = Arrays.asList(EntityType.APPLICATION, EntityType.EVENT, EntityType.PUBLISHER);
        Map<EntityType, List<DeepDefaultConfigDTO>> defaultConfigAll =
                defaultConfigService.findByEntityTypesAndEnv(env, entityTypes, null);

        List<DeepDefaultConfigDTO> applicationDefaults = defaultConfigAll.get(EntityType.APPLICATION);
        List<DeepDefaultConfigDTO> eventDefaults = defaultConfigAll.get(EntityType.EVENT);
        List<DeepDefaultConfigDTO> publisherDefaults = defaultConfigAll.get(EntityType.PUBLISHER);

        // 2) Get publisher-specific config (entity list from DB)
        List<PublisherConfig> publisherConfig = customConfigService.findPublisherConfig(producer, env);

        // 3) Merge all defaults + publisherConfig into finalPublisherConfig
        List<PublisherConfigDTO> finalPublisherConfig =
                buildFinalPublisherConfigDTOs(defaultConfigAll, publisherConfig, producer);

        // 4) Event size logic (unchanged)
        String maxEventSize = getPropertyValue(eventDefaults, MAX_EVENT_SIZE_IN_KB_PROP);
        Integer maxEventSizeKb = (maxEventSize == null || maxEventSize.trim().isEmpty())
                ? null : Integer.parseInt(maxEventSize);

        List<EventFileTypeConfig> eventFileTypeConfigs =
                getEventFileTypeConfigDTO(eventDefaults,
                        getEventFileTypeConfigByPublisherAndEnv(producer, env), maxEventSizeKb);

        List<EventFileTypeConfig> eventFileTypeAndCustomSize =
                getEventFileTypeConfigDTO(eventDefaults, eventFileTypeConfigs, maxEventSizeKb);

        // 5) Build final DTO
        return LargeFilePublisherConfigDTO.builder()
                .env(env)
                .publisher(producer)
                .applicationDefaults(applicationDefaults)
                .eventDefaults(eventDefaults)
                .publisherDefaults(publisherDefaults)
                .publisherConfig(finalPublisherConfig)
                .eventFileTypes(eventFileTypeAndCustomSize)
                .build();
    }

    // =========================================================================
    // Merge logic: Option 2 (single loop, proper names)
    // =========================================================================
    private List<PublisherConfigDTO> buildFinalPublisherConfigDTOs(
            Map<EntityType, List<DeepDefaultConfigDTO>> defaultConfigAll,
            List<PublisherConfig> publisherConfigList,
            String producer
    ) {
        // Resolve publisher name
        String publisherName = (publisherConfigList != null && !publisherConfigList.isEmpty())
                ? publisherConfigList.get(0).getPublisherName()
                : producer;

        // Merge publisher defaults with publisherConfig overrides
        LinkedHashMap<String, String> mergedPublisherProperties = new LinkedHashMap<>();
        List<DeepDefaultConfigDTO> publisherDefaults =
                defaultConfigAll.getOrDefault(EntityType.PUBLISHER, Collections.emptyList());

        // baseline: publisher defaults
        for (DeepDefaultConfigDTO defaultProperty : publisherDefaults) {
            mergedPublisherProperties.put(defaultProperty.getPropertyName(), defaultProperty.getPropertyValue());
        }

        // overrides: publisherConfig (non-blank values only)
        if (publisherConfigList != null) {
            for (PublisherConfig publisherProperty : publisherConfigList) {
                String propertyValue = publisherProperty.getPropertyValue();
                if (propertyValue != null && !propertyValue.trim().isEmpty()) {
                    mergedPublisherProperties.put(publisherProperty.getPropertyName(), propertyValue);
                }
            }
        }

        // Build final list in a single loop over all entity types
        List<PublisherConfigDTO> finalConfigList = new ArrayList<>();
        for (Map.Entry<EntityType, List<DeepDefaultConfigDTO>> entityDefaultsEntry : defaultConfigAll.entrySet()) {
            EntityType entityType = entityDefaultsEntry.getKey();
            List<DeepDefaultConfigDTO> entityDefaults = entityDefaultsEntry.getValue();

            if (entityType == EntityType.PUBLISHER) {
                // Use merged publisher map
                for (Map.Entry<String, String> mergedEntry : mergedPublisherProperties.entrySet()) {
                    finalConfigList.add(new PublisherConfigDTO(
                            publisherName,
                            mergedEntry.getKey(),
                            mergedEntry.getValue(),
                            EntityType.PUBLISHER));
                }
            } else {
                // APPLICATION and EVENT pass-through
                if (entityDefaults != null) {
                    for (DeepDefaultConfigDTO defaultProperty : entityDefaults) {
                        finalConfigList.add(new PublisherConfigDTO(
                                publisherName,
                                defaultProperty.getPropertyName(),
                                defaultProperty.getPropertyValue(),
                                entityType));
                    }
                }
            }
        }

        return finalConfigList;
    }

    // =========================================================================
    // Existing helpers (unchanged)
    // =========================================================================
    private List<EventFileTypeConfig> getEventFileTypeConfigByPublisherAndEnv(String publisher, String env) {
        return entityManager
                .createNamedQuery("EventFileTypeConfig.findByPublisherAndEnv", EventFileTypeConfig.class)
                .setParameter(1, publisher)
                .setParameter(2, env)
                .getResultList();
    }

    private List<EventFileTypeConfig> getEventCustomConfigByPublisherAndEnv(String publisher, String env) {
        return entityManager
                .createNamedQuery("EventFileTypeConfig.findCustomByPublisherAndEnv", EventFileTypeConfig.class)
                .setParameter(1, publisher)
                .setParameter(2, env)
                .getResultList();
    }

    private List<EventFileTypeConfig> getEventFileTypeConfigDTO(List<DeepDefaultConfigDTO> eventDefaults,
                                                                List<EventFileTypeConfig> eventFileTypeConfigs,
                                                                Integer maxEventSizeKb) {
        // keep your existing mapping logic
        return eventFileTypeConfigs;
    }

    private String getPropertyValue(List<DeepDefaultConfigDTO> defaultProps, String key) {
        DeepDefaultConfigDTO config = defaultProps.stream()
                .filter(c -> key.equals(c.getPropertyName()))
                .findFirst()
                .orElseThrow(() -> new MissingPublisherConfigException("Value missing in database config"));
        return config.getPropertyValue();
    }
}
