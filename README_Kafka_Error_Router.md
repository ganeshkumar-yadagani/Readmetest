package com.tmobile.deep.service.v4.impl;

import com.tmobile.deep.service.DeepCustomConfigService;
import com.tmobile.deep.service.DeepDefaultConfigService;
import com.tmobile.deep.service.dto.DeepDefaultConfigDTO;
import com.tmobile.deep.service.dto.PublisherConfigDTO;
import com.tmobile.deep.service.enums.EntityType;
import com.tmobile.deep.service.v4.LargeFileConfigServiceV4;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.*;
import java.util.stream.Collectors;

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

        List<DeepDefaultConfigDTO> defaultApplicationProps = defaultConfigAll.get(EntityType.APPLICATION);
        List<DeepDefaultConfigDTO> defaultEventProps = defaultConfigAll.get(EntityType.EVENT);
        List<DeepDefaultConfigDTO> defaultPublisherProps = defaultConfigAll.get(EntityType.PUBLISHER);

        // 2) Get publisher-specific config
        List<PublisherConfigDTO> publisherConfig = customConfigService.findPublisherConfig(producer, env);

        // 3) Merge all defaults + publisherConfig into finalPublisherConfig
        List<PublisherConfigDTO> finalPublisherConfig =
                buildFinalPublisherConfigDTOs(defaultConfigAll, publisherConfig, producer);

        // ---- Keep your event size logic as-is ----
        String maxEventSize = getPropertyValue(defaultEventProps, MAX_EVENT_SIZE_IN_KB_PROP);
        Integer maxEventSizeKb = (maxEventSize == null || maxEventSize.trim().isEmpty())
                ? null : Integer.parseInt(maxEventSize);

        List<EventFileTypeConfig> eventFileTypeConfigs =
                getEventFileTypeConfigDTO(defaultEventProps, getEventFileTypeConfigByPublisherAndEnv(producer, env), maxEventSizeKb);

        List<EventFileTypeConfig> eventFileTypeAndCustomSize =
                getEventFileTypeConfigDTO(defaultEventProps, eventFileTypeConfigs, maxEventSizeKb);

        // 4) Build final DTO
        return LargeFilePublisherConfigDTO.builder()
                .env(env)
                .publisher(producer)
                .applicationDefaults(defaultApplicationProps)
                .eventDefaults(defaultEventProps)
                .publisherDefaults(defaultPublisherProps)
                .publisherConfig(finalPublisherConfig)
                .eventFileTypes(eventFileTypeAndCustomSize)
                .build();
    }

    // =========================================================================
    // NEW: Merge logic
    // =========================================================================
    private List<PublisherConfigDTO> buildFinalPublisherConfigDTOs(
            Map<EntityType, List<DeepDefaultConfigDTO>> defaultConfigAll,
            List<PublisherConfigDTO> publisherConfig,
            String producer
    ) {
        // 1) Flatten all defaults (APPLICATION + EVENT + PUBLISHER)
        LinkedHashMap<String, String> merged = new LinkedHashMap<>();
        for (EntityType et : Arrays.asList(EntityType.APPLICATION, EntityType.EVENT, EntityType.PUBLISHER)) {
            List<DeepDefaultConfigDTO> defaults = defaultConfigAll.getOrDefault(et, Collections.emptyList());
            for (DeepDefaultConfigDTO d : defaults) {
                merged.put(d.getPropertyName(), d.getPropertyValue());
            }
        }

        // 2) Apply publisherConfig overrides (only for PUBLISHER keys)
        if (publisherConfig != null) {
            for (PublisherConfigDTO p : publisherConfig) {
                String val = p.getPropertyValue();
                if (val != null && !val.trim().isEmpty()) {
                    merged.put(p.getPropertyName(), val);
                }
            }
        }

        // 3) Resolve publisher name
        String publisherName = (publisherConfig != null && !publisherConfig.isEmpty())
                ? publisherConfig.get(0).getPublisherName()
                : producer;

        // 4) Convert merged map back into PublisherConfigDTO
        return merged.entrySet().stream()
                .map(e -> new PublisherConfigDTO(null, publisherName, e.getKey(), e.getValue()))
                .collect(Collectors.toList());
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

    private List<EventFileTypeConfig> getEventFileTypeConfigDTO(List<DeepDefaultConfigDTO> defaultEventProps,
                                                                List<EventFileTypeConfig> eventFileTypeConfigs,
                                                                Integer maxEventSizeKb) {
        // your existing mapping logic goes here
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
